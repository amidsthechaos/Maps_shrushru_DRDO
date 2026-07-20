# GeoRoute offline package — run ONCE on a machine with internet / Node / Maven.
# Produces a portable folder: offline-bundle/
# That folder needs only JDK 17 + PostgreSQL/PostGIS on the target machine (no npm, no Maven).

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$Frontend = Join-Path $Root 'georoute-frontend'
$Backend = Join-Path $Root 'georoute-backend'
$Fonts = Join-Path $Frontend 'src\assets\fonts'
$Out = Join-Path $Root 'offline-bundle'

Write-Host '==> GeoRoute offline package'
Write-Host "    Root: $Root"

# ── Fonts (required for production Angular build) ───────────────────────────
$requiredFonts = @(
    'JetBrainsMono-Regular.woff2',
    'JetBrainsMono-Medium.woff2',
    'Inter-Regular.woff2'
)
if (-not (Test-Path $Fonts)) { New-Item -ItemType Directory -Path $Fonts | Out-Null }
$missing = @($requiredFonts | Where-Object { -not (Test-Path (Join-Path $Fonts $_)) })
if ($missing.Count -gt 0) {
    Write-Host '==> Downloading self-hosted fonts...'
    $tmp = Join-Path $env:TEMP 'georoute-fonts-pack'
    if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }
    New-Item -ItemType Directory -Path $tmp | Out-Null
    $jb = Join-Path $tmp 'jbmono.zip'
    Invoke-WebRequest -Uri 'https://github.com/JetBrains/JetBrainsMono/releases/download/v2.304/JetBrainsMono-2.304.zip' -OutFile $jb
    Expand-Archive -Force $jb (Join-Path $tmp 'jbmono')
    Copy-Item (Join-Path $tmp 'jbmono\fonts\webfonts\JetBrainsMono-Regular.woff2') $Fonts -Force
    Copy-Item (Join-Path $tmp 'jbmono\fonts\webfonts\JetBrainsMono-Medium.woff2') $Fonts -Force
    $iz = Join-Path $tmp 'inter.zip'
    Invoke-WebRequest -Uri 'https://github.com/rsms/inter/releases/download/v4.0/Inter-4.0.zip' -OutFile $iz
    Expand-Archive -Force $iz (Join-Path $tmp 'inter')
    $interCandidates = @(
        (Join-Path $tmp 'inter\Inter Desktop\Inter-Regular.woff2'),
        (Join-Path $tmp 'inter\web\Inter-Regular.woff2'),
        (Join-Path $tmp 'inter\Inter-4.0\web\Inter-Regular.woff2')
    ) + @(Get-ChildItem -Path (Join-Path $tmp 'inter') -Recurse -Filter 'Inter-Regular.woff2' -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
    $interSrc = $interCandidates | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
    if (-not $interSrc) { throw 'Could not find Inter-Regular.woff2 in Inter zip' }
    Copy-Item $interSrc (Join-Path $Fonts 'Inter-Regular.woff2') -Force
    Remove-Item -Recurse -Force $tmp
}

# ── Angular production build ────────────────────────────────────────────────
Write-Host '==> Building Angular (production)'
Push-Location $Frontend
try {
    if (-not (Test-Path 'node_modules')) {
        Write-Host '==> npm install (node_modules missing)'
        npm install
        if ($LASTEXITCODE -ne 0) { throw 'npm install failed' }
    }
    npm run build
    if ($LASTEXITCODE -ne 0) { throw 'ng build failed' }
} finally {
    Pop-Location
}

$Dist = Join-Path $Frontend 'dist\georoute-frontend'
if (-not (Test-Path (Join-Path $Dist 'index.html'))) {
    throw "Angular dist missing: $Dist\index.html"
}

# ── Fat JAR with UI embedded ────────────────────────────────────────────────
Write-Host '==> Building Spring Boot fat JAR (-Poffline-bundle)'
Push-Location $Backend
try {
    mvn -Poffline-bundle package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw 'mvn package failed' }
} finally {
    Pop-Location
}

