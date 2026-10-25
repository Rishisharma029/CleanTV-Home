#!/usr/bin/env bash
set -e

# CleanTV Home - Universal Restore Script (macOS & Linux)
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "============================================================"
echo "          CleanTV Home - Universal Factory Restore          "
echo "============================================================"

# Resolve ADB
ADB="$("$REPO_ROOT/tools/get-adb.sh")"
if command -v adb >/dev/null 2>&1; then
  ADB="adb"
fi

if [ -z "$1" ]; then
  read -r -p "Enter your TV IP address [default: 192.168.0.159]: " TV_IP
  TV_IP="${TV_IP:-192.168.0.159}"
else
  TV_IP="$1"
fi

case "$TV_IP" in
  *:*) SERIAL="$TV_IP" ;;
  *) SERIAL="$TV_IP:5555" ;;
esac

echo "Connecting to TV at $SERIAL..."
"$ADB" connect "$SERIAL"

# Re-enable packages
PACKAGES=(
  tv.anoki.acr.controller
  com.homwee.aipont
  com.google.android.tvrecommendations
  android.autoinstalls.config.homwee
  com.homwee.store
  com.homwee.help
  com.google.android.feedback
  com.google.android.play.games
  com.google.android.syncadapters.calendar
  com.tcl.tvmanager
  com.tcl.adservice
  com.xiaomi.mitv.upgrade
  com.xiaomi.mitv.advertise
  com.miui.analytics
)

echo "Re-enabling disabled bloat and telemetry packages..."
for PKG in "${PACKAGES[@]}"; do
  "$ADB" -s "$SERIAL" shell pm enable --user 0 "$PKG" 2>/dev/null || true
done

echo "Re-enabling stock TV launchers..."
for STOCK in com.google.android.tvlauncher com.changhong.system.launcher.v2 com.mitv.tvhome; do
  "$ADB" -s "$SERIAL" shell pm enable --user 0 "$STOCK" 2>/dev/null || true
done

echo "Restoring default Android TV Home..."
"$ADB" -s "$SERIAL" shell cmd package set-home-activity --user 0 com.google.android.tvlauncher/.MainActivity 2>/dev/null || true
"$ADB" -s "$SERIAL" shell input keyevent HOME

echo ""
echo "============================================================"
echo "             RESTORE COMPLETED SUCCESSFULLY                 "
echo "============================================================"
echo "Stock launcher and apps have been re-enabled."
echo "============================================================"
