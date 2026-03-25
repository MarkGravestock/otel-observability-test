# start.ps1 — Start the full otel-observability-test stack
# Usage: .\scripts\start.ps1

$ErrorActionPreference = "Stop"

$RootDir = Split-Path -Parent $PSScriptRoot

Push-Location $RootDir

try {
    Write-Host "Starting infrastructure (docker-compose)..."
    docker-compose up -d

    Write-Host "Building application..."
    & .\gradlew.bat classes -q

    $LogDir = Join-Path $RootDir "logs"
    New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

    Write-Host "Starting services in separate windows (output -> logs\)..."

    $services = @(
        @{ Task = "runGreeting";   Log = "greeting.log";   Port = 8080 },
        @{ Task = "runSalutation"; Log = "salutation.log"; Port = 8081 },
        @{ Task = "runVisitor";    Log = "visitor.log";    Port = 8082 }
    )

    $processes = @()
    foreach ($svc in $services) {
        $logPath = Join-Path $LogDir $svc.Log
        $proc = Start-Process -FilePath ".\gradlew.bat" `
            -ArgumentList $svc.Task `
            -WorkingDirectory $RootDir `
            -RedirectStandardOutput $logPath `
            -RedirectStandardError "$logPath.err" `
            -PassThru `
            -NoNewWindow
        $processes += $proc
        Write-Host ("  {0,-12} (:{1}) PID={2,-6} -> logs\{3}" -f $svc.Task.Replace("run",""), $svc.Port, $proc.Id, $svc.Log)
    }

    Write-Host ""
    Write-Host "Grafana UI: http://localhost:3000 (admin/admin)"
    Write-Host "Test:       curl 'http://localhost:8080/greeting?name=World'"
    Write-Host ""
    Write-Host "Press Ctrl+C to stop all services."

    try {
        Wait-Process -Id ($processes | ForEach-Object { $_.Id })
    } finally {
        Write-Host ""
        Write-Host "Stopping services..."
        foreach ($proc in $processes) {
            if (-not $proc.HasExited) {
                Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
            }
        }
    }
} finally {
    Pop-Location
}