@echo off
cd /d "%~dp0"
where git >nul 2>nul || (echo Git fehlt. Bitte installieren: https://git-scm.com/download/win & pause & exit /b 1)
git pull --rebase origin main
git add -A
git commit -m "Update von %USERNAME%"
git push origin main
echo.
echo Fertig. Seite: https://chraclette.github.io/Skyblock-Roadmap/
pause
