#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$SCRIPT_DIR/lib/common.sh"
ACTION="HEALTH_CHECK"; OPERATION_ID="${1:-missing-operation-id}"

has_command() { if command_exists "$1"; then printf true; else printf false; fi; }
repo=false; db_config=false; db_running=false; rpc_process=false; rpc_port=false
commit=""
if [[ -d "$MAGO_MSF_DIR/.git" && -f "$MAGO_MSF_DIR/msfconsole" ]]; then
  repo=true
  commit="$(git -C "$MAGO_MSF_DIR" rev-parse --short=12 HEAD 2>/dev/null || true)"
fi
[[ -f "$MSF_CONFIG_DIR/database.yml" ]] && db_config=true
if command_exists pg_isready && pg_isready -h 127.0.0.1 -p 5433 >/dev/null 2>&1; then db_running=true; fi
if process_running "$MAGO_RPC_PID"; then rpc_process=true; fi
if localhost_port_open 55552; then rpc_port=true; fi
prefix="$(json_escape "${PREFIX:-}")"
data="{\"prefix\":\"$prefix\",\"ruby\":\"$(has_command ruby)\",\"psql\":\"$(has_command psql)\",\"msfconsole\":\"$(has_command msfconsole)\",\"metasploitRepository\":\"$repo\",\"metasploitCommit\":\"$(json_escape "$commit")\",\"databaseConfigured\":\"$db_config\",\"databaseRunning\":\"$db_running\",\"rpcProcessRunning\":\"$rpc_process\",\"rpcPortOpen\":\"$rpc_port\",\"bridgeVersion\":\"2\"}"
bridge_ok "$ACTION" "$OPERATION_ID" "Health check completed" 100 "$data"
