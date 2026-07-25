#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ACTION=CONFIGURE_RPC; OPERATION_ID="${1:-missing-operation-id}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; source "$SCRIPT_DIR/lib/common.sh"
require_command "$ACTION" "$OPERATION_ID" openssl 78
credentials_created=false
if [[ ! -s "$MAGO_RPC_CREDENTIALS" ]]; then
  umask 077
  rpc_user=msf
  rpc_password="$(openssl rand -hex 32)" || bridge_fail "$ACTION" "$OPERATION_ID" 78 "Unable to generate RPC credentials"
  cat > "$MAGO_RPC_CREDENTIALS" <<EOF_CREDS
MSF_RPC_USER=$rpc_user
MSF_RPC_PASS=$rpc_password
EOF_CREDS
  chmod 600 "$MAGO_RPC_CREDENTIALS"
  credentials_created=true
fi
# Values are generated from a fixed username and hexadecimal random bytes, so sourcing is safe.
# shellcheck disable=SC1090
source "$MAGO_RPC_CREDENTIALS"
[[ -n "${MSF_RPC_USER:-}" && "${MSF_RPC_PASS:-}" =~ ^[0-9a-f]{64}$ ]] || \
  bridge_fail "$ACTION" "$OPERATION_ID" 78 "Stored RPC credentials are invalid"
bridge_ok "$ACTION" "$OPERATION_ID" "RPC credentials configured" 100 \
  "rpcUser" "$MSF_RPC_USER" "rpcPassword" "$MSF_RPC_PASS" "credentialsCreated" "$credentials_created"
unset MSF_RPC_PASS
