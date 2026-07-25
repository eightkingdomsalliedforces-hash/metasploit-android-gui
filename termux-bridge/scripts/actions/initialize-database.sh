#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ACTION=INITIALIZE_DATABASE; OPERATION_ID="${1:-missing-operation-id}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; source "$SCRIPT_DIR/lib/common.sh"
initialize_database() {
  require_command "$ACTION" "$OPERATION_ID" initdb 76
  require_command "$ACTION" "$OPERATION_ID" pg_ctl 76
  [[ -d "$MAGO_FRAMEWORK_DIR" ]] || bridge_fail "$ACTION" "$OPERATION_ID" 76 "Metasploit is not installed"
  if [[ ! -f "$MAGO_PGDATA/PG_VERSION" ]]; then
    rm -rf "$MAGO_PGDATA"
    initdb -D "$MAGO_PGDATA" --auth=trust --encoding=UTF8 --username="${USER:-termux}" >/dev/null || \
      bridge_fail "$ACTION" "$OPERATION_ID" 76 "Unable to initialize PostgreSQL"
  fi
  start_postgres || bridge_fail "$ACTION" "$OPERATION_ID" 77 "PostgreSQL did not become ready"
  db_user="${USER:-termux}"
  if ! psql -h 127.0.0.1 -p "$MAGO_PGPORT" -d postgres -Atqc "SELECT 1 FROM pg_database WHERE datname='msf'" | grep -q 1; then
    createdb -h 127.0.0.1 -p "$MAGO_PGPORT" -O "$db_user" -E UTF8 msf || \
      bridge_fail "$ACTION" "$OPERATION_ID" 77 "Unable to create the Metasploit database"
  fi
  mkdir -p "$(dirname "$MAGO_DB_CONFIG")"
  umask 077
  cat > "$MAGO_DB_CONFIG" <<YAML
development: &pgsql
  adapter: postgresql
  database: msf
  username: $db_user
  host: 127.0.0.1
  port: $MAGO_PGPORT
  pool: 75
production:
  <<: *pgsql
test:
  <<: *pgsql
  database: msftest
YAML
  chmod 600 "$MAGO_DB_CONFIG"
  cd "$MAGO_FRAMEWORK_DIR"
  bundle exec rake db:migrate >/dev/null || bridge_fail "$ACTION" "$OPERATION_ID" 77 "Metasploit database migration failed"
  bridge_ok "$ACTION" "$OPERATION_ID" "PostgreSQL and Metasploit database initialized" 100 "port" "$MAGO_PGPORT"
}
with_install_lock "$ACTION" "$OPERATION_ID" initialize_database
