#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUTPUT="$ROOT/core/termux/src/main/res/raw/mago_bridge_v1.tgz"
METADATA="$ROOT/core/termux/src/main/kotlin/dev/mago/android/termux/BridgeBundleMetadata.kt"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

mkdir -p "$TMP/bridge-v1/actions" "$TMP/bridge-v1/lib" "$(dirname "$OUTPUT")"
install -m 700 "$ROOT/termux-bridge/scripts/dispatch.sh" "$TMP/bridge-v1/dispatch.sh"
install -m 700 "$ROOT/termux-bridge/scripts/lib/common.sh" "$TMP/bridge-v1/lib/common.sh"
for script in "$ROOT"/termux-bridge/scripts/actions/*.sh; do
  install -m 700 "$script" "$TMP/bridge-v1/actions/$(basename "$script")"
done

tar --sort=name \
  --mtime='UTC 2026-01-01 00:00:00' \
  --owner=0 --group=0 --numeric-owner \
  -C "$TMP" -cf - bridge-v1 | gzip -n > "$OUTPUT"

digest="$(sha256sum "$OUTPUT" | awk '{print $1}')"
python3 - "$METADATA" "$digest" <<'PY'
from pathlib import Path
import re, sys
path = Path(sys.argv[1])
digest = sys.argv[2]
text = path.read_text(encoding="utf-8")
text, count = re.subn(r'const val SHA256 = "[0-9a-f]{64}"', f'const val SHA256 = "{digest}"', text)
if count != 1:
    raise SystemExit("BridgeBundleMetadata SHA256 field not found exactly once")
text, count = re.subn(r'const val VERSION = [0-9]+', 'const val VERSION = 2', text)
if count != 1:
    raise SystemExit("BridgeBundleMetadata VERSION field not found exactly once")
path.write_text(text, encoding="utf-8")
PY
printf '%s\n' "$digest"
