@echo off
cd /d "%~dp0"
where git >nul 2>nul || (echo Git fehlt. Bitte installieren: https://git-scm.com/download/win & pause & exit /b 1)
git pull --rebase origin main
echo.
echo Neueste Daten und Seite von GitHub geholt.
pause
