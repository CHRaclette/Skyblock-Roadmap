@echo off
cd /d "%~dp0bot"
if not exist .env (copy .env.example .env >nul & echo .env wurde aus .env.example erstellt. Bitte DISCORD_TOKEN und GITHUB_TOKEN eintragen und dann erneut starten. & notepad .env & pause & exit /b 1)
where node >nul 2>nul || (echo Node.js fehlt. Bitte installieren: https://nodejs.org & pause & exit /b 1)
if not exist node_modules call npm install
node index.js
pause
