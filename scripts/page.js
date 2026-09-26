// ============================================================
// RASPBERRY ROADMAP — builds the self-contained HTML page
// Template + current data = one HTML file that works on its own
// (served by the bot, or opened as a file from Discord).
// ============================================================

const fs = require("fs");
const path = require("path");

const DATA_DIR = process.env.ROADMAP_DATA_DIR || __dirname;
const PAGE_FILE = path.join(process.env.ROADMAP_PAGE_DIR || DATA_DIR, process.env.ROADMAP_PAGE_NAME || "roadmap.html");
const TEMPLATE_DIR = process.env.ROADMAP_TEMPLATE_DIR || __dirname;
const MARKER = "/*__ROADMAP_DATA__*/";

// Keep "</script>" inside strings from closing the script tag
const js = value => JSON.stringify(value ?? null).replace(/</g, "\\u003c");

function buildPage(data) {
    const template = fs.readFileSync(path.join(TEMPLATE_DIR, "roadmap-template.html"), "utf8");
    if (!template.includes(MARKER)) throw new Error("roadmap-template.html: Daten-Marker fehlt");
    const vars =
        `var P=${js(data.P)};\n` +
        `var HOTM_TREE=${js(data.HOTM_TREE)};\n` +
        `var HOTM_BUILDS=${js(data.HOTM_BUILDS)};\n` +
        `var HOTM_P=${js(data.HOTM_P)};\n` +
        `var GARDEN=${js(data.GARDEN)};\n` +
        `var DAILY=${js(data.DAILY)};\n` +
        `var VS=${js(data.VS)};\n` +
        `var META=${js(data.meta)};\n`;
    const date = String(data.meta?.updated || "").split(" ")[0];
    const html = template
        .replace(MARKER, vars)
        .replace("Daten 26.09.2026", `Daten ${date}`);
    fs.mkdirSync(path.dirname(PAGE_FILE), { recursive: true });
    const tmp = PAGE_FILE + ".tmp";
    fs.writeFileSync(tmp, html);
    fs.renameSync(tmp, PAGE_FILE);
    return PAGE_FILE;
}

module.exports = { buildPage, PAGE_FILE };
