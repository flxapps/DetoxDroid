@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"

if not exist "platform-tools\adb.exe" (
    echo Downloading and unzipping Android Platform Tools. Please wait...
    powershell.exe -nologo -noprofile -command "(New-Object Net.WebClient).DownloadFile('https://dl.google.com/android/repository/platform-tools-latest-windows.zip', 'platform-tools-latest-windows.zip')"
    powershell.exe -nologo -noprofile -command "& { $shell = New-Object -COM Shell.Application; $target = $shell.NameSpace('%cd%'); $zip = $shell.NameSpace('%cd%\platform-tools-latest-windows.zip'); $target.CopyHere($zip.Items(), 16); }"
)

if not exist "platform-tools\adb.exe" (
    echo Error: platform-tools\adb.exe was not found after setup.
    exit /b 1
)

set "APK_URL="
set "APK_FILE="
for /f "usebackq tokens=1,2 delims=|" %%i in (`powershell.exe -nologo -noprofile -command "$asset = (Invoke-RestMethod 'https://api.github.com/repos/flxapps/DetoxDroid/releases/latest').assets | Where-Object { $_.name -match '^app-[0-9]+(\.[0-9]+)*\.apk$' } | Select-Object -First 1; if ($null -ne $asset) { Write-Output ($asset.browser_download_url + '|' + $asset.name) }"`) do (
    set "APK_URL=%%i"
    set "APK_FILE=%%j"
)

if "!APK_URL!"=="" (
    echo Could not resolve APK download URL from latest release.
    exit /b 1
)
if "!APK_FILE!"=="" (
    echo Could not resolve APK file name from latest release.
    exit /b 1
)

if not exist "!APK_FILE!" (
    echo Downloading latest DetoxDroid APK (!APK_FILE!)...
    powershell.exe -nologo -noprofile -command "(New-Object Net.WebClient).DownloadFile('!APK_URL!', '!APK_FILE!')"
) else (
    echo Using existing APK: !APK_FILE!
)

echo Starting ADB
echo If your device asks you to allow USB Debugging, press "Accept".
%cd%\platform-tools\adb.exe start-server

echo Installing DetoxDroid on your device...
%cd%\platform-tools\adb.exe install -r -t "!APK_FILE!"

echo Granting Permissions
%cd%\platform-tools\adb.exe shell pm grant com.flx_apps.digitaldetox android.permission.WRITE_SECURE_SETTINGS
%cd%\platform-tools\adb.exe shell "dpm set-device-owner com.flx_apps.digitaldetox/.system_integration.DetoxDroidDeviceAdminReceiver"

echo Starting App
%cd%\platform-tools\adb.exe shell monkey -p com.flx_apps.digitaldetox 1

echo Done.
pause
