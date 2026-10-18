# Moonlight Android

## Moonlight Desktop customization

This unofficial v12.2-based desktop client uses the separate Android ID
`org.moonlightdesktop.client.unofficial` and keeps the original application installed.
Tap a paired host to connect to its remembered desktop, preferring a unique
Privacy Desktop entry. Long-press the host to select another default entry or manage
sessions. A different running session is never ended automatically.

Direct touch is the default: tap for left click, hold for a complete right click,
or move beyond the touch slop to drag. A hold sends no preliminary left click.
The initial video profile is 1920x1080 at 30 FPS; settings remain configurable.

Build using JDK21, Android SDK37.2 and NDK29.0.14206865:
`gradlew.bat :app:testNonRootDebugUnitTest :app:assembleNonRootRelease`.
Release signing is optional and reads ignored `desktop-signing.properties` keys
`storeFile`, `storePassword`, `keyAlias`, and `keyPassword`. Keep the private key
to allow future in-place updates. Signing material must never be committed.

Before publishing changes, review the complete outgoing Git history for credentials
and identifying data. Keep host names, host addresses, device identifiers, pairing
state, diagnostic captures, local SDK paths and signing files outside Git. This
repository contains no preconfigured host or pairing credentials. Each installation
discovers or adds its own host and completes normal Sunshine pairing.

[![AppVeyor Build Status](https://ci.appveyor.com/api/projects/status/232a8tadrrn8jv0k/branch/master?svg=true)](https://ci.appveyor.com/project/cgutman/moonlight-android/branch/master)
[![Translation Status](https://hosted.weblate.org/widgets/moonlight/-/moonlight-android/svg-badge.svg)](https://hosted.weblate.org/projects/moonlight/moonlight-android/)

[Moonlight for Android](https://moonlight-stream.org) is an open source client for NVIDIA GameStream and [Sunshine](https://github.com/LizardByte/Sunshine).

Moonlight for Android will allow you to stream your full collection of games from your Windows PC to your Android device,
whether in your own home or over the internet.

Moonlight also has a [PC client](https://github.com/moonlight-stream/moonlight-qt) and [iOS/tvOS client](https://github.com/moonlight-stream/moonlight-ios).

You can follow development on our [Discord server](https://moonlight-stream.org/discord) and help translate Moonlight into your language on [Weblate](https://hosted.weblate.org/projects/moonlight/moonlight-android/).

## Downloads
* [Google Play Store](https://play.google.com/store/apps/details?id=com.limelight)
* [Amazon App Store](https://www.amazon.com/gp/product/B00JK4MFN2)
* [F-Droid](https://f-droid.org/packages/com.limelight)
* [APK](https://github.com/moonlight-stream/moonlight-android/releases)

## Building
* Install Android Studio and the Android NDK
* Run ‘git submodule update --init --recursive’ from within moonlight-android/
* In moonlight-android/, create a file called ‘local.properties’. Add an ‘ndk.dir=’ property to the local.properties file and set it equal to your NDK directory.
* Build the APK using Android Studio or gradle

## Authors

* [Cameron Gutman](https://github.com/cgutman)  
* [Diego Waxemberg](https://github.com/dwaxemberg)  
* [Aaron Neyer](https://github.com/Aaronneyer)  
* [Andrew Hennessy](https://github.com/yetanothername)

Moonlight is the work of students at [Case Western](http://case.edu) and was
started as a project at [MHacks](http://mhacks.org).
