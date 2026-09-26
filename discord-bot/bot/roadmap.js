// ============================================================
// RASPBERRY ROADMAP — Discord commands + web routes
// Data comes from roadmap-data.json, the same data the website uses.
// ============================================================

const fs = require("fs");
const path = require("path");
const express = require("express");
const {
    EmbedBuilder,
    AttachmentBuilder,
    ActionRowBuilder,
    ButtonBuilder,
    ButtonStyle,
    SlashCommandBuilder
} = require("discord.js");

const { runUpdate, zurichNow } = require("./update");
const { buildPage, PAGE_FILE } = require("./page");

const DATA_FILE = path.join(process.env.ROADMAP_DATA_DIR || __dirname, "roadmap-data.json");
const UPDATE_HOUR = Number(process.env.ROADMAP_UPDATE_HOUR ?? 6); // Europe/Zurich
const UPDATE_COOLDOWN_MS = 2 * 60 * 1000;
let updateRunning = null;
let lastManualUpdate = 0;
const COLOR = 0x171717;
const PUBLIC_PORT = Number(process.env.ROADMAP_PORT || 3001);
let tunnelBase = null; // filled from cloudflared (Quick Tunnel)

function loadData() {
    return JSON.parse(fs.readFileSync(DATA_FILE, "utf8"));
}

// Public link to the bot-hosted page. Order: ROADMAP_URL > PUBLIC_URL > Cloudflare tunnel.
// Returns null when the page is only reachable on the local machine.
function githubPagesUrl() {
    const repo = process.env.GITHUB_REPO; // "owner/repo"
    if (!repo || !repo.includes("/")) return null;
    const [owner, name] = repo.split("/");
    return name.toLowerCase() === `${owner.toLowerCase()}.github.io`
        ? `https://${owner.toLowerCase()}.github.io/`
        : `https://${owner.toLowerCase()}.github.io/${name}/`;
}

function websiteUrl() {
    if (process.env.ROADMAP_URL) return process.env.ROADMAP_URL;
    if (process.env.PUBLIC_URL) return process.env.PUBLIC_URL;
    const pages = githubPagesUrl();
    if (pages) return pages;
    return tunnelBase ? `${tunnelBase}/roadmap` : null;
}

// Starts the GitHub Actions workflow that rebuilds the GitHub Pages site
async function triggerGithubPages() {
    const { GITHUB_TOKEN, GITHUB_REPO } = process.env;
    if (!GITHUB_TOKEN || !GITHUB_REPO) return null;
    const workflow = process.env.GITHUB_WORKFLOW || "pages.yml";
    const ref = process.env.GITHUB_BRANCH || "main";
    try {
        const res = await fetch(`https://api.github.com/repos/${GITHUB_REPO}/actions/workflows/${workflow}/dispatches`, {
            method: "POST",
            headers: {
                Authorization: `Bearer ${GITHUB_TOKEN}`,
                Accept: "application/vnd.github+json",
                "X-GitHub-Api-Version": "2022-11-28",
                "User-Agent": "Raspberry-Roadmap-Bot"
            },
            body: JSON.stringify({ ref }),
            signal: AbortSignal.timeout(15000)
        });
        if (res.status === 204) return "GitHub Pages wird neu gebaut (1–2 Minuten).";
        return `GitHub Pages konnte nicht gestartet werden (HTTP ${res.status}).`;
    } catch (err) {
        return `GitHub Pages konnte nicht gestartet werden: ${err.message}`;
    }
}

function pageAttachment() {
    if (!fs.existsSync(PAGE_FILE)) buildPage(loadData());
    return new AttachmentBuilder(PAGE_FILE, { name: "raspberry-roadmap.html" });
}

const PLAYER_CHOICES = [
    { name: "Relaxo_GX", value: "relaxo" },
    { name: "ReverseAmin", value: "amin" }
];

