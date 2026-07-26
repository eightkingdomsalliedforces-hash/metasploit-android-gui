#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

export MAGO_HOME="${MAGO_HOME:-$HOME/.mago}"
export MAGO_STATE_DIR="$MAGO_HOME/state"
export MAGO_RUN_DIR="$MAGO_HOME/run"
export MAGO_LOG_DIR="$MAGO_HOME/logs"
export MAGO_CONFIG_DIR="$MAGO_HOME/config"
export MAGO_FRAMEWORK_DIR="$MAGO_HOME/metasploit-framework"
export MAGO_BUNDLE_DIR="$MAGO_HOME/bundle"
export MAGO_PGDATA="$MAGO_HOME/postgresql"
export MAGO_PGSOCKET="$MAGO_RUN_DIR/postgresql"
export MAGO_PGPORT="${MAGO_PGPORT:-54329}"
export MAGO_RPC_HOST="127.0.0.1"
export MAGO_RPC_PORT="55552"
export MAGO_RPC_CREDENTIALS="$MAGO_CONFIG_DIR/rpc.env"
export MAGO_RPC_PID="$MAGO_RUN_DIR/msfrpcd.pid"
export MAGO_PG_PID="$MAGO_PGDATA/postmaster.pid"
export MAGO_DB_CONFIG="$HOME/.msf4/database.yml"
export MAGO_INSTALL_LOCK="$MAGO_STATE_DIR/install.lock"

mkdir -p "$MAGO_STATE_DIR" "$MAGO_RUN_DIR" "$MAGO_LOG_DIR" "$MAGO_CONFIG_DIR" "$MAGO_PGSOCKET"

json_escape() {
  local value="${1:-}"
  value=${value//\\/\\\\}
  value=${value//\"/\\\"}
  value=${value//$'\n'/\\n}
  value=${value//$'\r'/\\r}
  value=${value//$'\t'/\\t}
  printf '%s' "$value"
}

json_data() {
  local first=true key value
  printf '{'
  while (($# >= 2)); do
    key="$1"; value="$2"; shift 2
    if [[ "$first" == false ]]; then printf ','; fi
    first=false
    printf '"%s":"%s"' "$(json_escape "$key")" "$(json_escape "$value")"
  done
  printf '}'
}

bridge_response() {
  local action="$1" operation_id="$2" success="$3" exit_code="$4" message="$5" progress="$6"
  shift 6
  printf '{"schemaVersion":1,"operationId":"%s","action":"%s","success":%s,"exitCode":%s,"message":"%s","progress":%s,"data":' \
    "$(json_escape "$operation_id")" "$(json_escape "$action")" "$success" "$exit_code" "$(json_escape "$message")" "$progress"
  json_data "$@"
  printf '}\n'
}

bridge_ok() {
  local action="$1" operation_id="$2" message="$3" progress="${4:-100}"
  if (($# >= 4)); then shift 4; else shift "$#"; fi
  bridge_response "$action" "$operation_id" true 0 "$message" "$progress" "$@"
}

bridge_fail() {
  local action="$1" operation_id="$2" exit_code="$3" message="$4"
  shift 4
  bridge_response "$action" "$operation_id" false "$exit_code" "$message" 0 "$@"
  exit "$exit_code"
}

require_command() {
  local action="$1" operation_id="$2" command_name="$3" error_code="${4:-70}"
  command -v "$command_name" >/dev/null 2>&1 || \
    bridge_fail "$action" "$operation_id" "$error_code" "Required command is unavailable: $command_name" "missingCommand" "$command_name"
}

with_install_lock() {
  local action="$1" operation_id="$2"
  shift 2
  if ! mkdir "$MAGO_INSTALL_LOCK" 2>/dev/null; then
    bridge_fail "$action" "$operation_id" 73 "Another installation operation is already running" "lock" "$MAGO_INSTALL_LOCK"
  fi
  trap 'rmdir "$MAGO_INSTALL_LOCK" 2>/dev/null || true' EXIT INT TERM
  "$@"
}

pid_is_running() {
  local pid_file="$1" pid
  [[ -s "$pid_file" ]] || return 1
  pid="$(head -n 1 "$pid_file" 2>/dev/null || true)"
  [[ "$pid" =~ ^[0-9]+$ ]] || return 1
  kill -0 "$pid" 2>/dev/null
}

stop_pid_file() {
  local pid_file="$1" timeout_seconds="${2:-15}" pid elapsed=0
  [[ -s "$pid_file" ]] || return 0
  pid="$(head -n 1 "$pid_file" 2>/dev/null || true)"
  if [[ "$pid" =~ ^[0-9]+$ ]] && kill -0 "$pid" 2>/dev/null; then
    kill "$pid" 2>/dev/null || true
    while kill -0 "$pid" 2>/dev/null && ((elapsed < timeout_seconds)); do
      sleep 1
      elapsed=$((elapsed + 1))
    done
    if kill -0 "$pid" 2>/dev/null; then kill -9 "$pid" 2>/dev/null || true; fi
  fi
  rm -f "$pid_file"
}

rpc_port_open() {
  if command -v ss >/dev/null 2>&1; then
    ss -ltn 2>/dev/null | grep -qE '(^|[[:space:]])127\.0\.0\.1:55552([[:space:]]|$)'
  elif command -v python3 >/dev/null 2>&1; then
    python3 - <<'PY'
import socket
s=socket.socket(); s.settimeout(.4)
try:
    s.connect(("127.0.0.1",55552)); raise SystemExit(0)
except OSError:
    raise SystemExit(1)
finally:
    s.close()
PY
  else
    return 1
  fi
}

postgres_ready() {
  command -v pg_isready >/dev/null 2>&1 && \
    pg_isready -h 127.0.0.1 -p "$MAGO_PGPORT" >/dev/null 2>&1
}

start_postgres() {
  mkdir -p "$MAGO_PGSOCKET" "$MAGO_LOG_DIR"
  if postgres_ready; then return 0; fi
  [[ -f "$MAGO_PGDATA/PG_VERSION" ]] || return 1
  pg_ctl -D "$MAGO_PGDATA" -l "$MAGO_LOG_DIR/postgresql.log" \
    -o "-h 127.0.0.1 -p $MAGO_PGPORT -k $MAGO_PGSOCKET" start >/dev/null
  local attempt
  for ((attempt = 0; attempt < 30; attempt += 1)); do
    postgres_ready && return 0
    sleep 1
  done
  return 1
}
