# Build script: Docker Compose deployment
# Output: Docker image + running containers

chcp 65001 > $null

Write-Host "===[1/4] Building frontend ===" -ForegroundColor Cyan
Set-Location "$PSScriptRoot\frontend"
if ($env:ADMIN_PASSWORD) { $env:VITE_ADMIN_PASSWORD = $env:ADMIN_PASSWORD }
npm run build
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ""
Write-Host "===[2/4] Copying frontend to backend static ===" -ForegroundColor Cyan
Set-Location "$PSScriptRoot"
Remove-Item -Recurse -Force "backend\src\main\resources\static\*" -ErrorAction SilentlyContinue
Copy-Item -Recurse -Force "frontend\dist\*" "backend\src\main\resources\static\"

Write-Host ""
Write-Host "===[3/4] Building backend JAR ===" -ForegroundColor Cyan
Set-Location "$PSScriptRoot\backend"
mvn clean package -DskipTests
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ""
Write-Host "===[4/4] Docker Compose build + start ===" -ForegroundColor Cyan
Set-Location "$PSScriptRoot"
docker compose up -d --build

Write-Host ""
Write-Host "Done!" -ForegroundColor Green
Write-Host "  URL: http://localhost:8080" -ForegroundColor Green
Write-Host "  Status: docker compose ps" -ForegroundColor Green
Write-Host "  Logs: docker compose logs -f app" -ForegroundColor Green
Write-Host "  Stop: docker compose down" -ForegroundColor Green
