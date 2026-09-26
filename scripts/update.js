// ============================================================
// RASPBERRY ROADMAP — live data refresh
// Pulls fresh profile data and rewrites roadmap-data.json.
// Curated texts (steps, accessory plan, setups) stay as they are;
// numbers, levels, pets, garden status and the "biggest levers"
// list are recalculated from the live data.
// ============================================================

const fs = require("fs");
const path = require("path");

const DATA_DIR = process.env.ROADMAP_DATA_DIR || __dirname;
const DATA_FILE = path.join(DATA_DIR, "roadmap-data.json");
const PREV_FILE = path.join(DATA_DIR, "roadmap-data.prev.json");
// First start with a separate data folder (e.g. Docker volume): seed it from the bundled file
if (!fs.existsSync(DATA_FILE)) {
    fs.mkdirSync(DATA_DIR, { recursive: true });
    fs.copyFileSync(path.join(__dirname, "roadmap-data.json"), DATA_FILE);
}

const ELITE_API = "https://api.elitebot.dev";
const PROFILE_ID = process.env.ROADMAP_PROFILE_ID || "db1dd88b25b64e998c5082d8c0548274";
const PLAYERS = {
    relaxo: { uuid: "5fdf17a46d3c48d58f9eb80698c62398", name: "Relaxo_GX" },
    amin: { uuid: "7b8b783fd39348b297a20e6771bdfff6", name: "ReverseAmin" }
};
const FAIRY_SOULS_TOTAL = 289; // Stand Wiki 26.09.2026
const HOTM_CUMULATIVE = [0, 3000, 12000, 37000, 97000, 197000, 347000, 557000, 847000, 1247000];
const CROP_TIER_BY_GARDEN = [0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 7, 8, 9]; // index = garden level
const NODE_NAMES = {
    mining_speed: "Mining Speed", mining_fortune: "Mining Fortune", titanium_insanium: "Titanium Insanium",
    precision_mining: "Precision Mining", pickobulus: "Pickobulus", mining_speed_boost: "Mining Speed Boost",
    luck_of_the_cave: "Luck of the Cave", efficient_miner: "Efficient Miner", quick_forge: "Quick Forge",
    sky_mall: "Sky Mall", old_school: "Old-School", professional: "Professional", mole: "Mole",
    gem_lover: "Gem Lover", seasoned_mineman: "Seasoned Mineman", front_loaded: "Front Loaded",
    daily_grind: "Daily Grind", core_of_the_mountain: "Core of the Mountain", daily_powder: "Daily Powder",
    blockhead: "Blockhead", subterranean_fisher: "Subterranean Fisher", keep_it_cool: "Keep It Cool",
    lonesome_miner: "Lonesome Miner", great_explorer: "Great Explorer", speedy_mineman: "Speedy Mineman",
    powder_buff: "Powder Buff", fortunate_mineman: "Fortunate Mineman", miners_blessing: "Miner's Blessing",
    no_stone_unturned: "No Stone Unturned", strong_arm: "Strong Arm", steady_hand: "Steady Hand",
    warm_heart: "Warm Heart", surveyor: "Surveyor", mineshaft_mayhem: "Mineshaft Mayhem",
    metal_head: "Metal Head", rags_to_riches: "Rags to Riches", eager_adventurer: "Eager Adventurer",
    crystalline: "Crystalline", gifts_from_the_departed: "Gifts from the Departed", mining_master: "Mining Master",
    dead_mans_chest: "Dead Man's Chest", vanguard_seeker: "Vanguard Seeker"
};
const RARITY_ORDER = { mythic: 0, legendary: 1, epic: 2, rare: 3, uncommon: 4, common: 5 };

// ---------- helpers ----------

async function getJson(url, headers = {}) {
    const res = await fetch(url, {
        headers: { "User-Agent": "Raspberry-Roadmap-Bot/1.0", Accept: "application/json", ...headers },
        signal: AbortSignal.timeout(20000)
    });
    if (!res.ok) throw new Error(`${url.split("?")[0]} → HTTP ${res.status}`);
    return res.json();
}

