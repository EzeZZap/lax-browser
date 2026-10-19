#!/usr/bin/env bash
# Installs a Lax APK on the running emulator, starts it (normally and with a link from another
# app), and fails if any Lax process crashes or Lax stops running. Release builds go through R8,
# so this catches missing keep rules before an APK is published.
#
# Usage: smoke-test.sh path/to/app-x86_64-release.apk
set -euo pipefail

apk="$1"
package="com.javzzap.laxbrowser"
wait_seconds=20

adb uninstall "$package" >/dev/null 2>&1 || true
adb install -r "$apk"
adb logcat -c
adb logcat -b crash -c || true

check() {
  local step="$1"
  sleep "$wait_seconds"
  local crashes
  crashes="$(adb logcat -d -b crash | grep -A 40 "Process: $package" || true)"
  local pid
  pid="$(adb shell pidof "$package" | tr -d '\r' || true)"
  if [ -n "$crashes" ] || [ -z "$pid" ]; then
    echo "Lax crashed or stopped after: $step" >&2
    echo "$crashes" >&2
    echo "--- recent log ---" >&2
    adb logcat -d -t 400 >&2 || true
    exit 1
  fi
  echo "OK: $step (pid $pid)"
}

adb shell am start -W -n "$package/.MainActivity"
check "launch"

adb shell am start -W -a android.intent.action.VIEW -d "https://example.com/" "$package"
check "opening a link from another app"

echo "Lax started and kept running."
