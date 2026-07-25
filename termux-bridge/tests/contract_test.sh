#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

validate_schema() {
  local schema="$1" document="$2"
  if command -v check-jsonschema >/dev/null 2>&1; then
    check-jsonschema --schemafile "$schema" "$document"
  else
    python3 - "$schema" "$document" <<'PY'
import json, sys
from jsonschema import Draft202012Validator
with open(sys.argv[1], encoding='utf-8') as f: schema=json.load(f)
with open(sys.argv[2], encoding='utf-8') as f: document=json.load(f)
Draft202012Validator(schema).validate(document)
PY
  fi
}

output="$(bash termux-bridge/scripts/dispatch.sh UNKNOWN op-test || true)"
printf '%s' "$output" | jq -e '.schemaVersion == 2 and .success == false and .exitCode == 64' >/dev/null
printf '%s' "$output" > "$TMP/response.json"
validate_schema termux-bridge/schemas/response.schema.json "$TMP/response.json"

invalid="$(bash termux-bridge/scripts/dispatch.sh HEALTH_CHECK '"bad' || true)"
printf '%s' "$invalid" | jq -e '.success == false and .exitCode == 65' >/dev/null

printf '%s\n' '{"schemaVersion":2,"operationId":"op-test","action":"HEALTH_CHECK","parameters":{}}' > "$TMP/request.json"
validate_schema termux-bridge/schemas/request.schema.json "$TMP/request.json"

health="$(HOME="$TMP/home" PREFIX=/data/data/com.termux/files/usr bash termux-bridge/scripts/dispatch.sh HEALTH_CHECK op-health)"
printf '%s' "$health" | jq -e '.success == true and .progress == 100 and .data.bridgeVersion == "2"' >/dev/null

mkdir -p "$TMP/home/.mago/metasploit-framework/.git"
touch "$TMP/home/.mago/metasploit-framework/msfconsole"
first="$(HOME="$TMP/home" bash termux-bridge/scripts/dispatch.sh CONFIGURE_RPC op-rpc-1)"
second="$(HOME="$TMP/home" bash termux-bridge/scripts/dispatch.sh CONFIGURE_RPC op-rpc-2)"
first_pass="$(printf '%s' "$first" | jq -r '.data.rpcPassword')"
second_pass="$(printf '%s' "$second" | jq -r '.data.rpcPassword')"
[[ "$first_pass" =~ ^[0-9a-f]{64}$ ]]
[[ "$first_pass" == "$second_pass" ]]
printf '%s' "$first" | jq -e '.data.credentialsCreated == "true"' >/dev/null
printf '%s' "$second" | jq -e '.data.credentialsCreated == "false"' >/dev/null
[[ "$(stat -c '%a' "$TMP/home/.mago/config/rpc.env")" == "600" ]]

if grep -R -E '\b(eval|bash -c|sh -c)\b' termux-bridge/scripts; then
  echo "unsafe shell evaluation found" >&2
  exit 1
fi

echo "Bridge v2 contract tests passed"
