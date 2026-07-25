#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ACTION=UPDATE_METASPLOIT; OPERATION_ID="${1:-missing-operation-id}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; source "$SCRIPT_DIR/lib/common.sh"
update_framework() {
  [[ -d "$MAGO_FRAMEWORK_DIR/.git" ]] || bridge_fail "$ACTION" "$OPERATION_ID" 74 "Metasploit is not installed"
  git -C "$MAGO_FRAMEWORK_DIR" fetch --depth 1 origin master || bridge_fail "$ACTION" "$OPERATION_ID" 74 "Unable to fetch Metasploit updates"
  git -C "$MAGO_FRAMEWORK_DIR" reset --hard FETCH_HEAD >/dev/null
  cd "$MAGO_FRAMEWORK_DIR"
  bundler_version="$(awk '/^BUNDLED WITH/{getline; gsub(/^[[:space:]]+/, ""); print; exit}' Gemfile.lock)"
  gem install bundler -v "$bundler_version" --no-document || bridge_fail "$ACTION" "$OPERATION_ID" 75 "Unable to update Bundler"
  bundle config set --local path "$MAGO_BUNDLE_DIR"
  bundle config set --local without 'development test'
  bundle install --jobs "${MAGO_BUNDLE_JOBS:-2}" --retry 3 || bridge_fail "$ACTION" "$OPERATION_ID" 75 "Unable to update Metasploit dependencies"
  commit="$(git -C "$MAGO_FRAMEWORK_DIR" rev-parse --short=12 HEAD)"
  bridge_ok "$ACTION" "$OPERATION_ID" "Metasploit Framework updated" 100 "commit" "$commit"
}
with_install_lock "$ACTION" "$OPERATION_ID" update_framework