function resolvePlayer(interaction, data) {
    const picked = interaction.options.getString("spieler");
    if (picked && data.P[picked]) return picked;
    // Fall back to the Discord display name if it matches a player
    const name = (interaction.member?.displayName || interaction.user?.username || "").toLowerCase();
    if (name.includes("amin") || name.includes("reverse")) return "amin";
    return "relaxo";
}

const clip = (s, n = 1024) => (s.length > n ? s.slice(0, n - 1) + "…" : s);
const SEV = { hi: "🔴", md: "🟠", lo: "🟢" };

function linkRow(data, label = "Roadmap öffnen") {
    const url = websiteUrl();
    if (!url) return [];
    return [new ActionRowBuilder().addComponents(
        new ButtonBuilder()
            .setStyle(ButtonStyle.Link)
            .setLabel(label)
            .setURL(url)
    )];
}

// Adds the link button, or the page as an HTML file when there is no public link
function withPage(payload, data, forceFile = false) {
    const components = linkRow(data);
    const out = { ...payload, components };
    if (forceFile || !components.length) out.files = [pageAttachment()];
    return out;
}

function footer(embed, data) {
    return embed.setFooter({ text: `Raspberry ♲ Ironman · Stand ${data.meta.updated}` });
}

// ---------- embeds ----------

function roadmapEmbed(data, key) {
    const p = data.P[key];
    const e = new EmbedBuilder()
        .setColor(COLOR)
        .setTitle(`🧭 Roadmap · ${p.name}`)
        .setURL(websiteUrl() || null)
        .setDescription(clip(p.lead, 4000));
    e.addFields({
        name: "Stats",
        value: p.stats.map(([v, l]) => `**${v}** ${l}`).join(" · ").slice(0, 1024)
    });
    e.addFields({
        name: "Grösste Hebel",
        value: clip(p.diag.slice(0, 4).map(([s, h]) => `${SEV[s]} ${h}`).join("\n"))
    });
    const [title, sub, items] = p.phases[0];
    e.addFields({
        name: `${title} (${sub})`,
        value: clip(items.map(([, t, , c]) => `☐ ${t} \`${c}\``).join("\n"))
    });
    const [t2, , items2] = p.phases[1];
    e.addFields({ name: t2, value: clip(items2.map(([, t]) => `☐ ${t}`).join("\n")) });
    return footer(e, data);
}

function dailyEmbed(data) {
    const e = new EmbedBuilder()
        .setColor(COLOR)
        .setTitle("📅 Daily · Ironman")
        .setURL(websiteUrl() || null)
        .setDescription("Alles ohne Auction House und Bazaar machbar. Zum Abhaken die Website öffnen.");
    for (const [group, items] of data.DAILY) {
        e.addFields({ name: group, value: clip(items.map(([, t, s]) => `☐ **${t}** – ${s}`).join("\n")) });
    }
    return footer(e, data);
}

function hotmEmbed(data, key, buildId) {
    const hp = data.HOTM_P[key];
    const b = data.HOTM_BUILDS.find(x => x.id === buildId) || data.HOTM_BUILDS[1];
    const lv = hp.levels;
    const e = new EmbedBuilder()
        .setColor(COLOR)
        .setTitle(`⛏️ HotM · ${data.P[key].name}`)
        .setURL(websiteUrl() || null)
        .setDescription(clip(hp.sum, 4000))
        .addFields(
            { name: "Tier", value: `${hp.tier} / 10\n${hp.toNext}`, inline: true },
            { name: "Tokens", value: hp.tokens, inline: true },
            { name: "Core", value: `${hp.peak} / 10`, inline: true },
            {
                name: `Setup: ${b.label} (${b.when})`,
                value: clip(b.order.map((n, i) => `${i + 1}. ${n} — ${lv[n] !== undefined ? "Lvl " + lv[n] : "fehlt"}`).join("\n"))
            },
            { name: "Ability", value: b.ability, inline: true },
            { name: "Weglassen", value: clip(b.skip), inline: false },
            { name: "Als Nächstes", value: clip(hp.next.map(([a, d]) => `• **${a}** – ${d}`).join("\n")) }
        );
    return footer(e, data);
}

