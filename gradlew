#!/usr/bin/env sh
set -eu

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi

cat >&2 <<'EOF'
Gradle is not installed and gradle-wrapper.jar is not present in this source bundle.
Open the project with Android Studio, or install Gradle 9.5.0 and run:
  gradle wrapper --gradle-version 9.5.0
Then commit gradle/wrapper/gradle-wrapper.jar for standard wrapper usage.
EOF
exit 127