$JarSrc = Join-Path $Backend 'target\georoute-backend-1.0.0.jar'
if (-not (Test-Path $JarSrc)) {
    throw "JAR not found: $JarSrc"
}

# ── Assemble offline-bundle/ ────────────────────────────────────────────────
Write-Host "==> Assembling $Out"
if (Test-Path $Out) { Remove-Item -Recurse -Force $Out }
New-Item -ItemType Directory -Path $Out | Out-Null
New-Item -ItemType Directory -Path (Join-Path $Out 'data') | Out-Null
New-Item -ItemType Directory -Path (Join-Path $Out 'db') | Out-Null

Copy-Item $JarSrc (Join-Path $Out 'georoute-backend-1.0.0.jar')
Copy-Item (Join-Path $Root 'db\init.sql') (Join-Path $Out 'db\init.sql')
Copy-Item (Join-Path $Root 'data\README.md') (Join-Path $Out 'data\README.md') -ErrorAction SilentlyContinue

# Optional: copy GIS data if present (keeps recipient machine simpler)
$dataCandidates = @(
    'roads_basemap.tif',
    'rasterized.tif',
    'gis_osm_roads_free_1.shp',
    'gis_osm_roads_free_1.dbf',
    'gis_osm_roads_free_1.shx',
    'gis_osm_roads_free_1.prj'
)
$copiedData = @()
foreach ($name in $dataCandidates) {
    $src = Join-Path $Root "data\$name"
    if (Test-Path $src) {
        Write-Host "    + data\$name"
        Copy-Item $src (Join-Path $Out "data\$name")
        $copiedData += $name
    }
}

# Runtime config — paths relative to offline-bundle/ working directory.
# Password is intentionally a PLACEHOLDER (do not ship real creds).
$tif = if ($copiedData -contains 'roads_basemap.tif') {
    './data/roads_basemap.tif'
} elseif ($copiedData -contains 'rasterized.tif') {
    './data/rasterized.tif'
} else {
    './data/YOUR_BASEMAP.tif'
}
$shp = if ($copiedData -contains 'gis_osm_roads_free_1.shp') {
    './data/gis_osm_roads_free_1.shp'
} else {
    './data/YOUR_ROADS.shp'
}

@"
# GeoRoute offline runtime config (edit DB password before first run)
spring.datasource.url=jdbc:postgresql://localhost:5432/georoute
spring.datasource.username=postgres
spring.datasource.password=CHANGE_ME
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.hibernate.ddl-auto=none
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.open-in-view=false

geotiff.base.path=$tif
shapefile.base.path=$shp
shapefile.force-reload=false
shapefile.node-cache.size=300000
shapefile.attr.name=name
shapefile.attr.fclass=fclass
shapefile.attr.oneway=oneway

spring.jdbc.template.query-timeout=30
tiles.cache.dir=./tile-cache
tiles.cache.enabled=true
routing.max.speed.kmh=110
server.port=8080
cors.allowed-origins=http://localhost:8080,http://127.0.0.1:8080
"@ | Set-Content -Encoding UTF8 (Join-Path $Out 'application.properties')

Copy-Item (Join-Path $PSScriptRoot 'run-offline.bat') (Join-Path $Out 'run-offline.bat')
Copy-Item (Join-Path $PSScriptRoot 'run-offline.ps1') (Join-Path $Out 'run-offline.ps1')
Copy-Item (Join-Path $PSScriptRoot 'OFFLINE_README.md') (Join-Path $Out 'README.md')

Write-Host ''
Write-Host '==> Done.'
Write-Host "    Folder: $Out"
Write-Host '    Zip that folder and ship it. Recipient needs JDK 17 + PostgreSQL/PostGIS only.'
Write-Host '    Open http://localhost:8080 after run-offline.bat'
