#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ACTION="${1:-}"
OPERATION_ID="${2:-}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if ! [[ "$OPERATION_ID" =~ ^[A-Za-z0-9._-]{1,128}$ ]]; then
  printf '%s\n' '{"schemaVersion":1,"operationId":"invalid-operation","action":"HEALTH_CHECK","success":false,"exitCode":65,"message":"Invalid operation id","progress":0,"data":{}}'
  exit 65
fi

case "$ACTION" in
  HEALTH_CHECK)
    exec "${BASH:-/data/data/com.termux/files/usr/bin/bash}" "$SCRIPT_DIR/actions/health-check.sh" "$OPERATION_ID"
    ;;
  INSTALL_METASPLOIT|REPAIR_METASPLOIT|INITIALIZE_DATABASE|START_SERVICES|STOP_SERVICES|START_RPC|STOP_RPC|UPDATE_METASPLOIT|BACKUP_ENVIRONMENT|RESTORE_ENVIRONMENT|CLEAN_CACHE)
    printf '{"schemaVersion":1,"operationId":"%s","action":"%s","success":false,"exitCode":64,"message":"Unsupported action in bridge v1","progress":0,"data":{}}\n' "$OPERATION_ID" "$ACTION"
    exit 64
    ;;
  *)
    printf '{"schemaVersion":1,"operationId":"%s","action":"HEALTH_CHECK","success":false,"exitCode":64,"message":"Unknown action","progress":0,"data":{}}\n' "$OPERATION_ID"
    exit 64
    ;;
esac
