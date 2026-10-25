#!/usr/bin/env bash
set -e

# CleanTV Home - Ad-Blocking Suite for Android TV (macOS & Linux)
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "============================================================"
echo "         CleanTV Home - Universal Ad-Blocking Suite         "
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

echo ""
echo "Select Ad-Blocking Option:"
echo "  [1] Enable System-Wide Ad-Blocking DNS (AdGuard DNS)"
echo "  [2] Enable System-Wide Privacy DNS (Control D)"
echo "  [3] Install CleanTV Ad-Block Dashboard Card"
echo "  [4] Disable Private DNS"
echo "  [Q] Exit"
read -r -p "Enter choice [1-4 or Q]: " CHOICE

case "$CHOICE" in
  1)
    echo "Setting Private DNS to dns.adguard-dns.com..."
    "$ADB" -s "$SERIAL" shell settings put global private_dns_mode hostname
    "$ADB" -s "$SERIAL" shell settings put global private_dns_specifier dns.adguard-dns.com
    echo "AdGuard DNS enabled successfully!"
    ;;
  2)
    echo "Setting Private DNS to p2.freedns.controld.com..."
    "$ADB" -s "$SERIAL" shell settings put global private_dns_mode hostname
    "$ADB" -s "$SERIAL" shell settings put global private_dns_specifier p2.freedns.controld.com
    echo "Control D DNS enabled successfully!"
    ;;
  3)
    TILE_APK="$REPO_ROOT/apks/addons/ad-blocking.apk"
    if [ -f "$TILE_APK" ]; then
      echo "Installing Ad-Blocking Tile..."
      "$ADB" -s "$SERIAL" install -r "$TILE_APK"
      echo "Tile installed!"
    else
      echo "Error: Tile APK not found."
    fi
    ;;
  4)
    echo "Disabling Private DNS..."
    "$ADB" -s "$SERIAL" shell settings put global private_dns_mode off
    "$ADB" -s "$SERIAL" shell settings delete global private_dns_specifier
    echo "Private DNS disabled."
    ;;
  *)
    exit 0
    ;;
esac