function gardenEmbed(data, key) {
    const g = data.GARDEN;
    const pp = g.player[key];
    const e = new EmbedBuilder()
        .setColor(COLOR)
        .setTitle(`🌾 Garden & Farming · ${data.P[key].name}`)
        .setURL(websiteUrl() || null)
        .setDescription(clip(pp.now, 4000))
        .addFields(
            { name: "Status", value: clip(g.status.map(([a, b]) => `**${a}:** ${b}`).join("\n")) },
            { name: "Copper-Plan", value: clip(g.copper.slice(0, 8).map((r, i) => `${i + 1}. ${r[0]} — ${r[1]} Copper (${r[3]})`).join("\n")) },
            { name: "Dein Setup als Nächstes", value: clip(pp.next.map(x => `• ${x}`).join("\n")) }
        );
    return footer(e, data);
}

function mpEmbed(data, key) {
    const p = data.P[key];
    const e = new EmbedBuilder()
        .setColor(COLOR)
        .setTitle(`💍 Magical Power · ${p.name}`)
        .setURL(websiteUrl() || null)
        .setDescription(`Jetzt **${p.mp.now} MP** · realistisch bald ≈ ${p.mp.t1} · mit Slayer/Dungeons/Crimson ≈ ${p.mp.t2}`);
    for (const [group, rows] of p.acc.slice(0, 3)) {
        e.addFields({ name: group, value: clip(rows.map(([a, src, mp]) => `• **${a}** ${mp} — ${src}`).join("\n")) });
    }
    return footer(e, data);
}

function setupEmbed(data, key, area) {
    const p = data.P[key];
    const s = p.setups[area] || p.setups.combat;
    const e = new EmbedBuilder()
        .setColor(COLOR)
        .setTitle(`🛡️ ${s.label} · ${p.name}`)
        .setURL(websiteUrl() || null)
        .addFields(
            { name: "Jetzt", value: clip(s.now.map(x => `• ${x}`).join("\n")), inline: true },
            { name: "Next", value: clip(s.next.map(x => `• ${x}`).join("\n")), inline: true },
            { name: "Endziel", value: clip(s.end.map(x => `• ${x}`).join("\n")), inline: true }
        );
    for (const [a, b] of s.kv) e.addFields({ name: a, value: clip(b), inline: true });
    return footer(e, data);
}

// ---------- slash commands ----------

const playerOpt = o => o.setName("spieler").setDescription("Welcher Spieler").setRequired(false).addChoices(...PLAYER_CHOICES);

const ROADMAP_COMMANDS = [
    new SlashCommandBuilder().setName("roadmap").setDescription("Ironman-Roadmap: grösste Hebel und Next Steps.").addStringOption(playerOpt),
    new SlashCommandBuilder().setName("daily").setDescription("Tägliche und stündliche Ironman-Aufgaben."),
    new SlashCommandBuilder().setName("hotm").setDescription("HotM-Stand und Perk-Reihenfolge.")
        .addStringOption(playerOpt)
        .addStringOption(o => o.setName("setup").setDescription("Welches Mining-Setup").setRequired(false).addChoices(
            { name: "Commissions & Titanium", value: "comm" },
            { name: "Gemstones", value: "gem" },
            { name: "Glacite & Mineshafts", value: "glacite" },
            { name: "Powder farmen", value: "powder" }
        )),
    new SlashCommandBuilder().setName("garden").setDescription("Copper-Plan und Farming-Setup.").addStringOption(playerOpt),
    new SlashCommandBuilder().setName("mp").setDescription("Magical Power und nächste Accessories.").addStringOption(playerOpt),
    new SlashCommandBuilder().setName("setup").setDescription("Armor, Equipment und Waffen pro Bereich.")
        .addStringOption(playerOpt)
        .addStringOption(o => o.setName("bereich").setDescription("Bereich").setRequired(false).addChoices(
            { name: "Combat / Slayer", value: "combat" },
            { name: "Dungeons", value: "dungeon" },
            { name: "Mining", value: "mining" },
            { name: "Farming", value: "farming" },
            { name: "Fishing", value: "fishing" },
            { name: "Foraging", value: "foraging" }
        )),
    new SlashCommandBuilder().setName("website").setDescription("Link zur Roadmap-Website und Einladungslink."),
    new SlashCommandBuilder().setName("update").setDescription("Holt neue Profildaten und aktualisiert die Roadmap-Website.")
].map(c => c.toJSON());

