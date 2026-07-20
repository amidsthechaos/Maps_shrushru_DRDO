# Sets machine JAVA_HOME to JDK 17 and adds JDK + Maven to PATH (if found).
$ErrorActionPreference = 'Stop'

function Add-ToMachinePath([string]$dir) {
    if (-not (Test-Path $dir)) { return $false }
    $machine = [Environment]::GetEnvironmentVariable('Path', 'Machine')
    $parts = $machine -split ';' | Where-Object { $_ -and $_.Trim() }
    if ($parts -notcontains $dir) {
        [Environment]::SetEnvironmentVariable('Path', ($parts + $dir) -join ';', 'Machine')
        Write-Host "Added to PATH: $dir"
    }
    return $true
}

# JDK 17 — Eclipse Temurin
$adoptium = 'C:\Program Files\Eclipse Adoptium'
if (Test-Path $adoptium) {
    $jdk17 = Get-ChildItem $adoptium -Directory -Filter 'jdk-17*' |
        Sort-Object Name -Descending | Select-Object -First 1
    if ($jdk17) {
        [Environment]::SetEnvironmentVariable('JAVA_HOME', $jdk17.FullName, 'Machine')
        Write-Host "JAVA_HOME = $($jdk17.FullName)"
        Add-ToMachinePath (Join-Path $jdk17.FullName 'bin') | Out-Null
    }
}

# Maven — common install locations after winget/choco
$mavenCandidates = @(
    'C:\Program Files\Apache\Maven\apache-maven-*\bin',
    'C:\ProgramData\chocolatey\lib\maven\apache-maven-*\bin',
    'C:\tools\apache-maven-*\bin'
)
foreach ($pattern in $mavenCandidates) {
    $parent = Split-Path $pattern -Parent
    if (-not (Test-Path $parent)) { continue }
    $resolved = Resolve-Path $pattern -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($resolved) {
        Add-ToMachinePath $resolved.Path | Out-Null
        break
    }
}

# Also try where.exe mvn after a refresh (best-effort)
$env:Path = [Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' +
            [Environment]::GetEnvironmentVariable('Path', 'User')
