#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ACTION=START_RPC; OPERATION_ID="${1:-missing-operation-id}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; source "$SCRIPT_DIR/lib/common.sh"
[[ -x "$MAGO_FRAMEWORK_DIR/msfrpcd" ]] || bridge_fail "$ACTION" "$OPERATION_ID" 79 "msfrpcd is not installed"
[[ -s "$MAGO_RPC_CREDENTIALS" ]] || bridge_fail "$ACTION" "$OPERATION_ID" 79 "RPC credentials are not configured"
start_postgres || bridge_fail "$ACTION" "$OPERATION_ID" 77 "PostgreSQL is unavailable"
if pid_is_running "$MAGO_RPC_PID" && rpc_port_open; then
  bridge_ok "$ACTION" "$OPERATION_ID" "RPC is already running" 100 "host" "$MAGO_RPC_HOST" "port" "$MAGO_RPC_PORT"
  exit 0
fi
stop_pid_file "$MAGO_RPC_PID" 2
# shellcheck disable=SC1090
source "$MAGO_RPC_CREDENTIALS"
cd "$MAGO_FRAMEWORK_DIR"
MSF_RPC_USER="$MSF_RPC_USER" MSF_RPC_PASS="$MSF_RPC_PASS" \
  bundle exec ./msfrpcd -a "$MAGO_RPC_HOST" -p "$MAGO_RPC_PORT" -S -f \
  >"$MAGO_LOG_DIR/msfrpcd.log" 2>&1 &
rpc_pid=$!
printf '%s\n' "$rpc_pid" > "$MAGO_RPC_PID"
chmod 600 "$MAGO_RPC_PID"
unset MSF_RPC_PASS
for _ in $(seq 1 60); do
  if rpc_port_open; then
    bridge_ok "$ACTION" "$OPERATION_ID" "Metasploit RPC started" 100 "host" "$MAGO_RPC_HOST" "port" "$MAGO_RPC_PORT"
    exit 0
  fi
  if ! kill -0 "$rpc_pid" 2>/dev/null; then break; fi
  sleep 1
done
stop_pid_file "$MAGO_RPC_PID" 2
bridge_fail "$ACTION" "$OPERATION_ID" 79 "Metasploit RPC did not become ready" "log" "$MAGO_LOG_DIR/msfrpcd.log"
