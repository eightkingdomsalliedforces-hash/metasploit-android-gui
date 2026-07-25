#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$SCRIPT_DIR/lib/common.sh"
ACTION="INITIALIZE_DATABASE"; OPERATION_ID="${1:-missing-operation-id}"
with_install_lock "$ACTION" "$OPERATION_ID"
require_metasploit "$ACTION" "$OPERATION_ID"
command_exists pg_ctl || bridge_fail "$ACTION" "$OPERATION_ID" 71 "PostgreSQL is not installed"
if [[ -f "$MSF_CONFIG_DIR/database.yml" ]]; then
  bridge_ok "$ACTION" "$OPERATION_ID" "Metasploit database is already initialized" 100 '{"initialized":"true"}'
  exit 0
fi
if ! "$MAGO_BIN_DIR/msfdb" --component database --use-defaults init >"$MAGO_LOG_DIR/msfdb-init.log" 2>&1; then
  bridge_fail "$ACTION" "$OPERATION_ID" 71 "Unable to initialize the Metasploit database" '{"log":"msfdb-init.log"}'
fi
[[ -f "$MSF_CONFIG_DIR/database.yml" ]] || bridge_fail "$ACTION" "$OPERATION_ID" 71 "Database configuration was not created"
bridge_ok "$ACTION" "$OPERATION_ID" "Metasploit database initialized" 100 '{"initialized":"true"}'
