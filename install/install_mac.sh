#!/bin/sh

if [ ! -f "platform-tools/adb" ]; then
    echo "Downloading and unzipping Android Platform Tools. Please wait..."
    wget -O platform-tools-latest-darwin.zip https://dl.google.com/android/repository/platform-tools-latest-darwin.zip
    unzip platform-tools-latest-darwin.zip 
fi

if [ ! -f "detoxdroid-latest.apk" ]; then
    echo "Downloading latest DetoxDroid APK..."
    wget -O detoxdroid-latest.apk https://github.com/flxapps/DetoxDroid/releases/latest/download/app-release.apk
fi

echo "Installing DetoxDroid on your device"
platform-tools/adb install -r -t detoxdroid-latest.apk

echo "Granting Permissions"
platform-tools/adb shell pm grant com.flx_apps.digitaldetox android.permission.WRITE_SECURE_SETTINGS
platform-tools/adb shell "dpm set-device-owner com.flx_apps.digitaldetox/.system_integration.DetoxDroidDeviceAdminReceiver"

echo "Starting App"
platform-tools/adb shell monkey -p com.flx_apps.digitaldetox 1

echo "Done."
