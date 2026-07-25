#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$SCRIPT_DIR/lib/common.sh"
ACTION="UPDATE_PACKAGES"; OPERATION_ID="${1:-missing-operation-id}"
with_install_lock "$ACTION" "$OPERATION_ID"
command_exists pkg || bridge_fail "$ACTION" "$OPERATION_ID" 69 "Termux package manager is unavailable"
if pkg update -y >"$MAGO_LOG_DIR/pkg-update.log" 2>&1; then
  bridge_ok "$ACTION" "$OPERATION_ID" "Termux package indexes updated" 100 '{}'
else
  bridge_fail "$ACTION" "$OPERATION_ID" 74 "Unable to update Termux package indexes" '{"log":"pkg-update.log"}'
fi
