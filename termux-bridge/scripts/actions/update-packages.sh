#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ACTION=UPDATE_PACKAGES; OPERATION_ID="${1:-missing-operation-id}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; source "$SCRIPT_DIR/lib/common.sh"
run_update() {
  require_command "$ACTION" "$OPERATION_ID" pkg 71
  export DEBIAN_FRONTEND=noninteractive
  pkg update -y || bridge_fail "$ACTION" "$OPERATION_ID" 71 "Unable to update Termux package indexes"
  pkg upgrade -y || bridge_fail "$ACTION" "$OPERATION_ID" 71 "Unable to upgrade Termux packages"
  bridge_ok "$ACTION" "$OPERATION_ID" "Termux packages updated" 100
}
with_install_lock "$ACTION" "$OPERATION_ID" run_update
