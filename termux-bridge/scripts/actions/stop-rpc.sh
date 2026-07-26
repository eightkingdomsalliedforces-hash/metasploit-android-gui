#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ACTION=STOP_RPC; OPERATION_ID="${1:-missing-operation-id}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; source "$SCRIPT_DIR/lib/common.sh"
stop_pid_file "$MAGO_RPC_PID" 15
bridge_ok "$ACTION" "$OPERATION_ID" "Metasploit RPC stopped" 100
