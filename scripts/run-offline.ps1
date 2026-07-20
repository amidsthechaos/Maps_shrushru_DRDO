# Launch GeoRoute from an offline-bundle/ folder (or any folder containing the JAR + application.properties).
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw 'java not found. Install JDK 17 and add it to PATH.'
}
if (-not (Test-Path '.\georoute-backend-1.0.0.jar')) {
    throw 'georoute-backend-1.0.0.jar missing.'
}

Write-Host '==> Starting GeoRoute on http://localhost:8080'
& java -Xmx2g -jar '.\georoute-backend-1.0.0.jar' '--spring.config.additional-location=optional:file:./'
