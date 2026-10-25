#!/usr/bin/env bash
set -e

# CleanTV Home - Universal Installer (macOS & Linux)
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "============================================================"
echo "         CleanTV Home - Universal Seamless Installer        "
echo "   Apple TV-style Clean Launcher & Bloat-Free TV Setup      "
echo "============================================================"
echo ""

# 1. Resolve ADB
ADB="$("$REPO_ROOT/tools/get-adb.sh")"
if [ ! -x "$ADB" ] && ! command -v adb >/dev/null 2>&1; then
  echo "Error: Could not find or download adb."
  exit 1
fi
if command -v adb >/dev/null 2>&1; then
  ADB="adb"
fi

# 2. TV Connection
if [ -z "$1" ]; then
  read -p "Enter your TV IP address [default: 192.168.0.159]: " TV_IP
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

# Check authorization
TIMEOUT=30
GRANTED=false
echo "Checking TV authorization..."
for i in $(seq 1 $TIMEOUT); do
  STATE="$("$ADB" -s "$SERIAL" get-state 2>&1 || true)"
  if [ "$STATE" = "device" ]; then
    GRANTED=true
    break
  fi
  if [[ "$STATE" == *"unauthorized"* ]]; then
    echo "LOOK AT YOUR TV SCREEN! Check 'Always allow from this computer' and press OK on your TV remote."
  fi
  sleep 1
done

if [ "$GRANTED" = false ]; then
  echo "Error: TV is not authorized or not reachable at $SERIAL."
  exit 1
fi

echo "TV Connected successfully!"

# 3. Backup
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP_DIR="$REPO_ROOT/backups/backup-$TIMESTAMP"
mkdir -p "$BACKUP_DIR"
echo "Creating safety backup at $BACKUP_DIR..."
"$ADB" -s "$SERIAL" shell pm list packages > "$BACKUP_DIR/packages-all.txt" 2>/dev/null || true
"$ADB" -s "$SERIAL" shell pm list packages -d > "$BACKUP_DIR/packages-disabled.txt" 2>/dev/null || true
"$ADB" -s "$SERIAL" shell getprop > "$BACKUP_DIR/getprop.txt" 2>/dev/null || true

# 4. Install Launcher
APK_PATH="$REPO_ROOT/apks/clean-tv-launcher-latest.apk"
echo "Installing clean launcher ($APK_PATH)..."
"$ADB" -s "$SERIAL" install -r "$APK_PATH"

echo "Setting Clean TV as Default Home..."
"$ADB" -s "$SERIAL" shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n com.gathin.tvhome/.MainActivity || true
"$ADB" -s "$SERIAL" shell cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity || true

# Disable stock launcher
for STOCK in com.google.android.tvlauncher com.changhong.system.launcher.v2 com.mitv.tvhome; do
  if "$ADB" -s "$SERIAL" shell pm list packages "$STOCK" | grep -q "$STOCK"; then
    echo "Disabling stock launcher: $STOCK"
    "$ADB" -s "$SERIAL" shell pm disable-user --user 0 "$STOCK" || true
  fi
done

"$ADB" -s "$SERIAL" shell cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity || true
"$ADB" -s "$SERIAL" shell input keyevent HOME

# 5. Debloat
PRESET="$REPO_ROOT/presets/realme-changhong.txt"
if [ -f "$PRESET" ]; then
  echo "Applying safe debloat..."
  while IFS= read -r line || [ -n "$line" ]; do
    clean_line="$(echo "$line" | sed 's/#.*//' | xargs)"
    if [ -n "$clean_line" ]; then
      if "$ADB" -s "$SERIAL" shell pm list packages "$clean_line" | grep -q "$clean_line"; then
        echo "Disabling: $clean_line"
        "$ADB" -s "$SERIAL" shell pm disable-user --user 0 "$clean_line" || true
      fi
    fi
  done < "$PRESET"
fi

echo ""
echo "============================================================"
echo "                 SETUP COMPLETE!                            "
echo "============================================================"
echo "Clean TV Launcher is now running on your TV."
echo "You can restore stock anytime with ./restore.sh"
echo "============================================================"
