#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$SCRIPT_DIR/lib/common.sh"
ACTION="UPDATE_METASPLOIT"; OPERATION_ID="${1:-missing-operation-id}"
with_install_lock "$ACTION" "$OPERATION_ID"
require_metasploit "$ACTION" "$OPERATION_ID"
origin="$(git -C "$MAGO_MSF_DIR" remote get-url origin 2>/dev/null || true)"
[[ "$origin" == "https://github.com/rapid7/metasploit-framework.git" ]] || bridge_fail "$ACTION" "$OPERATION_ID" 70 "Metasploit origin is not Rapid7"
if ! git -C "$MAGO_MSF_DIR" pull --ff-only >"$MAGO_LOG_DIR/metasploit-update.log" 2>&1; then
  bridge_fail "$ACTION" "$OPERATION_ID" 74 "Unable to update Metasploit" '{"log":"metasploit-update.log"}'
fi
(
  cd "$MAGO_MSF_DIR"
  bundle install --jobs 2 --retry 3
  bundle check
) >>"$MAGO_LOG_DIR/metasploit-update.log" 2>&1 || bridge_fail "$ACTION" "$OPERATION_ID" 70 "Updated Metasploit dependencies failed" '{"log":"metasploit-update.log"}'
commit="$(git -C "$MAGO_MSF_DIR" rev-parse --short=12 HEAD)"
bridge_ok "$ACTION" "$OPERATION_ID" "Metasploit updated" 100 "{\"commit\":\"$(json_escape "$commit")\"}"
