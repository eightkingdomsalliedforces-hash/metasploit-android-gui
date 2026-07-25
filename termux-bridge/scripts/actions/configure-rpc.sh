#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$SCRIPT_DIR/lib/common.sh"
ACTION="CONFIGURE_RPC"; OPERATION_ID="${1:-missing-operation-id}"
require_metasploit "$ACTION" "$OPERATION_ID"
created=false
if [[ ! -f "$MAGO_RPC_ENV" ]]; then
  command_exists openssl || bridge_fail "$ACTION" "$OPERATION_ID" 72 "OpenSSL is required to create RPC credentials"
  password="$(openssl rand -hex 32)"
  [[ "$password" =~ ^[0-9a-f]{64}$ ]] || bridge_fail "$ACTION" "$OPERATION_ID" 72 "Unable to generate RPC credentials"
  cat > "$MAGO_RPC_ENV" <<EOF
MSF_RPC_USER=msf
MSF_RPC_PASS=$password
EOF
  chmod 600 "$MAGO_RPC_ENV"
  created=true
fi
source "$MAGO_RPC_ENV"
[[ "${MSF_RPC_USER:-}" =~ ^[A-Za-z0-9._-]{1,64}$ ]] || bridge_fail "$ACTION" "$OPERATION_ID" 72 "Stored RPC username is invalid"
[[ "${MSF_RPC_PASS:-}" =~ ^[0-9a-f]{64}$ ]] || bridge_fail "$ACTION" "$OPERATION_ID" 72 "Stored RPC password is invalid"
bridge_ok "$ACTION" "$OPERATION_ID" "RPC credentials configured" 100 "{\"rpcUser\":\"$(json_escape "$MSF_RPC_USER")\",\"rpcPassword\":\"$(json_escape "$MSF_RPC_PASS")\",\"credentialsCreated\":\"$created\"}"