const NAMES = new Set(ROADMAP_COMMANDS.map(c => c.name));

async function handleRoadmapCommand(interaction) {
    const name = interaction.commandName;
    if (!NAMES.has(name)) return false;
    if (name === "update") {
        await handleUpdateCommand(interaction);
        return true;
    }
    const data = loadData();
    const key = resolvePlayer(interaction, data);
    let embed;
    if (name === "roadmap") embed = roadmapEmbed(data, key);
    else if (name === "daily") embed = dailyEmbed(data);
    else if (name === "hotm") embed = hotmEmbed(data, key, interaction.options.getString("setup") || "gem");
    else if (name === "garden") embed = gardenEmbed(data, key);
    else if (name === "mp") embed = mpEmbed(data, key);
    else if (name === "setup") embed = setupEmbed(data, key, interaction.options.getString("bereich") || "combat");
    else if (name === "website") {
        const appId = interaction.client.application?.id || interaction.client.user.id;
        const invite = `https://discord.com/oauth2/authorize?client_id=${appId}&scope=bot+applications.commands&permissions=277025508352`;
        const url = websiteUrl();
        embed = footer(new EmbedBuilder()
            .setColor(COLOR)
            .setTitle("🌐 Raspberry Roadmap")
            .setDescription(
                (url ? `Website: ${url}\n` : "Kein öffentlicher Link eingerichtet. Die Seite hängt als Datei an: herunterladen und im Browser öffnen.\n") +
                `Auf dem Bot-Rechner: \`http://localhost:${PUBLIC_PORT}/roadmap\`\n\n` +
                `Bot auf weiteren Server einladen: [Einladungslink](${invite})`
            ), data);
        await interaction.reply(withPage({ embeds: [embed] }, data, true));
        return true;
    }
    await interaction.reply(withPage({ embeds: [embed] }, data));
    return true;
}

// ---------- updates ----------

function updateEmbed(result, reason) {
    const { data, changes } = result;
    const e = new EmbedBuilder()
        .setColor(COLOR)
        .setTitle(reason === "automatisch" ? "🔄 Tägliches Roadmap-Update" : "🔄 Roadmap aktualisiert")
        .setURL(websiteUrl() || null)
        .setDescription(
            `Stand **${data.meta.updated}** · Quelle: ${data.meta.sources.join(" + ")}\n` +
            (result.pages ? `${result.pages}\n` : "") +
            (changes.length ? `**${changes.length} Änderungen seit dem letzten Update:**` : "Keine Änderungen seit dem letzten Update.")
        );
    if (changes.length) {
        let chunk = "";
        const fields = [];
        for (const c of changes) {
            if ((chunk + c + "\n").length > 1000) { fields.push(chunk); chunk = ""; }
            chunk += c + "\n";
        }
        if (chunk) fields.push(chunk);
        fields.slice(0, 5).forEach((v, i) => e.addFields({ name: i === 0 ? "Änderungen" : "\u200b", value: v }));
    }
    for (const key of Object.keys(data.P)) {
        const top = (data.P[key].diag || []).slice(0, 3).map(([s, h]) => `${SEV[s]} ${h}`).join("\n");
        if (top) e.addFields({ name: `Top-Hebel · ${data.P[key].name}`, value: clip(top), inline: true });
    }
    return footer(e, data);
}

