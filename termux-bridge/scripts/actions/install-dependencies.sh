#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$SCRIPT_DIR/lib/common.sh"
ACTION="INSTALL_DEPENDENCIES"; OPERATION_ID="${1:-missing-operation-id}"
with_install_lock "$ACTION" "$OPERATION_ID"
command_exists pkg || bridge_fail "$ACTION" "$OPERATION_ID" 69 "Termux package manager is unavailable"
packages=(git ruby postgresql openssl libffi libxml2 libxslt libyaml readline ncurses make clang pkg-config rust binutils coreutils findutils procps iproute2 jq tar gzip)
if pkg install -y "${packages[@]}" >"$MAGO_LOG_DIR/dependencies.log" 2>&1; then
  missing=()
  for command in git ruby gem bundle psql pg_ctl openssl make clang; do
    command_exists "$command" || missing+=("$command")
  done
  if (( ${#missing[@]} > 0 )); then
    bridge_fail "$ACTION" "$OPERATION_ID" 69 "Required commands are missing after package installation" "{\"missing\":\"$(json_escape "${missing[*]}")\"}"
  fi
  bridge_ok "$ACTION" "$OPERATION_ID" "Metasploit dependencies installed" 100 '{}'
else
  bridge_fail "$ACTION" "$OPERATION_ID" 69 "Unable to install Metasploit dependencies" '{"log":"dependencies.log"}'
fi
