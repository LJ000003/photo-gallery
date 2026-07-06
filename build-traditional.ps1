# Build script: traditional deployment (Nginx + JAR)
# Output: backend/target/photo-gallery-*.jar (with frontend embedded)

$ErrorActionPreference = "Stop"
$ROOT = $PSScriptRoot
$FRONTEND = Join-Path $ROOT "frontend"
$BACKEND = Join-Path $ROOT "backend"

Write-Host "===[1/3] Building frontend ===" -ForegroundColor Cyan
Set-Location $FRONTEND
if (-not (Test-Path "node_modules")) {
  Write-Host "  → Installing dependencies..." -ForegroundColor Yellow
  npm ci
  if ($LASTEXITCODE -ne 0) { throw "npm ci failed" }
}
$env:VITE_ADMIN_PASSWORD = if ($env:ADMIN_PASSWORD) { $env:ADMIN_PASSWORD } else { "photoadmin" }
npm run build
if ($LASTEXITCODE -ne 0) { throw "Frontend build failed" }

Write-Host ""
Write-Host "===[2/3] Copying frontend to backend static ===" -ForegroundColor Cyan
Set-Location $ROOT
Remove-Item -Recurse -Force "$BACKEND\src\main\resources\static\*" -ErrorAction SilentlyContinue
Copy-Item -Recurse -Force "$FRONTEND\dist\*" "$BACKEND\src\main\resources\static\"

Write-Host ""
Write-Host "===[3/3] Building backend JAR ===" -ForegroundColor Cyan
Set-Location $BACKEND
mvn clean package -DskipTests
if ($LASTEXITCODE -ne 0) { throw "Backend build failed" }

$jar = Get-ChildItem "target\photo-gallery-*.jar" | Select-Object -First 1
Write-Host ""
Write-Host "Done!" -ForegroundColor Green
Write-Host "  JAR: backend\$($jar.Name)" -ForegroundColor Green
Write-Host "  Frontend: $FRONTEND\dist\" -ForegroundColor Green
