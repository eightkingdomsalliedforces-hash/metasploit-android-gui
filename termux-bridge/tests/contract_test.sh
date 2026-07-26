#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"; cd "$ROOT"
validate_schema() {
  local schema="$1" document="$2"
  if command -v check-jsonschema >/dev/null 2>&1; then check-jsonschema --schemafile "$schema" "$document"
  else python3 - "$schema" "$document" <<'PY'
import json, sys
from jsonschema import Draft202012Validator
with open(sys.argv[1], encoding='utf-8') as f: schema=json.load(f)
with open(sys.argv[2], encoding='utf-8') as f: document=json.load(f)
Draft202012Validator(schema).validate(document)
PY
  fi
}

unknown="$(bash termux-bridge/scripts/dispatch.sh UNKNOWN op-test || true)"
printf '%s' "$unknown" | jq -e '.success == false and .exitCode == 64' >/dev/null
printf '%s' "$unknown" > /tmp/mago-bridge-response.json
validate_schema termux-bridge/schemas/response.schema.json /tmp/mago-bridge-response.json

invalid="$(bash termux-bridge/scripts/dispatch.sh HEALTH_CHECK '"bad' || true)"
printf '%s' "$invalid" | jq -e '.success == false and .exitCode == 65' >/dev/null

printf '%s\n' '{"schemaVersion":1,"operationId":"op-test","action":"CONFIGURE_RPC","parameters":{}}' > /tmp/mago-bridge-request.json
validate_schema termux-bridge/schemas/request.schema.json /tmp/mago-bridge-request.json

TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
export HOME="$TMP/home" PREFIX="$TMP/prefix" MAGO_HOME="$TMP/home/.mago" MAGO_FAKE_LOG="$TMP/commands.log"
mkdir -p "$HOME" "$PREFIX"; : > "$MAGO_FAKE_LOG"
health="$(bash termux-bridge/scripts/dispatch.sh HEALTH_CHECK op-health)"
printf '%s' "$health" | jq -e '.success == true and .data.bridgeVersion == "2" and .data.rpcPortOpen == "false"' >/dev/null

first="$(bash termux-bridge/scripts/dispatch.sh CONFIGURE_RPC op-rpc-1)"
second="$(bash termux-bridge/scripts/dispatch.sh CONFIGURE_RPC op-rpc-2)"
first_password="$(printf '%s' "$first" | jq -r '.data.rpcPassword')"
second_password="$(printf '%s' "$second" | jq -r '.data.rpcPassword')"
[[ "$first_password" =~ ^[0-9a-f]{64}$ && "$first_password" == "$second_password" ]]
[[ "$(stat -c '%a' "$MAGO_HOME/config/rpc.env")" == 600 ]]
printf '%s' "$first" | jq -e '.data.credentialsCreated == "true"' >/dev/null
printf '%s' "$second" | jq -e '.data.credentialsCreated == "false"' >/dev/null

FAKE_PATH="$ROOT/termux-bridge/tests/fakes:$PATH"
PATH="$FAKE_PATH" bash termux-bridge/scripts/dispatch.sh UPDATE_PACKAGES op-update | jq -e '.success == true' >/dev/null
PATH="$FAKE_PATH" bash termux-bridge/scripts/dispatch.sh INSTALL_DEPENDENCIES op-deps | jq -e '.success == true' >/dev/null
PATH="$FAKE_PATH" bash termux-bridge/scripts/dispatch.sh INSTALL_METASPLOIT op-msf-1 | jq -e '.success == true' >/dev/null
PATH="$FAKE_PATH" bash termux-bridge/scripts/dispatch.sh INSTALL_METASPLOIT op-msf-2 | jq -e '.success == true' >/dev/null
[[ "$(grep -c '^git clone ' "$MAGO_FAKE_LOG")" == 1 ]]
if grep -Fq "$first_password" "$MAGO_FAKE_LOG"; then
  echo 'RPC password leaked into command log' >&2
  exit 1
fi

FAIL_HOME="$TMP/failure-home"; mkdir -p "$FAIL_HOME/.mago/metasploit-framework"
cat > "$TMP/fail-bin-initdb" <<'EOF_FAIL'
#!/usr/bin/env bash
exit 1
EOF_FAIL
chmod +x "$TMP/fail-bin-initdb"
mkdir -p "$TMP/failbin"
ln -s "$TMP/fail-bin-initdb" "$TMP/failbin/initdb"
ln -s "$ROOT/termux-bridge/tests/fakes/pg_ctl" "$TMP/failbin/pg_ctl"
db_failure="$(HOME="$FAIL_HOME" MAGO_HOME="$FAIL_HOME/.mago" MAGO_FAKE_LOG="$MAGO_FAKE_LOG" PATH="$TMP/failbin:$PATH" bash termux-bridge/scripts/dispatch.sh INITIALIZE_DATABASE op-db || true)"
printf '%s' "$db_failure" | jq -e '.success == false and .exitCode == 76 and .action == "INITIALIZE_DATABASE"' >/dev/null

if grep -R -E '\b(eval|bash -c|sh -c)\b' termux-bridge/scripts; then
  echo 'unsafe shell evaluation found' >&2; exit 1
fi
