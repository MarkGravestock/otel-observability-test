#!/usr/bin/env bash
# start.sh — Start the full otel-observability-test stack
# Usage: ./scripts/start.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$ROOT_DIR"

echo "Starting infrastructure (docker-compose)..."
docker-compose up -d

echo "Building application..."
./gradlew classes -q

echo "Starting services (output → logs/)..."
mkdir -p logs

./gradlew runGreeting      > logs/greeting.log      2>&1 &
GREETING_PID=$!

./gradlew runSalutation   > logs/salutation.log    2>&1 &
SALUTATION_PID=$!

./gradlew runVisitor       > logs/visitor.log       2>&1 &
VISITOR_PID=$!

./gradlew runTimeProvider > logs/time-provider.log 2>&1 &
TIME_PROVIDER_PID=$!

echo ""
echo "Services started:"
echo "  Greeting      (8080) PID=$GREETING_PID       → logs/greeting.log"
echo "  Salutation    (8081) PID=$SALUTATION_PID     → logs/salutation.log"
echo "  Visitor       (8082) PID=$VISITOR_PID        → logs/visitor.log"
echo "  TimeProvider  (8083) PID=$TIME_PROVIDER_PID  → logs/time-provider.log"
echo ""
echo "Grafana UI: http://localhost:3000 (admin/admin)"
echo "Test:       curl 'http://localhost:8080/greeting?name=World'"
echo ""
echo "Press Ctrl+C to stop all services."

# Wait for all background jobs; on Ctrl+C kill the whole process group
trap 'echo ""; echo "Stopping services..."; kill $GREETING_PID $SALUTATION_PID $VISITOR_PID $TIME_PROVIDER_PID 2>/dev/null; exit 0' INT TERM

wait $GREETING_PID $SALUTATION_PID $VISITOR_PID $TIME_PROVIDER_PID