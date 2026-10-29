#!/usr/bin/env bash
# Build the launcher and install it on the connected Google TV device.
# Usage: scripts/install.sh [release]
set -euo pipefail
cd "$(dirname "$0")/.."

PKG=com.jayked.tvhome
VARIANT=${1:-debug}
TASK=assemble$(tr '[:lower:]' '[:upper:]' <<<"${VARIANT:0:1}")${VARIANT:1}

./gradlew ":app:$TASK"
adb install -r "app/build/outputs/apk/$VARIANT/app-$VARIANT.apk"
adb shell pm grant "$PKG" com.android.providers.tv.permission.READ_EPG_DATA \
  || echo "Could not grant READ_EPG_DATA; the Continue watching row will stay empty."
adb shell am start -n "$PKG/.MainActivity"
