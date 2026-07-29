param(
    [switch]$NoRestart
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$envFile = Join-Path $projectRoot '.env'

$candidate = Get-NetIPAddress -AddressFamily IPv4 |
    Where-Object {
        $_.IPAddress -notlike '127.*' -and
        $_.IPAddress -notlike '169.254.*' -and
        $_.InterfaceAlias -notmatch 'vEthernet|Docker|WSL|Loopback' -and
        $_.AddressState -eq 'Preferred'
    } |
    Sort-Object -Property InterfaceMetric |
    Select-Object -First 1

if (-not $candidate) {
    throw 'No usable LAN IPv4 address was found. Connect Wi-Fi or Ethernet first.'
}

$baseUrl = "http://$($candidate.IPAddress):8080/h5/index.html"
$line = "DINEHUB_H5_BASE_URL=$baseUrl"
$content = if (Test-Path -LiteralPath $envFile) {
    @(Get-Content -LiteralPath $envFile)
} else {
    @()
}

$matched = $false
$updated = foreach ($item in $content) {
    if ($item -match '^\s*DINEHUB_H5_BASE_URL=') {
        $matched = $true
        $line
    } else {
        $item
    }
}
if (-not $matched) {
    $updated += $line
}

Set-Content -LiteralPath $envFile -Value $updated -Encoding utf8
Write-Host "Mobile ordering URL updated to: $baseUrl" -ForegroundColor Green
Write-Host "On the same Wi-Fi, test: http://$($candidate.IPAddress):8080/actuator/health"

if (-not $NoRestart) {
    Push-Location $projectRoot
    try {
        docker compose up -d --force-recreate app
        docker compose ps
    } finally {
        Pop-Location
    }
}