async function doUpdate(reason) {
    if (!updateRunning) {
        updateRunning = runUpdate({ reason })
            .then(async result => {
                buildPage(result.data);
                result.pages = await triggerGithubPages();
                return result;
            })
            .finally(() => { updateRunning = null; });
    }
    return updateRunning;
}

async function handleUpdateCommand(interaction) {
    const wait = lastManualUpdate + UPDATE_COOLDOWN_MS - Date.now();
    if (wait > 0) {
        await interaction.reply({ content: `⏳ Das letzte Update ist gerade erst gelaufen. Nochmal in ${Math.ceil(wait / 1000)} s.`, ephemeral: true });
        return;
    }
    lastManualUpdate = Date.now();
    await interaction.deferReply();
    try {
        const result = await doUpdate("manuell");
        await interaction.editReply(withPage({ embeds: [updateEmbed(result, "manuell")] }, result.data));
    } catch (err) {
        console.error("[ROADMAP] Update fehlgeschlagen:", err);
        await interaction.editReply({ content: `⚠️ Update fehlgeschlagen: ${err.message}. Die alten Daten bleiben bestehen.` });
    }
}

async function postToChannel(client, payload) {
    const id = process.env.ROADMAP_CHANNEL_ID;
    if (!id) return;
    try {
        const channel = await client.channels.fetch(id);
        if (channel?.isTextBased()) await channel.send(payload);
    } catch (err) {
        console.warn("[ROADMAP] Konnte nicht in ROADMAP_CHANNEL_ID posten:", err.message);
    }
}

// Runs at least once per day: after ROADMAP_UPDATE_HOUR (Zurich time),
// and right after start if the data is older than 24 hours.
function startAutoUpdate(client) {
    const tick = async () => {
        try {
            const data = loadData();
            const now = zurichNow();
            const last = data.meta?.updatedAt ? Date.parse(data.meta.updatedAt) : 0;
            const doneToday = data.meta?.updatedDate === now.date && data.meta?.reason === "automatisch";
            const stale = Date.now() - last > 24 * 60 * 60 * 1000;
            if ((!doneToday && now.hour >= UPDATE_HOUR) || stale) {
                const result = await doUpdate("automatisch");
                await postToChannel(client, withPage({ embeds: [updateEmbed(result, "automatisch")] }, result.data));
            }
        } catch (err) {
            console.error("[ROADMAP] Automatisches Update fehlgeschlagen:", err.message);
        }
    };
    setTimeout(tick, 15 * 1000);
    setInterval(tick, 15 * 60 * 1000);
    console.log(`[ROADMAP] Auto-Update aktiv: täglich ab ${UPDATE_HOUR}:00 (Europe/Zurich).`);
}

// ---------- web routes ----------

function sendPage(req, res) {
    if (!fs.existsSync(PAGE_FILE)) buildPage(loadData());
    res.set("Cache-Control", "no-store");
    res.sendFile(PAGE_FILE);
}

function mountRoadmapRoutes(app, client) {
    app.get("/roadmap", sendPage);
    app.get("/api/roadmap", (req, res) => {
        res.set("Cache-Control", "no-store");
        res.json(loadData());
    });
    app.post("/api/roadmap/update", async (req, res) => {
        const token = process.env.ROADMAP_UPDATE_TOKEN;
        if (token && req.get("x-update-token") !== token && req.query.token !== token) {
            return res.status(401).json({ ok: false, error: "Falscher oder fehlender Update-Token." });
        }
        try {
            const result = await doUpdate("web");
            res.json({ ok: true, updated: result.data.meta.updated, changes: result.changes });
        } catch (err) {
            res.status(502).json({ ok: false, error: err.message });
        }
    });
    app.get("/invite", (req, res) => {
        const id = client?.application?.id || client?.user?.id;
        if (!id) return res.status(503).send("Bot ist noch nicht eingeloggt. In ein paar Sekunden erneut versuchen.");
        res.redirect(`https://discord.com/oauth2/authorize?client_id=${id}&scope=bot+applications.commands&permissions=277025508352`);
    });
}

