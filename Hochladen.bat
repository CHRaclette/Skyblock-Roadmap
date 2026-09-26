@echo off
cd /d "%~dp0"
where git >nul 2>nul || (echo Git fehlt. Bitte installieren: https://git-scm.com/download/win & pause & exit /b 1)
if not exist .git (
  echo Verbinde Ordner mit GitHub...
  git init -b main
  git remote add origin https://github.com/CHRaclette/Skyblock-Roadmap.git
  git fetch origin
  git reset origin/main
  git checkout -- .
  git branch --set-upstream-to=origin/main main
)
git pull --rebase origin main
git add -A
git commit -m "Update von %USERNAME%"
git push origin main
echo.
echo Fertig. Seite: https://chraclette.github.io/Skyblock-Roadmap/
pause
