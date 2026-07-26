#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ACTION=START_SERVICES; OPERATION_ID="${1:-missing-operation-id}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; source "$SCRIPT_DIR/lib/common.sh"
start_postgres || bridge_fail "$ACTION" "$OPERATION_ID" 77 "PostgreSQL did not become ready"
if ! ${BASH:-/data/data/com.termux/files/usr/bin/bash} "$SCRIPT_DIR/actions/start-rpc.sh" "$OPERATION_ID" >/dev/null; then
  bridge_fail "$ACTION" "$OPERATION_ID" 79 "Metasploit RPC did not become ready"
fi
bridge_ok "$ACTION" "$OPERATION_ID" "Metasploit services started" 100 "database" "ready" "rpc" "ready"
