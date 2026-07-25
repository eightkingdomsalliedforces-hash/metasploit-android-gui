#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$SCRIPT_DIR/lib/common.sh"
ACTION="CLEAN_CACHE"; OPERATION_ID="${1:-missing-operation-id}"
rm -rf "$HOME/.cache/bundle" "$MAGO_HOME/bootstrap" 2>/dev/null || true
bridge_ok "$ACTION" "$OPERATION_ID" "Installer caches cleared" 100 '{}'
