#!/usr/bin/env bash
# Make the Home button open TV Home, or give it back to Google TV.
# Uses TV Home's accessibility service; Google TV's own launcher stays enabled.
# Usage: scripts/takeover.sh on|off|status
set -euo pipefail

SERVICE=com.jayked.tvhome/com.jayked.tvhome.HomeButtonService

enabled_services() {
  local value
  value=$(adb shell settings get secure enabled_accessibility_services | tr -d '\r')
  [[ "$value" == "null" ]] && value=""
  echo "$value"
}

case "${1:-}" in
  on)
    adb shell pm list packages com.jayked.tvhome | grep -q com.jayked.tvhome || { echo "Install TV Home first (scripts/install.sh)."; exit 1; }
    current=$(enabled_services)
    if [[ ":$current:" != *":$SERVICE:"* ]]; then
      adb shell settings put secure enabled_accessibility_services "${current:+$current:}$SERVICE"
    fi
    adb shell settings put secure accessibility_enabled 1
    echo "Home button now opens TV Home. Undo with: scripts/takeover.sh off"
    ;;
  off)
    remaining=$(enabled_services | tr ':' '\n' | grep -vx "$SERVICE" | paste -sd: - || true)
    if [[ -n "$remaining" ]]; then
      adb shell settings put secure enabled_accessibility_services "$remaining"
    else
      adb shell settings delete secure enabled_accessibility_services
    fi
    echo "Home button opens Google TV again."
    ;;
  status)
    if [[ ":$(enabled_services):" == *":$SERVICE:"* ]]; then echo "on"; else echo "off"; fi
    ;;
  *)
    echo "Usage: $0 on|off|status" >&2
    exit 1
    ;;
esac
