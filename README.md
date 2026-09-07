# DetoxDroid

![GitHub Repo stars](https://img.shields.io/github/stars/flxapps/DetoxDroid)
[![GitHub release](https://img.shields.io/github/release/flxapps/DetoxDroid.svg)](https://github.com/flxapps/DetoxDroid/releases/) [![GitHub license](https://img.shields.io/github/license/flxapps/DetoxDroid.svg)](https://github.com/flxapps/DetoxDroid/blob/master/LICENSE) [![Maintenance](https://img.shields.io/badge/Maintained%3F-yes-green.svg)](https://github.com/flxapps/DetoxDroid/graphs/commit-activity) [![Ko-Fi](https://img.shields.io/static/v1?label=Buy%20me%20a%20coffee&message=3%20EUR&color=red)](https://ko-fi.com/flxapps/3) [![LiberaPay](https://img.shields.io/liberapay/receives/DetoxDroid)](https://liberapay.com/DetoxDroid) 

## Make digital detox your default

<img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="130" /><img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="130" /><img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" width="130" /><img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" width="130" /><img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" width="130" /><img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/6.png" width="130" />

## What DetoxDroid does

Most digital detox apps are opt-in: you start a timer, then try not to break it.

DetoxDroid is the opposite. It makes a calmer phone your default state:
- fewer colors
- fewer notifications
- fewer distracting apps

You can still take intentional pauses when you need them.

## Features

1. **Make Apps Boring**  
   Grayscale, dimmed and blurred. The apps you list lose their color, and on Android 12+ they go
   blurry as well. This works the minute you install the app. Real grayscale is an upgrade, see
   [Installation](#installation).
2. **Do Not Disturb by default**  
   Stays on for as long as DetoxDroid runs.
3. **Break Doom Scrolling**  
   Detects long scrolling sessions, interrupts them, and keeps that feed locked for a cooldown.
4. **Disable Apps**  
   Block an app behind a warning screen or deactivate it outright, launcher icon and notifications
   included. Optionally with a daily time budget.
5. **Pause Button**  
   Pause from Quick Settings, the assistant, or a long-pressed hardware button. Auto-resumes, with a
   minimum gap between pauses.
6. **Commitment Password**  
   Lock selected settings behind a passphrase.
7. **Minimal Launcher Widget**  
   A text-only launcher for the apps you actually meant to open.

## Installation

Install the APK from [Releases](https://github.com/flxapps/DetoxDroid/releases/) and grant the
accessibility service when the app asks. That is the whole setup, and every feature runs: where the
system color filter is out of reach, DetoxDroid draws its own screen filter that washes the color
out of distracting apps and blurs them (Android 12+, and the system drops the blur while battery
saver is on).

### Optional: real grayscale

Android only lets an app touch the system color filter if it holds `WRITE_SECURE_SETTINGS`, and
there is no permission dialog for it. Grant it once and the overlay is replaced by actual grayscale,
plus Extra Dim, which takes the screen below its normal minimum brightness. Grayscale, Extra Dim and
the screen filter are separate switches, so you can also run the overlay on top of the real thing.

Three ways to grant it:

**Shizuku, no computer involved.** The app ships a step-by-step wizard for this. It takes about five
minutes, and Shizuku has to be restarted after every reboot (the permission itself stays).

**Root.** One tap inside the app.

**From a computer:**

1. [Enable developer mode and USB debugging](https://www.youtube.com/watch?v=0usgePpr8_Y):
    1. Go to Android Settings → About Phone
    2. Tap Build Number repeatedly until developer mode is enabled
    3. Open Android Settings → Developer Options and enable USB debugging  
       (Some devices also require **USB debugging (Security Settings)** for `pm` commands. Some Xiaomi devices require a Mi account sign-in.)
    4. Connect your phone to your computer
2. [Download, unzip, and run the installation script](https://downgit.github.io/#/home?url=https://github.com/flxapps/DetoxDroid/tree/master/install) for your OS
    - If you are on Windows, you should be able to run `install_windows.bat` by simply double-clicking the file.
    - On Mac/Linux, run `bash install_<os-name>.sh`
    - If your phone asks to allow USB debugging for your computer, tap **Allow**
3. Optionally, disable USB debugging again.

If the script does not work, or you would rather see what it does, there are
[manual installation steps](https://github.com/flxapps/DetoxDroid/wiki/Manual-Installation) in the
wiki.

## Support
If DetoxDroid helps you, you can support development:
- [Submit feature suggestions and bug reports](https://github.com/flxapps/DetoxDroid/issues/new)
- [Buy me a coffee via Ko-Fi](https://ko-fi.com/flxapps)
- [Become a patron on LiberaPay](https://liberapay.com/DetoxDroid/donate)
- [Donate via PayPal](https://www.paypal.com/donate/?cmd=_s-xclick&hosted_button_id=K6T2HPXE7HQBG)

## Troubleshooting

### “App was denied access” on Pixel / Android 12+

On some Pixel and Android 12+ devices, you may see this dialog when DetoxDroid cannot access usage data:

<p float="left">
  <img src="https://github.com/user-attachments/assets/81c0d990-4b8e-40dd-9466-ac10c4833606" width="200" />
  <img src="https://github.com/user-attachments/assets/2b8fb76b-def0-4a80-a77b-b0b6af0a374d" width="200" />
</p>

Fix:

1. Open **Settings** → **Apps** → **DetoxDroid**.  
2. Tap the overflow menu (⋮) and select **Allow restricted settings**.  
3. Grant the permission.  

After this, DetoxDroid should read usage data normally.
