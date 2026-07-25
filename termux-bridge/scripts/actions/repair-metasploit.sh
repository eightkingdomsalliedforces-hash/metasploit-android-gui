#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$SCRIPT_DIR/lib/common.sh"
ACTION="REPAIR_METASPLOIT"; OPERATION_ID="${1:-missing-operation-id}"
with_install_lock "$ACTION" "$OPERATION_ID"
require_metasploit "$ACTION" "$OPERATION_ID"
(
  cd "$MAGO_MSF_DIR"
  bundle config set --local path "$MAGO_HOME/bundle"
  bundle config set --local without 'development test coverage'
  bundle install --jobs 2 --retry 3
  bundle check
) >"$MAGO_LOG_DIR/metasploit-repair.log" 2>&1 || bridge_fail "$ACTION" "$OPERATION_ID" 70 "Metasploit repair failed" '{"log":"metasploit-repair.log"}'
for executable in msfconsole msfrpcd msfdb; do write_wrapper "$executable"; done
bridge_ok "$ACTION" "$OPERATION_ID" "Metasploit repaired" 100 '{}'
