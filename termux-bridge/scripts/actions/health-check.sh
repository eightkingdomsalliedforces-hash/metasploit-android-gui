#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ACTION=HEALTH_CHECK; OPERATION_ID="${1:-missing-operation-id}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; source "$SCRIPT_DIR/lib/common.sh"
has_command() { command -v "$1" >/dev/null 2>&1 && printf true || printf false; }
repository=false; [[ -d "$MAGO_FRAMEWORK_DIR/.git" ]] && repository=true
database_initialized=false; [[ -f "$MAGO_PGDATA/PG_VERSION" ]] && database_initialized=true
database_config=false; [[ -s "$MAGO_DB_CONFIG" ]] && database_config=true
database_ready=false; postgres_ready && database_ready=true
rpc_configured=false; [[ -s "$MAGO_RPC_CREDENTIALS" ]] && rpc_configured=true
rpc_process=false; pid_is_running "$MAGO_RPC_PID" && rpc_process=true
rpc_port=false; rpc_port_open && rpc_port=true
commit=""; [[ "$repository" == true ]] && commit="$(git -C "$MAGO_FRAMEWORK_DIR" rev-parse --short=12 HEAD 2>/dev/null || true)"
bridge_ok "$ACTION" "$OPERATION_ID" "Health check completed" 100 \
  "prefix" "${PREFIX:-}" "git" "$(has_command git)" "ruby" "$(has_command ruby)" \
  "gem" "$(has_command gem)" "psql" "$(has_command psql)" "initdb" "$(has_command initdb)" \
  "pgCtl" "$(has_command pg_ctl)" "openssl" "$(has_command openssl)" "ss" "$(has_command ss)" "msfconsole" "$([[ -x "$MAGO_FRAMEWORK_DIR/msfconsole" ]] && printf true || printf false)" \
  "frameworkRepository" "$repository" "frameworkCommit" "$commit" \
  "databaseInitialized" "$database_initialized" "databaseConfig" "$database_config" "databaseReady" "$database_ready" \
  "rpcConfigured" "$rpc_configured" "rpcProcessRunning" "$rpc_process" \
  "rpcPortOpen" "$rpc_port" "rpcHost" "$MAGO_RPC_HOST" "rpcPort" "$MAGO_RPC_PORT" \
  "bridgeVersion" "2"
