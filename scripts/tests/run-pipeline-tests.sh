#!/usr/bin/env bash
set -euo pipefail

echo "[pipeline] Running contract, quality and pipeline tests..."
mvn -B -ntp \
  -Dtest='*ContractTest,*DataQualityServiceTest,*PipelineOrchestratorTest,*PipelineE2ETest,*SnapshotTest' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test

echo "[pipeline] Done."
