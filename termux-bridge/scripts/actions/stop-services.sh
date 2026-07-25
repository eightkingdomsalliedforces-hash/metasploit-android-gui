#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$SCRIPT_DIR/lib/common.sh"
ACTION="STOP_SERVICES"; OPERATION_ID="${1:-missing-operation-id}"
if [[ ! -x "$MAGO_BIN_DIR/msfdb" || ! -f "$MSF_CONFIG_DIR/database.yml" ]]; then
  bridge_ok "$ACTION" "$OPERATION_ID" "PostgreSQL is already stopped" 100 '{"databaseRunning":"false"}'
  exit 0
fi
if ! "$MAGO_BIN_DIR/msfdb" --component database stop >"$MAGO_LOG_DIR/msfdb-stop.log" 2>&1; then
  bridge_fail "$ACTION" "$OPERATION_ID" 71 "Unable to stop PostgreSQL" '{"log":"msfdb-stop.log"}'
fi
bridge_ok "$ACTION" "$OPERATION_ID" "PostgreSQL stopped" 100 '{"databaseRunning":"false"}'
