@echo off
REM Installs Maven + JDK 17 for GeoRoute backend.
REM GeoRoute requires JDK 17 (Java 26 breaks GeoTools). Maven is not on winget — we download it.
REM Run as Administrator.

echo ==^> GeoRoute: install Maven + JDK 17
echo.

where winget >nul 2>nul
if not errorlevel 1 (
  echo ==^> Installing JDK 17 ^(Eclipse Temurin^) via winget...
  winget install -e --id EclipseAdoptium.Temurin.17.JDK --accept-package-agreements --accept-source-agreements
) else (
  echo WARNING: winget not found. Install JDK 17 manually from https://adoptium.net/
)

echo.
echo ==^> Installing Apache Maven ^(direct download^)...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\install-maven.ps1"
if errorlevel 1 goto :error

echo.
echo ==^> Setting JAVA_HOME to JDK 17...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\set-java17.ps1"

echo.
echo ==^> Done. CLOSE this window, open a NEW terminal, then verify:
echo     java -version     ^(should show 17^)
echo     mvn -version
echo.
echo Then run: setup.bat
goto :eof

:error
echo.
echo ==^> Install FAILED. Run this batch file as Administrator.
exit /b 1
