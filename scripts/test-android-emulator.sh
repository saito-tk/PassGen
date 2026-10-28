#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
serial="${1:-}"
case "$serial" in emulator-*) ;; *) echo 'Specify an emulator serial, for example emulator-5580.' >&2; exit 2 ;; esac
case "${serial#emulator-}" in ''|*[!0-9]*) echo 'Invalid emulator serial.' >&2; exit 2 ;; esac
sdk="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
adb="$sdk/platform-tools/adb"
"$adb" -s "$serial" get-state
./scripts/gradle-local.sh :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest
"$adb" -s "$serial" install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
"$adb" -s "$serial" install -r androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk
mkdir -p .local-tools
test_log=".local-tools/test-android-$serial.log"
"$adb" -s "$serial" shell am instrument -w -r jp.passgen.app.test/androidx.test.runner.AndroidJUnitRunner > "$test_log" 2>&1
cat "$test_log"
grep -q '^OK (' "$test_log"
