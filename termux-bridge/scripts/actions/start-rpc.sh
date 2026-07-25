#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$SCRIPT_DIR/lib/common.sh"
ACTION="START_RPC"; OPERATION_ID="${1:-missing-operation-id}"
require_metasploit "$ACTION" "$OPERATION_ID"
[[ -f "$MAGO_RPC_ENV" ]] || bridge_fail "$ACTION" "$OPERATION_ID" 72 "RPC credentials are not configured"
if process_running "$MAGO_RPC_PID" && localhost_port_open 55552; then
  bridge_ok "$ACTION" "$OPERATION_ID" "Metasploit RPC is already running" 100 '{"rpcPort":"55552","rpcHost":"127.0.0.1"}'
  exit 0
fi
rm -f "$MAGO_RPC_PID"
source "$MAGO_RPC_ENV"
(
  cd "$MAGO_MSF_DIR"
  export MSF_RPC_USER MSF_RPC_PASS
  nohup bundle exec ruby "$MAGO_MSF_DIR/msfrpcd" -a 127.0.0.1 -p 55552 -S -f >"$MAGO_RPC_LOG" 2>&1 &
  printf '%s\n' "$!" > "$MAGO_RPC_PID"
)
for _ in $(seq 1 60); do
  if process_running "$MAGO_RPC_PID" && localhost_port_open 55552; then
    bridge_ok "$ACTION" "$OPERATION_ID" "Metasploit RPC started" 100 '{"rpcPort":"55552","rpcHost":"127.0.0.1"}'
    exit 0
  fi
  sleep 1
done
bridge_fail "$ACTION" "$OPERATION_ID" 72 "Metasploit RPC did not start" '{"log":"msfrpcd.log"}'
