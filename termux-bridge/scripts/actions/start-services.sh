#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$SCRIPT_DIR/lib/common.sh"
ACTION="START_SERVICES"; OPERATION_ID="${1:-missing-operation-id}"
require_metasploit "$ACTION" "$OPERATION_ID"
[[ -f "$MSF_CONFIG_DIR/database.yml" ]] || bridge_fail "$ACTION" "$OPERATION_ID" 71 "Database is not initialized"
if "$MAGO_BIN_DIR/msfdb" --component database status >"$MAGO_LOG_DIR/msfdb-status.log" 2>&1 && grep -q 'Database started' "$MAGO_LOG_DIR/msfdb-status.log"; then
  bridge_ok "$ACTION" "$OPERATION_ID" "PostgreSQL is already running" 100 '{"databaseRunning":"true"}'
  exit 0
fi
if ! "$MAGO_BIN_DIR/msfdb" --component database start >"$MAGO_LOG_DIR/msfdb-start.log" 2>&1; then
  bridge_fail "$ACTION" "$OPERATION_ID" 71 "Unable to start PostgreSQL" '{"log":"msfdb-start.log"}'
fi
bridge_ok "$ACTION" "$OPERATION_ID" "PostgreSQL started" 100 '{"databaseRunning":"true"}'
