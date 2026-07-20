<#
.SYNOPSIS
    Installs everything GeoRoute needs on Windows and puts it on PATH.

    Installs (via Chocolatey):
      - Eclipse Temurin JDK 17   (sets JAVA_HOME + PATH so Maven uses JDK 17)
      - Maven
      - Node.js LTS              (only if node is not already present)
      - GDAL                     (optional, for GeoTIFF prep)
    Plus:
      - Angular CLI 17           (global, via npm)
      - PostgreSQL + PostGIS     (Docker container by default; native fallback)

    NOT installed automatically: the GIS data files (GeoTIFF + roads Shapefile).
    Those are large/license-bound — see data\README.md.

.PARAMETER DbMode
    docker  : run a postgis/postgis container (recommended; requires Docker Desktop)
    native  : install PostgreSQL via Chocolatey (PostGIS added via StackBuilder, manual)
    skip    : do not touch the database
    auto    : (default) use docker if available, else native

.PARAMETER DbPassword
    Password for the 'postgres' user / container. Default: georoute

.EXAMPLE
    # Run from an ELEVATED PowerShell (Run as Administrator), in the project root:
    Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
    .\install-prereqs.ps1
#>

[CmdletBinding()]
param(
    [ValidateSet('auto', 'docker', 'native', 'skip')]
    [string]$DbMode = 'auto',
    [string]$DbPassword = 'georoute'
)

#requires -RunAsAdministrator
$ErrorActionPreference = 'Stop'
$ScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Definition

function Write-Step($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }
function Write-Info($msg) { Write-Host "    $msg" -ForegroundColor DarkGray }
function Write-Ok($msg)   { Write-Host "    $msg" -ForegroundColor Green }
function Write-Warn2($msg){ Write-Host "    $msg" -ForegroundColor Yellow }

function Test-Admin {
    $id = [Security.Principal.WindowsIdentity]::GetCurrent()
    $p = New-Object Security.Principal.WindowsPrincipal($id)
    return $p.IsInRole([Security.Principal.WindowsBuiltinRole]::Administrator)
}

function Update-SessionPath {
    # Refresh the current session PATH from Machine + User so newly installed tools resolve.
    $machine = [Environment]::GetEnvironmentVariable('Path', 'Machine')
    $user = [Environment]::GetEnvironmentVariable('Path', 'User')
    $env:Path = ($machine, $user | Where-Object { $_ }) -join ';'
}

function Add-MachinePath($dir) {
    if (-not (Test-Path $dir)) { return }
    $machine = [Environment]::GetEnvironmentVariable('Path', 'Machine')
    $parts = $machine -split ';' | Where-Object { $_ -and $_.Trim() }
    if ($parts -notcontains $dir) {
        $new = ($parts + $dir) -join ';'
        [Environment]::SetEnvironmentVariable('Path', $new, 'Machine')
        Write-Ok "Added to PATH (Machine): $dir"
    } else {
        Write-Info "Already on PATH: $dir"
    }
}

function Have($cmd) { return [bool](Get-Command $cmd -ErrorAction SilentlyContinue) }

# ─────────────────────────────────────────────────────────────────────────────
if (-not (Test-Admin)) {
    Write-Error "Please run this script in an ELEVATED PowerShell (Run as Administrator)."
}

Write-Step "GeoRoute prerequisite installer"
Write-Info "Project root: $ScriptRoot"
Write-Info "DbMode=$DbMode"

# ── 1. Chocolatey ────────────────────────────────────────────────────────────
Write-Step "Ensuring Chocolatey is installed"
if (-not (Have 'choco')) {
    Write-Info "Installing Chocolatey..."
    Set-ExecutionPolicy Bypass -Scope Process -Force
    [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072
    Invoke-Expression ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))
    Update-SessionPath
} else {
    Write-Ok "Chocolatey present: $(choco --version)"
}

# ── 2. Toolchain via Chocolatey ──────────────────────────────────────────────
Write-Step "Installing JDK 17 (Temurin) and Maven"
choco install temurin17 maven -y --no-progress
Update-SessionPath

if (-not (Have 'node')) {
    Write-Step "Installing Node.js LTS (node not found)"
    choco install nodejs-lts -y --no-progress
    Update-SessionPath
} else {
    Write-Ok "Node present: $(node --version)"
}

Write-Step "Installing GDAL (optional, for GeoTIFF prep)"
try {
    choco install gdal -y --no-progress
    Update-SessionPath
} catch {
    Write-Warn2 "GDAL install failed (optional). You can install OSGeo4W manually later."
}

