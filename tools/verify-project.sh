#!/usr/bin/env bash
set -euo pipefail
mvn --batch-mode --no-transfer-progress clean verify
jar tf target/WMOrder-1.0.0.jar | grep -q '^plugin.yml$'
jar tf target/WMOrder-1.0.0.jar | grep -q '^db/migration/sqlite/V1__initial.sql$'
jar tf target/WMOrder-1.0.0.jar | grep -q '^db/migration/mysql/V1__initial.sql$'
echo "WMOrder verification completed successfully."
