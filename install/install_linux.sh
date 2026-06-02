#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$SCRIPT_DIR"

fail() {
    echo "Error: $1"
    exit 1
}

fetch_text() {
    url="$1"
    if command -v curl >/dev/null 2>&1; then
        curl -fsSL "$url"
    elif command -v wget >/dev/null 2>&1; then
        wget -qO- "$url"
    else
        fail "Neither curl nor wget is installed. Please install one of them and retry."
    fi
}

download_file() {
    url="$1"
    output="$2"
    if command -v curl >/dev/null 2>&1; then
        curl -fL --retry 3 -o "$output" "$url"
    elif command -v wget >/dev/null 2>&1; then
        wget -O "$output" "$url"
    else
        fail "Neither curl nor wget is installed. Please install one of them and retry."
    fi
}

if [ ! -f "platform-tools/adb" ]; then
    echo "Downloading and unzipping Android Platform Tools. Please wait..."
    if ! command -v unzip >/dev/null 2>&1; then
        fail "unzip is required but not installed."
    fi
    download_file "https://dl.google.com/android/repository/platform-tools-latest-linux.zip" "platform-tools-latest-linux.zip"
    unzip -o platform-tools-latest-linux.zip >/dev/null
fi

if [ ! -x "platform-tools/adb" ]; then
    fail "platform-tools/adb was not found after setup."
fi

APK_URL=$(
    fetch_text "https://api.github.com/repos/flxapps/DetoxDroid/releases/latest" \
        | grep -Eo '"browser_download_url":[[:space:]]*"[^"]*app-[0-9]+(\.[0-9]+)*\.apk"' \
        | head -n 1 \
        | sed -E 's/^"browser_download_url":[[:space:]]*"([^"]+)"$/\1/'
)

if [ -z "$APK_URL" ]; then
    fail "Could not resolve APK download URL from latest release."
fi

APK_FILE=$(basename "$APK_URL")
if [ ! -f "$APK_FILE" ]; then
    echo "Downloading latest DetoxDroid APK ($APK_FILE)..."
    download_file "$APK_URL" "$APK_FILE"
else
    echo "Using existing APK: $APK_FILE"
fi

echo "Installing Detox Droid on your device"
platform-tools/adb install -r -t "$APK_FILE"

echo "Granting Permissions"
platform-tools/adb shell pm grant com.flx_apps.digitaldetox android.permission.WRITE_SECURE_SETTINGS
platform-tools/adb shell "dpm set-device-owner com.flx_apps.digitaldetox/.system_integration.DetoxDroidDeviceAdminReceiver"

echo "Starting App"
platform-tools/adb shell monkey -p com.flx_apps.digitaldetox 1

echo "Done."
