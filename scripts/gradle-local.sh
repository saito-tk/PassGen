#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
if [ -z "${JAVA_HOME:-}" ] && [ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]; then
    JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    export JAVA_HOME
fi
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$PWD/.local-tools/gradle}"
export KONAN_DATA_DIR="${KONAN_DATA_DIR:-$PWD/.local-tools/konan}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export ANDROID_USER_HOME="$PWD/.local-tools/android"
exec ./gradlew "$@"
