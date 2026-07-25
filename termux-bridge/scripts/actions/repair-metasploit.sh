#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ACTION=REPAIR_METASPLOIT; OPERATION_ID="${1:-missing-operation-id}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; source "$SCRIPT_DIR/lib/common.sh"
repair_framework() {
  [[ -d "$MAGO_FRAMEWORK_DIR/.git" ]] || bridge_fail "$ACTION" "$OPERATION_ID" 74 "Metasploit is not installed"
  cd "$MAGO_FRAMEWORK_DIR"
  bundler_version="$(awk '/^BUNDLED WITH/{getline; gsub(/^[[:space:]]+/, ""); print; exit}' Gemfile.lock)"
  gem install bundler -v "$bundler_version" --no-document || bridge_fail "$ACTION" "$OPERATION_ID" 75 "Unable to repair Bundler"
  bundle config set --local path "$MAGO_BUNDLE_DIR"
  bundle config set --local without 'development test'
  bundle check || bundle install --jobs "${MAGO_BUNDLE_JOBS:-2}" --retry 3 || bridge_fail "$ACTION" "$OPERATION_ID" 75 "Unable to repair Metasploit dependencies"
  bridge_ok "$ACTION" "$OPERATION_ID" "Metasploit installation repaired" 100
}
with_install_lock "$ACTION" "$OPERATION_ID" repair_framework
