#!/usr/bin/env bash
# Auto-detects or downloads standalone Google ADB Platform-Tools for Linux / macOS

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if command -v adb >/dev/null 2>&1; then
  command -v adb
  exit 0
fi

if [ -f "$REPO_ROOT/bin/platform-tools/adb" ]; then
  echo "$REPO_ROOT/bin/platform-tools/adb"
  exit 0
fi

echo "ADB not found. Downloading Android Platform-Tools..." >&2
mkdir -p "$REPO_ROOT/bin"

OS="$(uname -s)"
if [ "$OS" = "Darwin" ]; then
  URL="https://dl.google.com/android/repository/platform-tools-latest-darwin.zip"
else
  URL="https://dl.google.com/android/repository/platform-tools-latest-linux.zip"
fi

TMP_ZIP="$REPO_ROOT/bin/platform-tools.zip"
curl -sSL "$URL" -o "$TMP_ZIP"
unzip -q -o "$TMP_ZIP" -d "$REPO_ROOT/bin"
rm -f "$TMP_ZIP"
chmod +x "$REPO_ROOT/bin/platform-tools/adb"

if [ -f "$REPO_ROOT/bin/platform-tools/adb" ]; then
  echo "$REPO_ROOT/bin/platform-tools/adb"
else
  echo "Failed to install ADB" >&2
  exit 1
fi
