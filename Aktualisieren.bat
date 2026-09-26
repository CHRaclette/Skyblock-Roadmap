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
echo.
echo Ordner ist mit GitHub verbunden und auf dem neuesten Stand.
pause
