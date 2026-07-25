#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ACTION=INSTALL_METASPLOIT; OPERATION_ID="${1:-missing-operation-id}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; source "$SCRIPT_DIR/lib/common.sh"
readonly UPSTREAM=https://github.com/rapid7/metasploit-framework.git
install_framework() {
  require_command "$ACTION" "$OPERATION_ID" git 74
  require_command "$ACTION" "$OPERATION_ID" ruby 74
  require_command "$ACTION" "$OPERATION_ID" gem 74
  if [[ -d "$MAGO_FRAMEWORK_DIR/.git" ]]; then
    remote="$(git -C "$MAGO_FRAMEWORK_DIR" remote get-url origin 2>/dev/null || true)"
    [[ "$remote" == "$UPSTREAM" ]] || bridge_fail "$ACTION" "$OPERATION_ID" 74 "Existing framework repository has an unexpected origin"
    git -C "$MAGO_FRAMEWORK_DIR" fetch --depth 1 origin master || bridge_fail "$ACTION" "$OPERATION_ID" 74 "Unable to refresh the Rapid7 repository"
    git -C "$MAGO_FRAMEWORK_DIR" reset --hard FETCH_HEAD >/dev/null
  elif [[ -e "$MAGO_FRAMEWORK_DIR" ]]; then
    bridge_fail "$ACTION" "$OPERATION_ID" 74 "Framework path exists but is not a Git repository"
  else
    git clone --depth 1 --branch master "$UPSTREAM" "$MAGO_FRAMEWORK_DIR" || bridge_fail "$ACTION" "$OPERATION_ID" 74 "Unable to clone the Rapid7 repository"
  fi
  cd "$MAGO_FRAMEWORK_DIR"
  bundler_version="$(awk '/^BUNDLED WITH/{getline; gsub(/^[[:space:]]+/, ""); print; exit}' Gemfile.lock)"
  [[ "$bundler_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+ ]] || bridge_fail "$ACTION" "$OPERATION_ID" 75 "Unable to determine the required Bundler version"
  gem install bundler -v "$bundler_version" --no-document || bridge_fail "$ACTION" "$OPERATION_ID" 75 "Unable to install the required Bundler version"
  bundle config set --local path "$MAGO_BUNDLE_DIR"
  bundle config set --local without 'development test'
  bundle install --jobs "${MAGO_BUNDLE_JOBS:-2}" --retry 3 || bridge_fail "$ACTION" "$OPERATION_ID" 75 "Unable to install Metasploit Ruby dependencies"
  local executable
  for executable in msfconsole msfrpcd msfdb; do
    [[ -x "$MAGO_FRAMEWORK_DIR/$executable" ]] || bridge_fail "$ACTION" "$OPERATION_ID" 75 "Metasploit executable is missing: $executable"
  done
  commit="$(git -C "$MAGO_FRAMEWORK_DIR" rev-parse --short=12 HEAD)"
  bridge_ok "$ACTION" "$OPERATION_ID" "Metasploit Framework installed" 100 "commit" "$commit" "source" "$UPSTREAM"
}
with_install_lock "$ACTION" "$OPERATION_ID" install_framework
