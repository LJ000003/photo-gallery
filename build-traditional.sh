#!/bin/bash
# Build script: traditional deployment (Nginx + JAR)
# Output: backend/target/photo-gallery-*.jar (with frontend embedded)
set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"
FRONTEND="$ROOT/frontend"
BACKEND="$ROOT/backend"

echo "=== 1/3 构建前端 ==="
cd "$FRONTEND"
if [ ! -d "node_modules" ]; then
  echo "  → 安装依赖..."
  npm ci
fi
export VITE_ADMIN_PASSWORD="${ADMIN_PASSWORD:-photoadmin}"
npm run build

echo ""
echo "=== 2/3 复制前端到后端静态目录 ==="
rm -rf "$BACKEND/src/main/resources/static"/*
cp -r "$FRONTEND/dist"/* "$BACKEND/src/main/resources/static/"

echo ""
echo "=== 3/3 构建后端 JAR ==="
cd "$BACKEND"
mvn clean package -DskipTests

JAR=$(ls target/photo-gallery-*.jar | head -1)
echo ""
echo "✓ 构建完成"
echo "  JAR: backend/$JAR"
echo "  前端: $FRONTEND/dist/"
