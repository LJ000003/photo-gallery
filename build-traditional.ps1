# Build script: traditional deployment (Nginx + JAR)
# Output: backend/target/demo-backend-*.jar (with frontend embedded)

chcp 65001 > $null

Write-Host "===[1/3] Building frontend ===" -ForegroundColor Cyan
Set-Location "$PSScriptRoot\frontend"
npm run build
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ""
Write-Host "===[2/3] Copying frontend to backend static ===" -ForegroundColor Cyan
Set-Location "$PSScriptRoot"
Remove-Item -Recurse -Force "backend\src\main\resources\static\*" -ErrorAction SilentlyContinue
Copy-Item -Recurse -Force "frontend\dist\*" "backend\src\main\resources\static\"

Write-Host ""
Write-Host "===[3/3] Building backend JAR ===" -ForegroundColor Cyan
Set-Location "$PSScriptRoot\backend"
mvn clean package -DskipTests
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$jar = Get-ChildItem "target\demo-backend-*.jar" | Select-Object -First 1
Write-Host ""
Write-Host "Done!" -ForegroundColor Green
Write-Host "  JAR: backend\$($jar.Name)" -ForegroundColor Green
Write-Host "  Frontend: frontend\dist\" -ForegroundColor Green
