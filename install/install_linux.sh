#!/bin/sh

if [ ! -f "platform-tools/adb" ]; then
    echo "Downloading Android Platform Tools"
    wget https://dl.google.com/android/repository/platform-tools-latest-linux.zip
    unzip platform-tools-latest-linux.zip 
fi

if [ ! -f "detoxdroid_latest.apk" ]; then
    echo "Download latest DetoxDroid APK"
    wget https://raw.githubusercontent.com/flxapps/DetoxDroid/master/install/app-release.apk
fi

echo "Installing Detox Droid on your device"
platform-tools/adb install app-release.apk

echo "Granting Permissions"
platform-tools/adb shell pm grant com.flx_apps.digitaldetox android.permission.WRITE_SECURE_SETTINGS
platform-tools/adb shell pm grant com.flx_apps.digitaldetox android.permission.ACCESS_NOTIFICATION_POLICY

echo "Starting App"
platform-tools/adb shell monkey -p com.flx_apps.digitaldetox 1

echo "Done."