const fmtInt = n => Math.round(n).toLocaleString("de-CH");
const fmtM = n => (n >= 1e9 ? (n / 1e9).toFixed(2).replace(".", ",") + " B" : Math.round(n / 1e6) + " M");
const fmtM1 = n => (n / 1e6).toFixed(1).replace(".", ",") + " M";
const title = s => String(s).toLowerCase().split("_").map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(" ");
const zurichNow = () => {
    const parts = Object.fromEntries(new Intl.DateTimeFormat("en-GB", {
        timeZone: "Europe/Zurich", year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hour12: false
    }).formatToParts(new Date()).map(p => [p.type, p.value]));
    return { date: `${parts.year}-${parts.month}-${parts.day}`, hour: Number(parts.hour), label: `${parts.day}.${parts.month}.${parts.year} ${parts.hour}:${parts.minute}` };
};

function hotmTierFromXp(xp) {
    let tier = 1;
    HOTM_CUMULATIVE.forEach((need, i) => { if (xp >= need) tier = i + 1; });
    return tier;
}

// ---------- fetching ----------

async function fetchPlayer(key) {
    const { uuid } = PLAYERS[key];
    // EliteBot reads the Hypixel data on its side, so no Hypixel API key is needed here
    const elite = await getJson(`${ELITE_API}/profile/${uuid}/${PROFILE_ID}`);
    return { elite };
}

// ---------- extraction ----------

function extract(key, { elite: d }) {
    const st = d.stats || {};
    const u = d.unparsed || {};
    const skills = st.skills?.levels || {};
    const acc = u.accessoryBagSettings || {};
    const tuning = acc.tuning || {};
    const tuningUsed = ["slot_0", "slot_1", "slot_2", "slot_3", "slot_4"].some(s => tuning[s] && Object.values(tuning[s]).some(v => v > 0));
    const slayer = Object.fromEntries(Object.entries(st.slayer?.bosses || {}).map(([k, v]) => [k, {
        level: v.level || 0,
        xp: v.xp || 0,
        unclaimed: Array.from({ length: v.level || 0 }, (_, i) => i + 1).filter(l => !(v.claimedLevels || {})[`level_${l}`])
    }]));
    const classes = Object.fromEntries(Object.entries(st.dungeons?.classLevels || {}).map(([k, v]) => [k, v.level]));
    const selected = String(st.dungeons?.selectedClass || "").toLowerCase();
    const bestClass = selected && classes[selected] != null
        ? [selected, classes[selected]]
        : Object.entries(classes).sort((a, b) => b[1] - a[1])[0] || ["berserk", 0];

    // HotM perk levels come from EliteBot. Tier and tokens are not in that data,
    // so the last known values on the page are kept for those.
    const nodes = st.mining?.nodes || {};
    const hotmXp = st.mining?.experience || 0;
    const hotmTier = hotmXp > 0 ? hotmTierFromXp(hotmXp) : null;
    const tokens = null;
    const hotmLevels = {};
    for (const [id, lvl] of Object.entries(nodes)) {
        if (typeof lvl !== "number" || id.startsWith("toggle")) continue;
        hotmLevels[NODE_NAMES[id] || title(id)] = lvl;
    }

    const fs0 = st.playerStats?.fairySouls || {};
    const garden = d.garden || {};
    return {
        key,
        name: PLAYERS[key].name,
        sbLevel: Math.floor((d.skyblockXp || 0) / 100),
        networth: d.networth?.normal || 0,
        purse: d.purse || 0,
        skillAvg: st.skills?.average || null,
        skills: Object.fromEntries(Object.entries(skills).map(([k, v]) => [k, { level: v.level, max: v.maxLevel }])),
        cata: st.dungeons?.catacombsLevel || 0,
        bestClass,
        classes,
        f5: st.dungeons?.floors?.["5"]?.completions || 0,
        f6: st.dungeons?.floors?.["6"]?.completions || 0,
        secrets: st.dungeons?.secretsFound || 0,
        slayer,
        slayerXp: st.slayer?.totalXp || 0,
        mp: acc.highest_magical_power || st.accessories?.highestMagicalPower || 0,
        power: acc.selected_power || st.accessories?.selectedPower || null,
        tuningUsed,
        fairy: { collected: fs0.collected || 0, unspent: fs0.unspent || 0 },
        essence: st.miscStats?.essence || {},
        petScore: st.miscStats?.highestPetScore || null,
        pets: (d.pets || []).map(p => ({ type: p.type, tier: String(p.tier || "").toLowerCase(), level: p.level || 1, item: p.heldItem })),
        hotm: { levels: hotmLevels, tier: hotmTier, xp: hotmXp, tokens, source: "EliteBot" },
        nucleus: st.mining?.crystalNucleusRuns || 0,
        mineshafts: st.mining?.glacite?.mineshaftsEntered || 0,
        hotf: st.foraging?.hotfLevel || null,
        crimsonFaction: st.crimsonIsle?.selectedFaction || null,
        rift: { visits: st.rift?.visits || 0, souls: st.rift?.enigmaSoulsFound || 0 },
        copper: d.memberData?.garden?.copper ?? null,
        jacob: { medals: d.jacob?.medals || {}, extraFortune: d.jacob?.perks?.doubleDrops || 0, levelCap: d.jacob?.perks?.levelCap || 0 },
        farmingWeight: d.farmingWeight?.totalWeight || 0,
        garden: {
            level: garden.gardenLevel || 0,
            xp: garden.experience || 0,
            visitors: garden.completedVisitors || 0,
            uniqueVisitors: garden.uniqueVisitors || 0,
            cropUpgrades: garden.cropUpgrades || {},
            plots: (garden.plots || []).length,
            composter: garden.composter?.upgrades || {}
        }
    };
}

