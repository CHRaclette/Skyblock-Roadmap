# Raspberry Roadmap · GitHub Pages

Die Ironman-Roadmap für Relaxo_GX und ReverseAmin als statische Website.
GitHub Actions holt jeden Morgen neue Daten (EliteBot API, kein Hypixel API Key nötig), baut `docs/index.html` neu und veröffentlicht sie.

## Einrichten (einmalig, ca. 5 Minuten)
1. Auf GitHub als **CHRaclette** ein neues **öffentliches** Repository `Skyblock-Roadmap` anlegen (erledigt)
2. Den Inhalt dieses Ordners hochladen (Web-Upload per Drag & Drop oder `git push`).
   Wichtig: der Ordner `.github` muss mit hochgeladen werden.
3. Repository → **Settings → Pages** → Source: **GitHub Actions**.
4. **Actions**-Tab → Workflow „Roadmap aktualisieren und veröffentlichen“ → **Run workflow**.
5. Nach 1–2 Minuten ist die Seite online: `https://chraclette.github.io/Skyblock-Roadmap/`

## Aktualisierung
- Automatisch täglich um ca. 6:17 Uhr (Zürich).
- Manuell: Actions → Run workflow, oder im Discord `/update` (siehe unten).
- Neue Daten werden als Commit in `docs/roadmap-data.json` gespeichert; so sieht der nächste Lauf, was sich geändert hat.

## Discord-Bot verbinden
In der `.env` des Bots:
```
GITHUB_REPO=CHRaclette/Skyblock-Roadmap
GITHUB_TOKEN=github_pat_...
```
- Der Token ist ein **Fine-grained personal access token** (GitHub → Settings → Developer settings) nur für dieses Repository mit der Berechtigung **Actions: Read and write**.
- Danach zeigen alle Bot-Buttons auf die GitHub-Pages-Seite, und `/update` startet zusätzlich den Pages-Build.

## Dateien
- `docs/index.html`: die Website (eine Datei, alle Daten eingebaut)
- `docs/roadmap-data.json`: aktuelle Daten
- `scripts/update.js`: holt Live-Daten und rechnet Stats, HotM, Garden und die grössten Hebel neu
- `scripts/page.js`, `scripts/roadmap-template.html`: bauen die Seite
- `scripts/run.js`: beides zusammen, wird von GitHub Actions aufgerufen
- `.github/workflows/pages.yml`: täglicher Build und Veröffentlichung

## Lokal testen
```
node scripts/run.js --build-only   # nur Seite aus vorhandenen Daten bauen
node scripts/run.js                # neue Daten holen und bauen
```
