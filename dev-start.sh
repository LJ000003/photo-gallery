#!/usr/bin/env bash
# Photo Gallery - One-click dev startup
# Backend: Spring Boot (dev profile, port 8080)
# Frontend: Vite dev server (port 5173)
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKEND="$SCRIPT_DIR/backend"
FRONTEND="$SCRIPT_DIR/frontend"

echo -e "\033[36m========================================"
echo "  Photo Gallery - Dev Mode"
echo "  Backend :8080  |  Frontend :5173"
echo -e "========================================\033[0m"
echo ""

# --- Prerequisites ---
echo -e "\033[90m--- Checking prerequisites ---\033[0m"

if [ ! -f "$BACKEND/pom.xml" ]; then
    echo -e "\033[31m[FAIL] backend/pom.xml not found - run from project root\033[0m"
    exit 1
fi
if [ ! -f "$FRONTEND/package.json" ]; then
    echo -e "\033[31m[FAIL] frontend/package.json not found\033[0m"
    exit 1
fi
if [ ! -d "$FRONTEND/node_modules" ]; then
    echo -e "\033[33m  -> Installing frontend dependencies...\033[0m"
    (cd "$FRONTEND" && npm install)
fi

echo -e "\033[32m[OK] Prerequisites passed\033[0m"
echo ""

# --- Start backend ---
echo -e "\033[90m--- Starting backend (mvn spring-boot:run, port 8080) ---\033[0m"
(cd "$BACKEND" && mvn spring-boot:run 2>&1 | sed 's/^/[backend] /') &
BACKEND_PID=$!

echo -e "\033[33m  Waiting for backend to be ready...\033[0m"
for i in $(seq 1 60); do
    sleep 2
    if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo -e "\033[32m[OK] Backend ready ($((i*2))s)\033[0m"
        break
    fi
    if [ $((i % 5)) -eq 0 ]; then
        echo -e "\033[90m  Waiting... ($((i*2))s / 120s)\033[0m"
    fi
done

echo ""

# --- Cleanup: kill JVM on port 8080 ---
cleanup() {
    echo ""
    echo -e "\033[33mStopping backend...\033[0m"
    # Kill the mvn + java process tree
    if [ -n "$BACKEND_PID" ] && kill -0 "$BACKEND_PID" 2>/dev/null; then
        kill "$BACKEND_PID" 2>/dev/null
        wait "$BACKEND_PID" 2>/dev/null
    fi
    # Safety net: kill whatever is holding port 8080
    if command -v fuser >/dev/null 2>&1; then
        fuser -k 8080/tcp 2>/dev/null || true
    elif command -v lsof >/dev/null 2>&1; then
        JAVA_PID=$(lsof -ti:8080 2>/dev/null)
        [ -n "$JAVA_PID" ] && kill "$JAVA_PID" 2>/dev/null || true
    fi
    echo -e "\033[32mAll services stopped\033[0m"
}
trap cleanup EXIT INT TERM

# --- Start frontend ---
echo -e "\033[90m--- Starting frontend (npm run dev, port 5173) ---\033[0m"
echo -e "\033[90m  Press Ctrl+C to stop all services\033[0m"
echo ""

cd "$FRONTEND" && npm run dev
