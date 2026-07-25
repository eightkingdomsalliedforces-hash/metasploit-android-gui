#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$SCRIPT_DIR/lib/common.sh"
ACTION="INSTALL_METASPLOIT"; OPERATION_ID="${1:-missing-operation-id}"
with_install_lock "$ACTION" "$OPERATION_ID"
for command in git ruby gem bundle; do command_exists "$command" || bridge_fail "$ACTION" "$OPERATION_ID" 69 "Missing dependency: $command"; done
if [[ ! -d "$MAGO_MSF_DIR/.git" ]]; then
  rm -rf "$MAGO_MSF_DIR"
  if ! git clone --depth 1 https://github.com/rapid7/metasploit-framework.git "$MAGO_MSF_DIR" >"$MAGO_LOG_DIR/metasploit-clone.log" 2>&1; then
    bridge_fail "$ACTION" "$OPERATION_ID" 74 "Unable to download Metasploit from Rapid7" '{"log":"metasploit-clone.log"}'
  fi
else
  origin="$(git -C "$MAGO_MSF_DIR" remote get-url origin 2>/dev/null || true)"
  [[ "$origin" == "https://github.com/rapid7/metasploit-framework.git" ]] || bridge_fail "$ACTION" "$OPERATION_ID" 70 "Existing Metasploit directory has an untrusted origin"
fi
bundle_version="$(awk '/^BUNDLED WITH/{getline; gsub(/^[[:space:]]+/, ""); print; exit}' "$MAGO_MSF_DIR/Gemfile.lock")"
[[ "$bundle_version" =~ ^[0-9]+([.][0-9]+)+$ ]] || bridge_fail "$ACTION" "$OPERATION_ID" 70 "Unable to determine the required Bundler version"
if ! gem install bundler -v "$bundle_version" --no-document >"$MAGO_LOG_DIR/bundler-install.log" 2>&1; then
  bridge_fail "$ACTION" "$OPERATION_ID" 70 "Unable to install the required Bundler version" '{"log":"bundler-install.log"}'
fi
(
  cd "$MAGO_MSF_DIR"
  bundle config set --local path "$MAGO_HOME/bundle"
  bundle config set --local without 'development test coverage'
  bundle install --jobs 2 --retry 3
) >"$MAGO_LOG_DIR/bundle-install.log" 2>&1 || bridge_fail "$ACTION" "$OPERATION_ID" 70 "Metasploit Ruby dependencies failed to install" '{"log":"bundle-install.log"}'
for executable in msfconsole msfrpcd msfdb; do
  [[ -f "$MAGO_MSF_DIR/$executable" ]] || bridge_fail "$ACTION" "$OPERATION_ID" 70 "Metasploit executable is missing: $executable"
  write_wrapper "$executable"
done
commit="$(git -C "$MAGO_MSF_DIR" rev-parse --short=12 HEAD)"
bridge_ok "$ACTION" "$OPERATION_ID" "Metasploit installed" 100 "{\"commit\":\"$(json_escape "$commit")\"}"
