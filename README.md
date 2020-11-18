# DetoxDroid
Digital Detoxing As Your New Default

## What it is about

Usually, "Digital Detoxing" apps in Android are about opting-in for a contract to not use our phones for the next X minutes. Or that we will not use the app Y for more than Z minutes. These contracts are often reinforced with financial incentives, i.e. if we fail we will have to pay a fee of a few dollars to the developers.

This app follows a different, more technical approach (where you can still use other apps complementarily). It does not require you to choose a time and place when you want to "detox", or punish you if you fail. Instead, DetoxDroid aims to enable you to use your phone rather than letting your phone use you. This is to be achieved by stripping away all the attention-grabbing features of your phone.

## Features

### 1. Grayscale your screen
According to former Google design ethicist Tristan Harris, founder of the Center for Humane Technology, going grayscale removes positive reinforcements and dampens compulsive smartphone use.

#### Make Exceptions
While a grayscaled screen has been proven to significantly reduce compulsive smartphone use, there are obvious downsides. For example, you do want to see colors on your screen when you take a photo. Hence, you can allow specific apps to show colors, while the rest of your phone stays gray.

### 2. Automatically enter the "Do Not Disturb" mode
Notifications are the number 1 way for apps to draw our attention. Any app on our phones competes for our attention, and even games will remind us to play them if we have not opened them for a while. Reclaim your time by turning them off with the "Do Not Disturb" mode.

### 3. Opt-out > Opt-in
Rather than needing to opt-in for this mode, you are encouraged to deliberately to pause the app, allowing colors and notifications. If the pause is over, DetoxDroid automatically comes back into life. Let this become your new default rather than having to repeatedly decide for it.

## Installation
1. [Enable developer mode and USB debugging](https://www.youtube.com/watch?v=0usgePpr8_Y):
    1. Go to Android Settings → About Phone
    2. Look for the build number option
    3. Touch it multiple times until developer mode is enabled
    4. Go to Android Settings → Developer Options
    5. Look for USB debugging and enable it
    6. Connect device with your computer
    7. A prompt should ask you whether you want to allow debugging by your computer: Confirm
2. Run the installation script for your OS:
    - Linux: [install_linux.sh](https://raw.githubusercontent.com/flxapps/DetoxDroid/master/install/install_linux.sh)

If the installation script does not work or you do not trust it, follow these steps:
1. On your phone, download and install [the latest APK](https://raw.githubusercontent.com/flxapps/DetoxDroid/master/install/release/app-release.apk) (or compile it yourself using the git repository)
2. On your computer or laptop, download and unzip the [Android SDK Platform Tools for your OS](https://developer.android.com/studio/releases/platform-tools)
3. Go the the platform-tools/ folder and open a terminal
4. Grant the [WRITE_SECURE_SETTINGS](https://developer.android.com/reference/android/Manifest.permission#WRITE_SECURE_SETTINGS) permission to the app by running
```
adb shell pm grant com.flx_apps.digitaldetox android.permission.WRITE_SECURE_SETTINGS
```
This permission is mandatory for the grayscale feature to work and can only be granted from your computer via adb.

## Support
If you like the project, feel free to support further development.
- [Submit feature suggestions and bug reports](https://github.com/flxapps/DetoxDroid/issues/new)
- [Donate via PayPal](https://www.paypal.com/donate/?cmd=_s-xclick&hosted_button_id=K6T2HPXE7HQBG)
- [Become a patron on LiberaPay](https://liberapay.com/DetoxDroid/donate)
