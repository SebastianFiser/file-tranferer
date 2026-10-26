#!/usr/bin/env bash

set -e

#Locate android project folder
SCRIPT_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"

cd "$PROJECT_DIR"

echo "==> BUILDING DEBUG APK"
./gradlew assembleDebug

echo "==> CHECKING CONNECTED ANDROID DEVICES"
ADB="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"

if [[ ! -x "$ADB" ]]; then
    echo "ERROR: ADB NOT FOUND AT $ADB"
    exit 1
fi

mapfile -t DEVICES < <("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')

if (( ${#DEVICES[@]} == 0 )); then
    echo "ERROR: NO CONNECTED ANDROID DEVICES FOUND"
    echo "WARN: CONNECT YOUR DEVICE VIA USB AND TRY AGAIN"
    exit 1
fi

if (( ${#DEVICES[@]} > 1 )); then
    echo "ERROR: MULTIPLE CONNECTED ANDROID DEVICES FOUND"
    echo "WARN: PLEASE CONNECT ONLY ONE DEVICE AND TRY AGAIN"
    exit 1
fi

if [[ ! -f "$APK_PATH" ]]; then
    echo "ERROR: APK NOT FOUND AT $APK_PATH"
    exit 1
fi


echo "==> INSTALLING APK ON DEVICE ${DEVICES[0]}"
"$ADB" -s "${DEVICES[0]}" install -r "$APK_PATH" 

echo "==> APK INSTALLED SUCCESSFULLY"