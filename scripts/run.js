// Holt neue Daten (EliteBot API) und baut docs/index.html für GitHub Pages.
// Lokal testen: node scripts/run.js   (Node 20+)
const path = require("path");
process.env.ROADMAP_DATA_DIR = path.join(__dirname, "..", "docs");
process.env.ROADMAP_PAGE_DIR = process.env.ROADMAP_DATA_DIR;
process.env.ROADMAP_PAGE_NAME = "index.html";
const { runUpdate } = require("./update");
const { buildPage } = require("./page");
const fs = require("fs");

(async () => {
    const onlyBuild = process.argv.includes("--build-only");
    let data;
    if (onlyBuild) {
        data = JSON.parse(fs.readFileSync(path.join(process.env.ROADMAP_DATA_DIR, "roadmap-data.json"), "utf8"));
    } else {
        const result = await runUpdate({ reason: process.env.GITHUB_EVENT_NAME === "schedule" ? "automatisch" : "manuell" });
        data = result.data;
        console.log(result.changes.length ? result.changes.join("\n") : "Keine Änderungen");
    }
    buildPage(data);
    // prev-Datei nicht veröffentlichen
    fs.rmSync(path.join(process.env.ROADMAP_DATA_DIR, "roadmap-data.prev.json"), { force: true });
    fs.writeFileSync(path.join(process.env.ROADMAP_DATA_DIR, ".nojekyll"), "");
    console.log("docs/index.html gebaut");
})().catch(err => {
    console.error(err);
    process.exit(1);
});
