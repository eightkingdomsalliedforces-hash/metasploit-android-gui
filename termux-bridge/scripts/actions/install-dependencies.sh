#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ACTION=INSTALL_DEPENDENCIES; OPERATION_ID="${1:-missing-operation-id}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; source "$SCRIPT_DIR/lib/common.sh"
readonly PACKAGES=(git ruby clang make pkg-config postgresql openssl libpcap libsqlite libffi libxml2 libxslt zlib readline ncurses-utils iproute2 libgmp autoconf bison rust cmake ninja)
run_install() {
  require_command "$ACTION" "$OPERATION_ID" pkg 72
  export DEBIAN_FRONTEND=noninteractive
  pkg install -y "${PACKAGES[@]}" || bridge_fail "$ACTION" "$OPERATION_ID" 72 "Unable to install Metasploit dependencies"
  local command_name
  for command_name in git ruby gem initdb pg_ctl pg_isready openssl ss; do
    require_command "$ACTION" "$OPERATION_ID" "$command_name" 72
  done
  bridge_ok "$ACTION" "$OPERATION_ID" "Metasploit dependencies installed" 100 "packageCount" "${#PACKAGES[@]}"
}
with_install_lock "$ACTION" "$OPERATION_ID" run_install
