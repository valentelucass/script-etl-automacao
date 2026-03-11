#!/usr/bin/env bash
set -euo pipefail

./run-unit-tests.sh
./run-integration-tests.sh
./run-pipeline-tests.sh
./run-chaos-tests.sh