# ── 3. JAVA_HOME → JDK 17, and PATH ──────────────────────────────────────────
Write-Step "Configuring JAVA_HOME to point at JDK 17"
$adoptiumRoot = 'C:\Program Files\Eclipse Adoptium'
$jdk17 = $null
if (Test-Path $adoptiumRoot) {
    $jdk17 = Get-ChildItem $adoptiumRoot -Directory -Filter 'jdk-17*' |
        Sort-Object Name -Descending | Select-Object -First 1
}
if ($jdk17) {
    [Environment]::SetEnvironmentVariable('JAVA_HOME', $jdk17.FullName, 'Machine')
    $env:JAVA_HOME = $jdk17.FullName
    Write-Ok "JAVA_HOME = $($jdk17.FullName)"
    Add-MachinePath (Join-Path $jdk17.FullName 'bin')
    Update-SessionPath
} else {
    Write-Warn2 "Could not locate a JDK 17 under '$adoptiumRoot'. Set JAVA_HOME manually."
}

# ── 4. Angular CLI 17 (global) ───────────────────────────────────────────────
Write-Step "Installing Angular CLI 17 (global)"
if (Have 'npm') {
    npm install -g '@angular/cli@17'
    Update-SessionPath
    Write-Ok "Angular CLI installed."
} else {
    Write-Warn2 "npm not found on PATH yet. Open a NEW terminal and run: npm install -g @angular/cli@17"
}

# ── 5. PostgreSQL + PostGIS ──────────────────────────────────────────────────
function Setup-DockerDb {
    Write-Step "Starting PostgreSQL + PostGIS via Docker"
    $initSql = Join-Path $ScriptRoot 'db\init.sql'
    $existing = (docker ps -a --filter "name=georoute-postgis" --format "{{.Names}}") 2>$null
    if ($existing -eq 'georoute-postgis') {
        Write-Info "Container 'georoute-postgis' already exists; starting it."
        docker start georoute-postgis | Out-Null
    } else {
        # Mount init.sql so the schema (PostGIS extensions + tables) is created on first start.
        # Docker Desktop expects forward slashes (drive colon is handled specially).
        $initSqlDocker = $initSql -replace '\\', '/'
        $mount = "${initSqlDocker}:/docker-entrypoint-initdb.d/init.sql"
        docker run -d --name georoute-postgis `
            -e POSTGRES_PASSWORD=$DbPassword `
            -e POSTGRES_DB=georoute `
            -p 5432:5432 `
            -v $mount `
            postgis/postgis:16-3.4 | Out-Null
        Write-Ok "Container 'georoute-postgis' started (db=georoute, user=postgres, pass=$DbPassword)."
        Write-Info "Schema from db\init.sql is applied automatically on first start."
    }
    Write-Warn2 "Set spring.datasource.password=$DbPassword in georoute-backend\src\main\resources\application.properties"
}

function Setup-NativeDb {
    Write-Step "Installing PostgreSQL via Chocolatey"
    choco install postgresql -y --no-progress --params "/Password:$DbPassword"
    Update-SessionPath
    Write-Warn2 "PostGIS is NOT installed by the choco package."
    Write-Warn2 "Add PostGIS via Stack Builder: Start Menu -> 'Application Stack Builder' -> Spatial Extensions -> PostGIS."
    Write-Warn2 "Then create the DB and schema:"
    Write-Info   "  createdb -U postgres georoute"
    Write-Info   "  psql -U postgres -d georoute -f db\init.sql"
    Write-Warn2 "Set spring.datasource.password=$DbPassword in application.properties."
}

switch ($DbMode) {
    'skip'   { Write-Step "Skipping database setup (DbMode=skip)" }
    'docker' { if (Have 'docker') { Setup-DockerDb } else { Write-Error "DbMode=docker but Docker is not installed/on PATH." } }
    'native' { Setup-NativeDb }
    'auto'   {
        if (Have 'docker') { Setup-DockerDb }
        else {
            Write-Warn2 "Docker not found; falling back to native PostgreSQL install."
            Setup-NativeDb
        }
    }
}

# ── 6. Summary ───────────────────────────────────────────────────────────────
Write-Step "Done. Open a NEW terminal so PATH/JAVA_HOME changes take effect, then verify:"
Write-Info "  java -version        (expect 17)"
Write-Info "  mvn -version"
Write-Info "  node --version ; npm --version"
Write-Info "  ng version"
Write-Info "  gdalinfo --version   (optional)"
Write-Host ""
Write-Step "Next steps"
Write-Info "  1. Put GIS data into .\data  (see data\README.md)"
Write-Info "  2. Run .\setup.bat  (npm install, fonts, mvn dependency:go-offline)"
Write-Info "  3. cd georoute-backend ; mvn spring-boot:run"
Write-Info "  4. cd georoute-frontend ; npm start   -> http://localhost:4200"
