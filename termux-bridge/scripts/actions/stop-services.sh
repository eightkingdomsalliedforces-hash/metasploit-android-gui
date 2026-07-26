#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ACTION=STOP_SERVICES; OPERATION_ID="${1:-missing-operation-id}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; source "$SCRIPT_DIR/lib/common.sh"
${BASH:-/data/data/com.termux/files/usr/bin/bash} "$SCRIPT_DIR/actions/stop-rpc.sh" "$OPERATION_ID" >/dev/null || true
if [[ -f "$MAGO_PGDATA/PG_VERSION" ]] && command -v pg_ctl >/dev/null 2>&1; then
  pg_ctl -D "$MAGO_PGDATA" stop -m fast >/dev/null 2>&1 || true
fi
bridge_ok "$ACTION" "$OPERATION_ID" "Metasploit services stopped" 100
