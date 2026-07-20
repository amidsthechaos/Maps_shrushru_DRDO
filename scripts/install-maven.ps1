# Downloads Apache Maven and adds to PATH.
# Default: installs to %LOCALAPPDATA%\Programs\apache-maven (no admin required).
# Pass -MachineInstall to install under Program Files (requires elevated PowerShell).
param(
    [string]$Version = '3.9.16',
    [switch]$MachineInstall
)

$ErrorActionPreference = 'Stop'

$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
    ).IsInRole([Security.Principal.WindowsBuiltinRole]::Administrator)

if ($MachineInstall -and -not $isAdmin) {
    Write-Error 'MachineInstall requires an elevated PowerShell (Run as Administrator).'
}

$InstallRoot = if ($MachineInstall) {
    'C:\Program Files\Apache\maven'
} else {
    Join-Path $env:LOCALAPPDATA 'Programs\apache-maven'
}

$zipName = "apache-maven-$Version-bin.zip"
$url = "https://dlcdn.apache.org/maven/maven-3/$Version/binaries/$zipName"
$tmp = Join-Path $env:TEMP $zipName
$dest = Join-Path $InstallRoot "apache-maven-$Version"

Write-Host "==> Downloading Maven $Version..."
Invoke-WebRequest -Uri $url -OutFile $tmp -UseBasicParsing

Write-Host "==> Extracting to $dest..."
if (-not (Test-Path $InstallRoot)) {
    New-Item -ItemType Directory -Path $InstallRoot -Force | Out-Null
}
if (Test-Path $dest) {
    Remove-Item $dest -Recurse -Force
}
Expand-Archive -Path $tmp -DestinationPath $InstallRoot -Force
Remove-Item $tmp -Force

$mvnBin = Join-Path $dest 'bin'
if (-not (Test-Path (Join-Path $mvnBin 'mvn.cmd'))) {
    Write-Error "mvn.cmd not found after extract in $mvnBin"
}

# Add Maven bin to PATH
$scope = if ($MachineInstall) { 'Machine' } else { 'User' }
$current = [Environment]::GetEnvironmentVariable('Path', $scope)
$parts = $current -split ';' | Where-Object { $_ -and $_.Trim() -and $_ -notmatch 'apache-maven' }
if ($parts -notcontains $mvnBin) {
    $parts += $mvnBin
    [Environment]::SetEnvironmentVariable('Path', ($parts -join ';'), $scope)
}
Write-Host "==> Added to PATH ($scope): $mvnBin"

[Environment]::SetEnvironmentVariable('MAVEN_HOME', $dest, $scope)
Write-Host "==> MAVEN_HOME = $dest"
Write-Host "==> Maven installed. Open a NEW terminal and run: mvn -version"
