#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ACTION=CLEAN_CACHE; OPERATION_ID="${1:-missing-operation-id}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; source "$SCRIPT_DIR/lib/common.sh"
rm -rf "$HOME/.cache/bundle" "$HOME/.gem/specs" 2>/dev/null || true
command -v pkg >/dev/null 2>&1 && pkg clean -y >/dev/null 2>&1 || true
bridge_ok "$ACTION" "$OPERATION_ID" "Package caches cleaned" 100
