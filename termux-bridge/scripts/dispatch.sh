#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ACTION="${1:-}"
OPERATION_ID="${2:-}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if ! [[ "$OPERATION_ID" =~ ^[A-Za-z0-9._-]{1,128}$ ]]; then
  printf '%s\n' '{"schemaVersion":2,"operationId":"invalid-operation","action":"HEALTH_CHECK","success":false,"exitCode":65,"message":"Invalid operation id","progress":0,"data":{}}'
  exit 65
fi

case "$ACTION" in
  UPDATE_PACKAGES) script="update-packages.sh" ;;
  INSTALL_DEPENDENCIES) script="install-dependencies.sh" ;;
  INSTALL_METASPLOIT) script="install-metasploit.sh" ;;
  REPAIR_METASPLOIT) script="repair-metasploit.sh" ;;
  INITIALIZE_DATABASE) script="initialize-database.sh" ;;
  CONFIGURE_RPC) script="configure-rpc.sh" ;;
  START_SERVICES) script="start-services.sh" ;;
  STOP_SERVICES) script="stop-services.sh" ;;
  START_RPC) script="start-rpc.sh" ;;
  STOP_RPC) script="stop-rpc.sh" ;;
  UPDATE_METASPLOIT) script="update-metasploit.sh" ;;
  HEALTH_CHECK) script="health-check.sh" ;;
  CLEAN_CACHE) script="clean-cache.sh" ;;
  BACKUP_ENVIRONMENT|RESTORE_ENVIRONMENT)
    printf '{"schemaVersion":2,"operationId":"%s","action":"%s","success":false,"exitCode":64,"message":"Unsupported action in bridge v2","progress":0,"data":{}}\n' "$OPERATION_ID" "$ACTION"
    exit 64
    ;;
  *)
    printf '{"schemaVersion":2,"operationId":"%s","action":"HEALTH_CHECK","success":false,"exitCode":64,"message":"Unknown action","progress":0,"data":{}}\n' "$OPERATION_ID"
    exit 64
    ;;
esac

exec "${BASH:-/data/data/com.termux/files/usr/bin/bash}" "$SCRIPT_DIR/actions/$script" "$OPERATION_ID"
