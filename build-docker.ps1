# Build script: Docker Compose deployment
# Output: Docker image + running containers

$ErrorActionPreference = "Stop"
$ROOT = $PSScriptRoot
$FRONTEND = Join-Path $ROOT "frontend"
$BACKEND = Join-Path $ROOT "backend"

Write-Host "===[1/4] Building frontend ===" -ForegroundColor Cyan
Set-Location $FRONTEND
if (-not (Test-Path "node_modules")) {
  Write-Host "  → Installing dependencies..." -ForegroundColor Yellow
  npm ci
  if ($LASTEXITCODE -ne 0) { throw "npm ci failed" }
}
npm run build
if ($LASTEXITCODE -ne 0) { throw "Frontend build failed" }

Write-Host ""
Write-Host "===[2/4] Copying frontend to backend static ===" -ForegroundColor Cyan
Set-Location $ROOT
Remove-Item -Recurse -Force "$BACKEND\src\main\resources\static\*" -ErrorAction SilentlyContinue
Copy-Item -Recurse -Force "$FRONTEND\dist\*" "$BACKEND\src\main\resources\static\"

Write-Host ""
Write-Host "===[3/4] Building backend JAR ===" -ForegroundColor Cyan
Set-Location $BACKEND
mvn clean package -DskipTests
if ($LASTEXITCODE -ne 0) { throw "Backend build failed" }

Write-Host ""
Write-Host "===[4/4] Docker Compose build + start ===" -ForegroundColor Cyan
Set-Location $ROOT
docker compose up -d --build

Write-Host ""
Write-Host "Done!" -ForegroundColor Green
Write-Host "  URL: http://localhost" -ForegroundColor Green
Write-Host "  Status: docker compose ps" -ForegroundColor Green
Write-Host "  Logs: docker compose logs -f app" -ForegroundColor Green
Write-Host "  Stop: docker compose down" -ForegroundColor Green
