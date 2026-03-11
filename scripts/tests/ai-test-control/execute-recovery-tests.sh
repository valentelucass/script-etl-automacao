#!/usr/bin/env bash
set -euo pipefail

echo "[ai] Running recovery-focused tests..."
mvn -B -ntp \
  -Dtest='*Recovery*Test,*PipelineOrchestratorTest,*DataQualityServiceTest' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test