// Separate small web server that only serves the roadmap.
// Safe to expose to the internet: the dashboard with its admin routes stays on PORT.
function startPublicServer(client) {
    const pub = express();
    pub.disable("x-powered-by");
    pub.get("/", (req, res) => res.redirect("/roadmap"));
    pub.get("/roadmap", sendPage);
    pub.get("/api/roadmap", (req, res) => {
        res.set("Cache-Control", "no-store");
        res.json(loadData());
    });
    pub.get("/invite", (req, res) => {
        const id = client?.application?.id || client?.user?.id;
        if (!id) return res.status(503).send("Bot startet noch.");
        res.redirect(`https://discord.com/oauth2/authorize?client_id=${id}&scope=bot+applications.commands&permissions=277025508352`);
    });
    if (process.env.ROADMAP_UPDATE_TOKEN) {
        pub.post("/api/roadmap/update", async (req, res) => {
            if (req.get("x-update-token") !== process.env.ROADMAP_UPDATE_TOKEN) {
                return res.status(401).json({ ok: false, error: "Falscher Update-Token." });
            }
            try {
                const result = await doUpdate("web");
                res.json({ ok: true, updated: result.data.meta.updated, changes: result.changes });
            } catch (err) {
                res.status(502).json({ ok: false, error: err.message });
            }
        });
    }
    const server = pub.listen(PUBLIC_PORT, () => console.log(`[ROADMAP] Roadmap-Seite: http://localhost:${PUBLIC_PORT}/roadmap`));
    try { buildPage(loadData()); } catch (err) { console.warn("[ROADMAP] Seite konnte nicht gebaut werden:", err.message); }
    startTunnelWatch();
    return server;
}

// Reads the public https address of a Cloudflare Quick Tunnel (cloudflared service in docker-compose).
function startTunnelWatch() {
    const metrics = process.env.CLOUDFLARED_METRICS_URL;
    if (!metrics || process.env.PUBLIC_URL || process.env.ROADMAP_URL || githubPagesUrl()) return;
    const check = async () => {
        try {
            const res = await fetch(`${metrics.replace(/\/$/, "")}/quicktunnel`, { signal: AbortSignal.timeout(5000) });
            const body = await res.json();
            if (body?.hostname) {
                const next = `https://${body.hostname}`;
                if (next !== tunnelBase) console.log(`[ROADMAP] Öffentlicher Link: ${next}/roadmap`);
                tunnelBase = next;
            }
        } catch {
            // tunnel not ready yet
        }
    };
    check();
    setInterval(check, 60 * 1000);
}

const HELP_LINES =
    "\n**Roadmap (Raspberry Coop)**\n" +
    "`/roadmap [spieler]` — Hebel und Next Steps\n" +
    "`/daily` — tägliche Ironman-Aufgaben\n" +
    "`/hotm [spieler] [setup]` — HotM-Perk-Reihenfolge\n" +
    "`/garden [spieler]` — Copper-Plan und Farming\n" +
    "`/mp [spieler]` — Magical Power\n" +
    "`/setup [spieler] [bereich]` — Armor und Waffen\n" +
    "`/website` — Link zur Roadmap und Einladungslink\n" +
    "`/update` — neue Daten holen (läuft zusätzlich täglich automatisch)\n";

module.exports = {
    ROADMAP_COMMANDS,
    handleRoadmapCommand,
    mountRoadmapRoutes,
    startPublicServer,
    startAutoUpdate,
    websiteUrl,
    HELP_LINES,
    _test: { loadData, roadmapEmbed, dailyEmbed, hotmEmbed, gardenEmbed, mpEmbed, setupEmbed }
};
