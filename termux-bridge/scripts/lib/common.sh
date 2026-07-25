#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

MAGO_HOME="${MAGO_HOME:-$HOME/.mago}"
MAGO_STATE_DIR="${MAGO_STATE_DIR:-$MAGO_HOME/state}"
MAGO_LOG_DIR="${MAGO_LOG_DIR:-$MAGO_HOME/logs}"
MAGO_CONFIG_DIR="${MAGO_CONFIG_DIR:-$MAGO_HOME/config}"
MAGO_BIN_DIR="${MAGO_BIN_DIR:-$MAGO_HOME/bin}"
MAGO_MSF_DIR="${MAGO_MSF_DIR:-$MAGO_HOME/metasploit-framework}"
MAGO_RPC_ENV="${MAGO_RPC_ENV:-$MAGO_CONFIG_DIR/rpc.env}"
MAGO_RPC_PID="${MAGO_RPC_PID:-$MAGO_STATE_DIR/msfrpcd.pid}"
MAGO_RPC_LOG="${MAGO_RPC_LOG:-$MAGO_LOG_DIR/msfrpcd.log}"
MAGO_INSTALL_LOCK="${MAGO_INSTALL_LOCK:-$MAGO_STATE_DIR/install.lock}"
MSF_CONFIG_DIR="${MSF_CONFIG_DIR:-$HOME/.msf4}"

mkdir -p "$MAGO_STATE_DIR" "$MAGO_LOG_DIR" "$MAGO_CONFIG_DIR" "$MAGO_BIN_DIR"
umask 077

json_escape() {
  local value="${1:-}"
  value=${value//\\/\\\\}
  value=${value//\"/\\\"}
  value=${value//$'\n'/\\n}
  value=${value//$'\r'/\\r}
  value=${value//$'\t'/\\t}
  printf '%s' "$value"
}

bridge_ok() {
  local action="$1" operation_id="$2" message="$3" progress="${4:-100}" data="${5-}"
  [[ -n "$data" ]] || data='{}'
  printf '{"schemaVersion":2,"operationId":"%s","action":"%s","success":true,"exitCode":0,"message":"%s","progress":%s,"data":%s}\n' \
    "$(json_escape "$operation_id")" "$(json_escape "$action")" "$(json_escape "$message")" "$progress" "$data"
}

bridge_fail() {
  local action="$1" operation_id="$2" exit_code="$3" message="$4" data="${5-}"
  [[ -n "$data" ]] || data='{}'
  printf '{"schemaVersion":2,"operationId":"%s","action":"%s","success":false,"exitCode":%s,"message":"%s","progress":0,"data":%s}\n' \
    "$(json_escape "$operation_id")" "$(json_escape "$action")" "$exit_code" "$(json_escape "$message")" "$data"
  exit "$exit_code"
}

command_exists() { command -v "$1" >/dev/null 2>&1; }

process_running() {
  local pid_file="$1" pid
  [[ -f "$pid_file" ]] || return 1
  pid="$(cat "$pid_file" 2>/dev/null || true)"
  [[ "$pid" =~ ^[0-9]+$ ]] || return 1
  kill -0 "$pid" >/dev/null 2>&1
}

localhost_port_open() {
  local port="$1"
  command_exists ss || return 1
  ss -ltn 2>/dev/null | grep -qE "(^|[[:space:]])127\\.0\\.0\\.1:${port}([[:space:]]|$)"
}

with_install_lock() {
  local action="$1" operation_id="$2"
  if ! mkdir "$MAGO_INSTALL_LOCK" 2>/dev/null; then
    bridge_fail "$action" "$operation_id" 75 "Another installation operation is already running"
  fi
  trap 'rmdir "$MAGO_INSTALL_LOCK" 2>/dev/null || true' EXIT INT TERM
}

require_metasploit() {
  local action="$1" operation_id="$2"
  [[ -d "$MAGO_MSF_DIR/.git" && -f "$MAGO_MSF_DIR/msfconsole" ]] || \
    bridge_fail "$action" "$operation_id" 70 "Metasploit is not installed"
}

write_wrapper() {
  local name="$1"
  cat > "$MAGO_BIN_DIR/$name" <<WRAPPER
#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
cd "$MAGO_MSF_DIR"
exec bundle exec ruby "$MAGO_MSF_DIR/$name" "\$@"
WRAPPER
  chmod 700 "$MAGO_BIN_DIR/$name"
  if [[ -n "${PREFIX:-}" && -d "$PREFIX/bin" ]]; then
    ln -sfn "$MAGO_BIN_DIR/$name" "$PREFIX/bin/$name"
  fi
}
