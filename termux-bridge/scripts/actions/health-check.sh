#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

OPERATION_ID="${1:-missing-operation-id}"

has_command() {
  if command -v "$1" >/dev/null 2>&1; then
    printf true
  else
    printf false
  fi
}

json_escape() {
  local value="$1"
  value=${value//\\/\\\\}
  value=${value//\"/\\\"}
  value=${value//$'\n'/\\n}
  value=${value//$'\r'/\\r}
  value=${value//$'\t'/\\t}
  printf '%s' "$value"
}

port_open=false
if command -v ss >/dev/null 2>&1 && ss -ltn 2>/dev/null | grep -qE '(^|[[:space:]])127\.0\.0\.1:55552([[:space:]]|$)'; then
  port_open=true
fi

prefix="$(json_escape "${PREFIX:-}")"
printf '{"schemaVersion":1,"operationId":"%s","action":"HEALTH_CHECK","success":true,"exitCode":0,"message":"Health check completed","progress":100,"data":{"prefix":"%s","ruby":"%s","psql":"%s","msfconsole":"%s","rpcPortOpen":"%s","bridgeVersion":"1"}}\n' \
  "$OPERATION_ID" "$prefix" "$(has_command ruby)" "$(has_command psql)" \
  "$(has_command msfconsole)" "$port_open"
