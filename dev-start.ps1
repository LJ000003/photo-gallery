# Photo Gallery - One-click dev startup
# Backend: Spring Boot (dev profile, port 8080)
# Frontend: Vite dev server (port 5173)

$ErrorActionPreference = "Stop"
$ROOT = $PSScriptRoot
$BACKEND = Join-Path $ROOT "backend"
$FRONTEND = Join-Path $ROOT "frontend"
$ORIGIN = Get-Location

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Photo Gallery - Dev Mode" -ForegroundColor Cyan
Write-Host "  Backend :8080  |  Frontend :5173" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# --- Prerequisites ---
Write-Host "--- Checking prerequisites ---" -ForegroundColor DarkGray

if (-not (Test-Path "$BACKEND\pom.xml")) {
    Write-Host "[FAIL] backend/pom.xml not found - run from project root" -ForegroundColor Red
    exit 1
}
if (-not (Test-Path "$FRONTEND\package.json")) {
    Write-Host "[FAIL] frontend/package.json not found" -ForegroundColor Red
    exit 1
}
if (-not (Test-Path "$FRONTEND\node_modules")) {
    Write-Host "  -> Installing frontend dependencies..." -ForegroundColor Yellow
    Set-Location $FRONTEND
    npm install
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[FAIL] npm install failed" -ForegroundColor Red
        exit 1
    }
    Set-Location $ROOT
}

Write-Host "[OK] Prerequisites passed" -ForegroundColor Green
Write-Host ""

# --- Start backend ---
Write-Host "--- Starting backend (mvn spring-boot:run, port 8080) ---" -ForegroundColor DarkGray

$backendJob = Start-Job -Name "photo-backend" -ScriptBlock {
    param($dir)
    Set-Location $dir
    mvn spring-boot:run 2>&1
} -ArgumentList $BACKEND

Write-Host "  Waiting for backend to be ready..." -ForegroundColor Yellow
Write-Host ""

$maxWait = 120
$elapsed = 0
$ready = $false
do {
    Start-Sleep -Seconds 2
    $elapsed += 2
    # Print backend output while waiting (shows Spring Boot banner + startup logs)
    Receive-Job -Name "photo-backend" 2>&1 | ForEach-Object { Write-Host $_ }
    try {
        $null = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -TimeoutSec 2 -UseBasicParsing
        Write-Host "[OK] Backend ready (${elapsed}s)" -ForegroundColor Green
        $ready = $true
        break
    }
    catch {}
    if ($elapsed % 10 -eq 0) {
        Write-Host "  Waiting... (${elapsed}s / ${maxWait}s)" -ForegroundColor DarkGray
    }
} while ($elapsed -lt $maxWait)

if (-not $ready) {
    Write-Host "[WARN] Backend startup timed out, check backend logs" -ForegroundColor Yellow
    Write-Host "       Starting frontend anyway (API proxy may be unavailable)" -ForegroundColor Yellow
}

# Clear any remaining buffered output
Receive-Job -Name "photo-backend" 2>&1 | ForEach-Object { Write-Host $_ }

Write-Host ""

# --- Cleanup function ---
function Stop-Backend {
    Write-Host ""
    Write-Host "Stopping backend..." -ForegroundColor Yellow

    Stop-Job -Name "photo-backend" -ErrorAction SilentlyContinue
    Remove-Job -Name "photo-backend" -ErrorAction SilentlyContinue

    # Safety net: kill any java process on port 8080
    $portProc = Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($portProc) {
        Stop-Process -Id $portProc.OwningProcess -Force -ErrorAction SilentlyContinue
    }

    Set-Location $ORIGIN
    Write-Host "All services stopped" -ForegroundColor Green
}

# --- Start frontend ---
Write-Host "--- Starting frontend (npm run dev, port 5173) ---" -ForegroundColor DarkGray
Write-Host "  Press Ctrl+C to stop all services" -ForegroundColor DarkGray
Write-Host ""

Set-Location $FRONTEND

try {
    npm run dev
}
finally {
    Stop-Backend
}