// ---------- rules for "biggest levers" ----------

function buildDiag(x) {
    const out = [];
    if (x.fairy.unspent > 0 || x.fairy.collected < 250) {
        out.push(["hi", `${x.fairy.collected} von ${FAIRY_SOULS_TOTAL} Fairy Souls`, `${x.fairy.unspent} gefundene sind noch nicht bei Tia eingelöst. Eintauschen gibt SkyBlock XP und Backpack-Slots.`]);
    }
    if (x.power === "simple" || !x.tuningUsed) {
        out.push(["hi", "Accessory Power und Tuning prüfen", `Power: ${x.power ? title(x.power) : "keine"}${x.tuningUsed ? "" : ", Tuning-Punkte nicht verteilt"}. Bei Maxwell setzen: rund ${Math.floor(x.mp / 10)} Tuning-Punkte bei ${x.mp} MP.`]);
    }
    if (x.mp < 300) {
        out.push(["hi", `Nur ${x.mp} Magical Power`, "Im Tab Accessories stehen die günstigsten nächsten Accessories in Reihenfolge."]);
    }
    if (!x.crimsonFaction) {
        out.push(["hi", "Crimson Isle: keine Faction", "Mages oder Barbarians wählen, gleich wie im Coop, damit ihr Kuudra zusammen laufen könnt."]);
    }
    const unclaimed = Object.entries(x.slayer).filter(([, v]) => v.unclaimed.length).map(([k, v]) => `${title(k)} ${v.unclaimed.join(", ")}`);
    if (unclaimed.length) out.push(["md", "Slayer-Rewards nicht abgeholt", `Bei Maddox abholen: ${unclaimed.join(" · ")}.`]);
    const maxTier = CROP_TIER_BY_GARDEN[Math.min(x.garden.level, 15)] || 0;
    const low = Object.entries(x.garden.cropUpgrades).filter(([, t]) => t < maxTier);
    if (low.length) out.push(["md", `Crop Upgrades unter Tier ${maxTier}`, `${low.length} Crops sind unter dem Maximum für Garden ${x.garden.level}. Copper zuerst in eure Haupt-Crops.`]);
    if (x.jacob.extraFortune === 0 && (x.jacob.medals.gold || 0) > 0) {
        out.push(["md", "Anita: Extra Farming Fortune auf 0", `${x.jacob.medals.gold} Gold-Medaillen vorhanden, jede Stufe gibt +4 Farming Fortune.`]);
    }
    const core = ["Mining Fortune", "Professional", "Efficient Miner"].filter(n => (x.hotm.levels[n] || 0) <= 1);
    if (core.length) out.push(["md", "HotM-Kernperks auf Stufe 1", `${core.join(", ")} brauchen Powder.`]);
    if ((x.skills.alchemy?.level || 0) < 30) out.push(["lo", `Alchemy ${x.skills.alchemy?.level || 0}`, "Günstigster Skill-Average-Boost mit Nether Wart aus den Sacks."]);
    if (x.rift.visits < 5) out.push(["lo", "Rift kaum besucht", `${x.rift.visits} Besuche, ${x.rift.souls} von 52 Enigma Souls. Rift Prism gibt +11 MP.`]);
    const order = { hi: 0, md: 1, lo: 2 };
    return out.sort((a, b) => order[a[0]] - order[b[0]]).slice(0, 8);
}

// ---------- apply to roadmap data ----------

