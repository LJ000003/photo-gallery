#!/bin/bash
# Build script: Docker Compose deployment
# Output: Docker image + running containers
set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"
FRONTEND="$ROOT/frontend"
BACKEND="$ROOT/backend"

echo "=== 1/4 构建前端 ==="
cd "$FRONTEND"
if [ ! -d "node_modules" ]; then
  echo "  → 安装依赖..."
  npm ci
fi
npm run build

echo ""
echo "=== 2/4 复制前端到后端静态目录 ==="
rm -rf "$BACKEND/src/main/resources/static"/*
cp -r "$FRONTEND/dist"/* "$BACKEND/src/main/resources/static/"

echo ""
echo "=== 3/4 构建后端 JAR ==="
cd "$BACKEND"
mvn clean package -DskipTests

echo ""
echo "=== 4/4 Docker Compose 构建 + 启动 ==="
cd "$ROOT"
docker compose up -d --build

echo ""
echo "✓ 部署完成"
echo "  访问: http://localhost"
echo "  状态: docker compose ps"
echo "  日志: docker compose logs -f app"
echo "  停止: docker compose down"
