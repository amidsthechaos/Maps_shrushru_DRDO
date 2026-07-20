@echo off
REM Launch GeoRoute offline bundle (same folder as this script).
REM Requires: JDK 17 on PATH, PostgreSQL+PostGIS with DB "georoute" initialized.
setlocal
cd /d "%~dp0"

where java >nul 2>nul
if errorlevel 1 (
  echo ERROR: java not found. Install JDK 17 and add it to PATH.
  exit /b 1
)

if not exist "georoute-backend-1.0.0.jar" (
  echo ERROR: georoute-backend-1.0.0.jar missing. Run scripts\package-offline.ps1 on a build machine first.
  exit /b 1
)

if not exist "application.properties" (
  echo ERROR: application.properties missing.
  exit /b 1
)

echo ==^> Starting GeoRoute on http://localhost:8080
echo     Edit application.properties for DB password / GIS paths if needed.
echo.
java -Xmx2g -jar georoute-backend-1.0.0.jar --spring.config.additional-location=optional:file:./
