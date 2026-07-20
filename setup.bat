@echo off
REM GeoRoute one-time ONLINE setup (Windows).
REM After this completes, the app builds and runs fully offline.
setlocal enabledelayedexpansion

set "ROOT=%~dp0"
set "FRONTEND=%ROOT%georoute-frontend"
set "BACKEND=%ROOT%georoute-backend"
set "FONTS=%FRONTEND%\src\assets\fonts"

echo ==^> GeoRoute setup starting

REM -- 1. Angular CLI (global, one time) --------------------------------------
where ng >nul 2>nul
if errorlevel 1 (
  echo ==^> Installing Angular CLI 17 globally
  call npm install -g @angular/cli@17 || goto :error
) else (
  echo ==^> Angular CLI already installed
)

REM -- 2. Frontend npm packages (includes OpenLayers) -------------------------
echo ==^> Installing frontend npm packages
pushd "%FRONTEND%"
call npm install || goto :error
popd

REM -- 3. Self-hosted fonts ---------------------------------------------------
echo ==^> Downloading self-hosted fonts
if not exist "%FONTS%" mkdir "%FONTS%"
set "TMP=%TEMP%\georoute-fonts"
if exist "%TMP%" rmdir /s /q "%TMP%"
mkdir "%TMP%"

REM JetBrains Mono (OFL-1.1)
powershell -NoProfile -Command "Invoke-WebRequest -Uri 'https://github.com/JetBrains/JetBrainsMono/releases/download/v2.304/JetBrainsMono-2.304.zip' -OutFile '%TMP%\jbmono.zip'" || goto :error
powershell -NoProfile -Command "Expand-Archive -Force '%TMP%\jbmono.zip' '%TMP%\jbmono'" || goto :error
copy /Y "%TMP%\jbmono\fonts\webfonts\JetBrainsMono-Regular.woff2" "%FONTS%\" >nul
copy /Y "%TMP%\jbmono\fonts\webfonts\JetBrainsMono-Medium.woff2" "%FONTS%\" >nul

REM Inter (OFL-1.1)
powershell -NoProfile -Command "Invoke-WebRequest -Uri 'https://github.com/rsms/inter/releases/download/v4.0/Inter-4.0.zip' -OutFile '%TMP%\inter.zip'" || goto :error
powershell -NoProfile -Command "Expand-Archive -Force '%TMP%\inter.zip' '%TMP%\inter'" || goto :error
copy /Y "%TMP%\inter\Inter Desktop\Inter-Regular.woff2" "%FONTS%\" >nul

rmdir /s /q "%TMP%"
echo ==^> Fonts installed in %FONTS%

REM -- 4. Backend Maven dependencies (cache locally) --------------------------
where mvn >nul 2>nul
if errorlevel 1 (
  echo.
  echo ==^> Maven ^(mvn^) is not installed or not on PATH.
  echo     GeoRoute backend needs Maven + JDK 17.
  echo.
  echo     Fix: run this from the project root ^(as Administrator if winget asks^):
  echo       install-maven-jdk.bat
  echo     Then open a NEW terminal and run setup.bat again.
  echo.
  echo     Frontend setup ^(npm + fonts^) completed; backend step skipped.
  goto :done_no_mvn
)

echo ==^> Caching backend Maven dependencies (full online install — required for offline builds)
pushd "%BACKEND%"
call mvn install || goto :error
popd

:done_no_mvn

echo.
echo ==^> Setup complete.
echo     Next:
echo       1. Put GIS data into .\data (see data\README.md)
echo       2. createdb georoute ^&^& psql -d georoute -f db\init.sql
echo       3. Set DB password in georoute-backend\src\main\resources\application.properties
echo       4. cd georoute-backend ^&^& mvn spring-boot:run
echo       5. cd georoute-frontend ^&^& npm start
goto :eof

:error
echo.
echo ==^> Setup FAILED. See the error above.
exit /b 1
