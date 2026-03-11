#!/usr/bin/env bash
set -euo pipefail

echo "[chaos] Starting test dependencies..."
docker compose -f test-environment/docker-compose.yml up -d
trap 'docker compose -f test-environment/docker-compose.yml down -v' EXIT

echo "[chaos] Running resilience and chaos tests..."
mvn -B -ntp \
  -Dtest='*ChaosTest' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test

echo "[chaos] Done."
