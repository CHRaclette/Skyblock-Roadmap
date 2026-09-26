# Skyblock-Bot + Raspberry Roadmap

Die Roadmap-Seite läuft auf GitHub Pages und/oder direkt im Bot. Kein Claude nötig.

## GitHub Pages (fester Link, empfohlen)
Die Website liegt zusätzlich als eigenes Repository bei (`skyblock-roadmap.zip`, Anleitung im README dort).
GitHub Actions baut sie täglich neu, auch wenn der Bot offline ist.
In `bot/.env` dann `GITHUB_REPO` und `GITHUB_TOKEN` setzen: alle Buttons zeigen auf `https://chraclette.github.io/Skyblock-Roadmap/`, und `/update` startet den Pages-Build mit.

## Starten (empfohlen: Docker)
1. Deine bestehende `.env` nach `bot/.env` kopieren (Vorlage: `bot/.env.example`).
2. `docker compose up -d --build`
3. Den öffentlichen Link anzeigen: `docker compose logs skyblock-bot | grep "Öffentlicher Link"`
   (oder in Discord `/website`).

Docker startet zwei Dienste:
- **skyblock-bot**: Bot, Dashboard (`localhost:3000`, nur auf diesem Rechner) und Roadmap-Seite (`localhost:3001/roadmap`).
- **cloudflared**: kostenloser Cloudflare Quick Tunnel. Macht nur die Roadmap-Seite (Port 3001) öffentlich über `https://…trycloudflare.com/roadmap`, ohne Port-Freigabe am Router. Der Bot liest den Link automatisch aus und hängt ihn als Button an jede Antwort.

Hinweis: Der Quick-Tunnel-Link ändert sich bei jedem Neustart. Für einen festen Link eine eigene Domain nutzen und `PUBLIC_URL` setzen (z. B. mit einem benannten Cloudflare Tunnel).

Ohne Docker: `cd bot && npm install && npm start`. Dann gibt es keinen Tunnel; der Bot schickt die Seite in Discord als HTML-Datei (herunterladen und im Browser öffnen).

## Discord-Befehle
/update · /roadmap [spieler] · /daily · /hotm [spieler] [setup] · /garden [spieler] · /mp [spieler] · /setup [spieler] [bereich] · /website

Ohne `spieler` nimmt der Bot ReverseAmin, wenn der Discord-Name "amin" oder "reverse" enthält, sonst Relaxo_GX.
`/website` schickt immer den Link (falls vorhanden) und zusätzlich die Seite als Datei.

## Daten aktualisieren
- `/update` holt sofort neue Profildaten über die EliteBot API (kein Hypixel API Key nötig) und baut die Seite neu. Cooldown 2 Minuten.
- Automatisch mindestens 1× täglich ab `ROADMAP_UPDATE_HOUR` (Standard 6:00, Europe/Zurich) und direkt nach dem Start, wenn die Daten älter als 24 h sind. Mit `ROADMAP_CHANNEL_ID` wird das Ergebnis mit allen Änderungen gepostet.
- Die Seite lädt beim Öffnen die aktuellen Daten von `/api/roadmap` und zeigt oben „· live“.

Automatisch neu berechnet: Stats, Skills, Slayer, Pets, HotM-Stufen, Garden-Status, Copper, Vergleich und „Grösste Hebel“.
Unverändert bleiben die handgeschriebenen Pläne (Next Steps, Accessory-Reihenfolge, Setups).
HotM-Tier und Tokens stehen nicht in den EliteBot-Daten; dafür bleiben die zuletzt eingetragenen Werte. HotM-Stufen werden nie gesenkt; nach einem HotM-Reset die Werte in `roadmap-data.json` von Hand anpassen.

## Sicherheit
- Öffentlich ist nur Port 3001: Seite und Daten zum Lesen. Das Dashboard mit Admin-Funktionen (Kanäle erstellen/löschen) bleibt auf `127.0.0.1:3000`.
- `POST /api/roadmap/update` gibt es öffentlich nur, wenn `ROADMAP_UPDATE_TOKEN` gesetzt ist, und nur mit Header `x-update-token`.

## Dateien
- `bot/roadmap.js`: Discord-Befehle, öffentlicher Server, Tunnel-Link, täglicher Scheduler
- `bot/update.js`: holt Live-Daten und rechnet die Roadmap neu
- `bot/page.js` + `bot/roadmap-template.html`: bauen die Seite als eine HTML-Datei
- `bot/roadmap-data.json`: Startdaten (Docker kopiert sie beim ersten Start in das Volume `roadmap-data`)
- `bot/index.js`: kleine Einbindungen (require, Befehlsliste, Slash-Handler, Routen, Server- und Scheduler-Start) und /help-Text
