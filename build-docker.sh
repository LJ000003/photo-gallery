#!/bin/bash
set -e

echo "=== 1/4 构建前端 ==="
cd "$(dirname "$0")/frontend"
export VITE_ADMIN_PASSWORD="${ADMIN_PASSWORD:-photoadmin}"
npm run build

echo ""
echo "=== 2/4 复制前端到后端静态目录 ==="
cd "$(dirname "$0")"
rm -rf backend/src/main/resources/static/*
cp -r frontend/dist/* backend/src/main/resources/static/

echo ""
echo "=== 3/4 构建后端 JAR ==="
cd "$(dirname "$0")/backend"
mvn clean package -DskipTests

echo ""
echo "=== 4/4 Docker Compose 构建 + 启动 ==="
cd "$(dirname "$0")"
docker compose up -d --build

echo ""
echo "✓ 部署完成"
echo "  访问: http://localhost"
echo "  状态: docker compose ps"
echo "  日志: docker compose logs -f app"
echo "  停止: docker compose down"
