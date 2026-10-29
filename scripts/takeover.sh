#!/usr/bin/env bash
# Make TV Home the launcher the Home button opens, or restore the stock Google TV home.
# Usage: scripts/takeover.sh on|off|status
set -euo pipefail

PKG=com.jayked.tvhome
STOCK=com.google.android.apps.tv.launcherx

case "${1:-}" in
  on)
    adb shell pm list packages "$PKG" | grep -q "$PKG" || { echo "Install $PKG first (scripts/install.sh)."; exit 1; }
    adb shell pm disable-user --user 0 "$STOCK"
    adb shell cmd package set-home-activity "$PKG/.MainActivity" || true
    adb shell input keyevent KEYCODE_HOME
    echo "Stock launcher disabled. Undo with: scripts/takeover.sh off"
    ;;
  off)
    adb shell pm enable "$STOCK"
    adb shell input keyevent KEYCODE_HOME
    echo "Stock Google TV launcher restored."
    ;;
  status)
    adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1
    ;;
  *)
    echo "Usage: $0 on|off|status" >&2
    exit 1
    ;;
esac
