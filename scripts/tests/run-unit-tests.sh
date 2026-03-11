#!/usr/bin/env bash
set -euo pipefail

echo "[unit] Running unit tests..."
mvn -B -ntp \
  -Dtest='*Test,!*IT,!*IntegrationTest,!*ContractTest,!*PipelineE2ETest,!*ChaosTest' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test

echo "[unit] Done."