function applyPlayer(data, x) {
    const p = data.P[x.key];
    const sk = x.skills;
    const slay = ["zombie", "spider", "wolf", "enderman", "blaze", "vampire"].map(k => x.slayer[k]?.level || 0).join("·");

    p.stats = [
        [String(x.mp), "Magical Power"],
        [String(x.sbLevel), "SkyBlock Level"],
        [fmtM(x.networth), `Networth · ${fmtM1(x.purse)} Purse`],
        [x.skillAvg != null ? String(x.skillAvg) : "–", "Skill Average"],
        [String(x.cata), `Catacombs · ${title(x.bestClass[0])} ${x.bestClass[1]}`],
        [slay, "Slayer Z·S·W·E·B·V"],
        [`${x.fairy.collected}/${FAIRY_SOULS_TOTAL}`, "Fairy Souls"],
        [String(Math.round(x.farmingWeight)), "Farming Weight"]
    ];
    const order = ["mining", "taming", "farming", "enchanting", "carpentry", "combat", "foraging", "fishing", "hunting", "alchemy"];
    p.skills = order.filter(k => sk[k]).map(k => [title(k), sk[k].level, sk[k].max]).sort((a, b) => b[1] / b[2] - a[1] / a[2]);
    if (x.skillAvg != null) p.sa = `Skill Average ${x.skillAvg}`;

    const oldNotes = Object.fromEntries((p.pets || []).map(([n, , , note]) => [n.toLowerCase(), note]));
    p.pets = x.pets
        .sort((a, b) => (RARITY_ORDER[a.tier] ?? 9) - (RARITY_ORDER[b.tier] ?? 9) || b.level - a.level)
        .map(pt => {
            const n = title(pt.type);
            return [n, pt.tier, pt.level, oldNotes[n.toLowerCase()] || (pt.item ? title(pt.item.replace(/^PET_ITEM_/, "")) : "")];
        });
    p.petcount = `${x.pets.length} Pets${x.petScore ? ` · Pet Score ${x.petScore}` : ""}`;

    const gap1 = (p.mp?.t1 || x.mp) - (p.mp?.now || x.mp);
    const gap2 = (p.mp?.t2 || x.mp) - (p.mp?.now || x.mp);
    p.mp = { now: x.mp, t1: x.mp + Math.max(gap1, 0), t2: x.mp + Math.max(gap2, 0) };

    const e = x.essence;
    const essence = ["WITHER", "UNDEAD", "DRAGON", "SPIDER", "ICE", "DIAMOND", "GOLD", "CRIMSON"]
        .filter(k => e[k] != null).map(k => `${title(k)} ${fmtInt(e[k])}`).join(" · ");
    const setExtra = (name, value) => {
        p.extra = p.extra || [];
        const row = p.extra.find(r => r[0] === name);
        if (row) row[1] = value; else p.extra.push([name, value]);
    };
    if (essence) setExtra("Essence", essence + ".");
    setExtra("Rift", `${x.rift.visits} Besuche, ${x.rift.souls} von 52 Enigma Souls.`);
    if (x.hotf) setExtra("Heart of the Forest", `Level ${x.hotf}`);

    p.diag = buildDiag(x);

    // HotM
    const hp = data.HOTM_P[x.key];
    for (const [n, lvl] of Object.entries(x.hotm.levels)) {
        // EliteBot can lag behind the game; never lower a level because of that.
        // After a HotM reset, correct the levels by hand in roadmap-data.json.
        hp.levels[n] = Math.max(hp.levels[n] || 0, lvl);
    }
    if (x.hotm.tier) {
        hp.tier = x.hotm.tier;
        hp.xp = fmtInt(x.hotm.xp);
        hp.toNext = x.hotm.tier >= 10 ? "Maximum erreicht" : `${fmtInt(HOTM_CUMULATIVE[x.hotm.tier] - x.hotm.xp)} XP bis Tier ${x.hotm.tier + 1}`;
    }
    if (x.hotm.tokens) hp.tokens = x.hotm.tokens;
    if (hp.levels["Core of the Mountain"]) hp.peak = hp.levels["Core of the Mountain"];
    hp.source = x.hotm.source;
}

