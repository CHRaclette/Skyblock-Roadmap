# Übergabe für neue Claude-Sessions

Stand: 26.09.2026. Alles Wichtige liegt in diesem Repository, nichts nur in einer Chat-Session.

## Was es gibt
- **Website** (GitHub Pages): https://chraclette.github.io/Skyblock-Roadmap/
  - `docs/index.html` wird aus `scripts/roadmap-template.html` + `docs/roadmap-data.json` gebaut (`scripts/page.js`).
  - `scripts/update.js` holt Live-Daten von der EliteBot API (`https://api.elitebot.dev/profile/<uuid>/<profilId>`) und rechnet Stats, Skills, Slayer, Pets, HotM-Stufen, Garden, Vergleich und „Grösste Hebel“ neu.
  - `.github/workflows/pages.yml`: täglich 04:17 UTC + manuell + bei Änderungen an `scripts/`; committet neue Daten zurück und deployt Pages.
- **Discord-Bot** in `discord-bot/` (ohne `.env`): Befehle /update /roadmap /daily /hotm /garden /mp /setup /website; tägliches Auto-Update; öffentlicher Roadmap-Server auf Port 3001; Cloudflare-Quick-Tunnel in docker-compose; GitHub-Anbindung (`GITHUB_REPO`, `GITHUB_TOKEN` → startet den Pages-Workflow).

## Profil
- Profil „Raspberry“ (Ironman), Profil-ID `db1dd88b25b64e998c5082d8c0548274`
- Relaxo_GX: UUID `5fdf17a46d3c48d58f9eb80698c62398`
- ReverseAmin: UUID `7b8b783fd39348b297a20e6771bdfff6`
- Weitere Quellen: SkyCrypt (Wardrobe, Accessories, exakte HotM-Stufen), Hypixel SkyBlock Wiki (hypixelskyblock.minecraft.wiki) für alle Fakten.

## Entscheidungen
- Kein Hypixel API Key (veraltet/nicht nutzbar) → nur EliteBot.
- HotM-Tier/Tokens nicht in EliteBot → letzte Werte bleiben; Stufen werden nie gesenkt.
- Copper ist co-op-geteilt.
- Fairy Souls total 289; Eintauschen gibt SkyBlock XP + Backpack-Slots (keine Stats).
- Pet Score gibt Magic Find (nicht Magical Power).
- Handgeschriebene Pläne (Next Steps, Accessory-Reihenfolge, Setups) stehen in `docs/roadmap-data.json` unter `P.<spieler>.phases/acc/setups` und werden vom Update nicht überschrieben.
- Mining-Fahrplan (HotM-Tab) und Pest-Farming-Guide (Garden-Tab) stehen in `scripts/guides-data.js` → schreibt `MINING_GUIDE` / `PEST_GUIDE` in die Daten. Ändern: Datei bearbeiten, `node scripts/guides-data.js`, dann `node scripts/run.js --build-only`.

## Erledigt
- 26.09.2026: GitHub Pages auf Source „GitHub Actions“ gestellt, Workflow läuft erfolgreich, Seite ist live.

## Offene Punkte
- Bot-Token neu erstellen (alte ZIP enthielt ihn) und in `discord-bot/bot/.env` eintragen.
- `GITHUB_TOKEN` (fine-grained, nur dieses Repo, Actions: Read and write) in die Bot-`.env`.

## Neue Session starten
In der neuen Session sagen: „Lies SESSION-UEBERGABE.md im Repo CHRaclette/Skyblock-Roadmap und mach weiter.“
