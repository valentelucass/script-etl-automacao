#!/usr/bin/env bash
set -euo pipefail

echo "[integration] Starting ephemeral dependencies..."
docker compose -f test-environment/docker-compose.yml up -d
trap 'docker compose -f test-environment/docker-compose.yml down -v' EXIT

echo "[integration] Running integration tests..."
mvn -B -ntp \
  -Dtest='*IT,*IntegrationTest' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test

echo "[integration] Done."