function applyGarden(data, xs) {
    const g = data.GARDEN;
    const x = xs[0];
    const gd = x.garden;
    const maxTier = CROP_TIER_BY_GARDEN[Math.min(gd.level, 15)] || 0;
    const tiers = Object.values(gd.cropUpgrades);
    const minTier = tiers.length ? Math.min(...tiers.filter(t => t > 0)) : 0;
    const comp = gd.composter;
    const cumulative = [0, 70, 140, 280, 520, 1120, 2620, 4620, 7120, 10120, 20120, 30120, 40120, 50120, 60120];
    const toNext = gd.level >= 15 ? "Maximum" : `${fmtInt(cumulative[gd.level] - gd.xp)} XP bis Level ${gd.level + 1}`;
    g.status = [
        ["Garden Level", String(gd.level), toNext],
        ["Crop Upgrades", `${minTier} / 9`, `Garden ${gd.level} erlaubt bis Tier ${maxTier}`],
        ["Plots", `${gd.plots} / 24`, "+3 Farming Fortune pro Plot"],
        ["Composter", `Speed ${comp.speed || 0} · Multi ${comp.multi_drop || 0}`, `Fuel ${comp.fuel_cap || 0} · OM ${comp.organic_matter_cap || 0} · Cost ${comp.cost_reduction || 0}`],
        ["Visitors", `${gd.visitors} erledigt`, `${gd.uniqueVisitors} verschiedene`],
        ["Anita Extra Fortune", `${x.jacob.extraFortune} / 15`, "+4 Farming Fortune pro Stufe"]
    ];
    g.copperBalance = Object.fromEntries(xs.map(v => [v.key, v.copper]));
    g.tierBought = minTier;
    g.tierMax = maxTier;
}

function applyCompare(data, xs) {
    const [a, b] = xs;
    data.VS = [
        ["Skill Average", a.skillAvg, b.skillAvg],
        ["Mining", a.skills.mining?.level, b.skills.mining?.level],
        ["Farming", a.skills.farming?.level, b.skills.farming?.level],
        ["Combat", a.skills.combat?.level, b.skills.combat?.level],
        ["Enchanting", a.skills.enchanting?.level, b.skills.enchanting?.level],
        ["Catacombs", a.cata, b.cata],
        ["Magical Power", a.mp, b.mp],
        ["Slayer XP", Math.round(a.slayerXp / 100) / 10 + " k", Math.round(b.slayerXp / 100) / 10 + " k"],
        ["Fairy Souls", a.fairy.collected, b.fairy.collected],
        ["Networth", fmtM(a.networth), fmtM(b.networth)]
    ];
}

// ---------- change detection ----------

function snapshot(data) {
    const s = {};
    for (const key of Object.keys(PLAYERS)) {
        const p = data.P[key];
        if (!p) continue;
        for (const [v, l] of p.stats || []) s[`${key}|${String(l).split(" · ")[0]}`] = v;
        for (const [n, lvl] of p.skills || []) s[`${key}|${n}`] = lvl;
        for (const [n, lvl] of Object.entries(data.HOTM_P?.[key]?.levels || {})) s[`${key}|HotM ${n}`] = lvl;
    }
    for (const [l, v] of data.GARDEN?.status || []) s[`coop|${l}`] = v;
    return s;
}

function diff(before, after, data) {
    const changes = [];
    for (const [k, v] of Object.entries(after)) {
        if (before[k] === undefined || String(before[k]) === String(v)) continue;
        const [who, label] = k.split("|");
        const name = who === "coop" ? "Coop" : data.P[who]?.name || who;
        changes.push(`**${name}** · ${label}: ${before[k]} → ${v}`);
    }
    return changes;
}

// ---------- main ----------

async function runUpdate({ reason = "manuell" } = {}) {
    const data = JSON.parse(fs.readFileSync(DATA_FILE, "utf8"));
    const before = snapshot(data);
    const keys = Object.keys(PLAYERS);
    const raw = await Promise.all(keys.map(fetchPlayer));
    const xs = keys.map((k, i) => extract(k, raw[i]));
    xs.forEach(x => applyPlayer(data, x));
    applyGarden(data, xs);
    applyCompare(data, xs);
    const now = zurichNow();
    data.meta = {
        ...data.meta,
        updated: now.label,
        updatedDate: now.date,
        updatedAt: new Date().toISOString(),
        reason,
        sources: ["EliteBot API"]
    };
    const changes = diff(before, snapshot(data), data);
    fs.copyFileSync(DATA_FILE, PREV_FILE);
    const tmp = DATA_FILE + ".tmp";
    fs.writeFileSync(tmp, JSON.stringify(data, null, 1));
    fs.renameSync(tmp, DATA_FILE);
    console.log(`[ROADMAP] Update (${reason}) ${now.label}: ${changes.length} Änderungen`);
    return { data, changes, xs };
}

module.exports = { runUpdate, zurichNow, _test: { extract, applyPlayer, applyGarden, applyCompare, buildDiag, snapshot, diff } };
