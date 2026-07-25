#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

validate_schema() {
  local schema="$1"
  local document="$2"
  if command -v check-jsonschema >/dev/null 2>&1; then
    check-jsonschema --schemafile "$schema" "$document"
  else
    python3 - "$schema" "$document" <<'PY'
import json, sys
from jsonschema import Draft202012Validator
schema_path, document_path = sys.argv[1:]
with open(schema_path, encoding="utf-8") as f:
    schema = json.load(f)
with open(document_path, encoding="utf-8") as f:
    document = json.load(f)
Draft202012Validator(schema).validate(document)
PY
  fi
}

known_unsupported="$(bash termux-bridge/scripts/dispatch.sh START_RPC op-start || true)"
printf '%s' "$known_unsupported" | jq -e '.success == false and .exitCode == 64 and .action == "START_RPC"' >/dev/null

output="$(bash termux-bridge/scripts/dispatch.sh UNKNOWN op-test || true)"
printf '%s' "$output" | jq -e '.success == false and .exitCode == 64' >/dev/null
printf '%s' "$output" > /tmp/mago-bridge-response.json
validate_schema termux-bridge/schemas/response.schema.json /tmp/mago-bridge-response.json

invalid="$(bash termux-bridge/scripts/dispatch.sh HEALTH_CHECK '"bad' || true)"
printf '%s' "$invalid" | jq -e '.success == false and .exitCode == 65' >/dev/null

printf '%s\n' '{"schemaVersion":1,"operationId":"op-test","action":"HEALTH_CHECK","parameters":{}}' \
  > /tmp/mago-bridge-request.json
validate_schema termux-bridge/schemas/request.schema.json /tmp/mago-bridge-request.json

health="$(PREFIX=/data/data/com.termux/files/usr bash termux-bridge/scripts/dispatch.sh HEALTH_CHECK op-health)"
printf '%s' "$health" | jq -e '.success == true and .progress == 100 and .action == "HEALTH_CHECK"' >/dev/null

if grep -R -E '\b(eval|bash -c|sh -c)\b' termux-bridge/scripts; then
  echo "unsafe shell evaluation found" >&2
  exit 1
fi
