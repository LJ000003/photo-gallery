#!/bin/bash
set -e

echo "=== 1/3 构建前端 ==="
cd "$(dirname "$0")/frontend"
export VITE_ADMIN_PASSWORD="${ADMIN_PASSWORD:-photoadmin}"
npm run build

echo ""
echo "=== 2/3 复制前端到后端静态目录 ==="
cd "$(dirname "$0")"
rm -rf backend/src/main/resources/static/*
cp -r frontend/dist/* backend/src/main/resources/static/

echo ""
echo "=== 3/3 构建后端 JAR ==="
cd "$(dirname "$0")/backend"
mvn clean package -DskipTests

JAR=$(ls target/photo-gallery-*.jar | head -1)
echo ""
echo "✓ 构建完成"
echo "  JAR: backend/$JAR"
echo "  前端: frontend/dist/"
