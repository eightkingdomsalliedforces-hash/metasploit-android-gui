#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$SCRIPT_DIR/lib/common.sh"
ACTION="STOP_RPC"; OPERATION_ID="${1:-missing-operation-id}"
if process_running "$MAGO_RPC_PID"; then
  pid="$(cat "$MAGO_RPC_PID")"
  kill "$pid" 2>/dev/null || true
  for _ in $(seq 1 20); do kill -0 "$pid" 2>/dev/null || break; sleep 1; done
  kill -9 "$pid" 2>/dev/null || true
fi
rm -f "$MAGO_RPC_PID"
bridge_ok "$ACTION" "$OPERATION_ID" "Metasploit RPC stopped" 100 '{"rpcPortOpen":"false"}'
