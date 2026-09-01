# Lukia — Local Build Commands

## Android

```bash
./gradlew :androidApp:assembleDebug
```

## Desktop

### macOS

```bash
./gradlew :desktopApp:packageDmg
```

### Windows

```bash
./gradlew :desktopApp:packageMsi
```

## iOS

Requires macOS + Xcode.

```bash
./gradlew :shared:embedAndSignAppleFrameworkForXcode
```

### Signing

Building to a real iPhone or iPad needs an Apple developer team, which is
personal to whoever is building and so is not in the repository. Xcode fails
with *Signing for "iosApp" requires a development team* until it has one.

Write yours into `iosApp/Configuration/Local.xcconfig`, which git ignores and
`Config.xcconfig` picks up if it is there:

```bash
echo 'TEAM_ID=YOURTEAMID' > iosApp/Configuration/Local.xcconfig
```

The ten-character ID is in Xcode under Settings > Accounts, or on
[developer.apple.com](https://developer.apple.com/account) under Membership
details. A free Apple ID is enough for building to your own device; signing is
automatic from there, and Xcode makes the provisioning profile itself.

The Simulator needs none of this.

## Build Android + Desktop

### macOS

```bash
./gradlew :androidApp:assembleDebug :desktopApp:packageDmg
```

### Windows

```bash
./gradlew :androidApp:assembleDebug :desktopApp:packageMsi
```

## Android baseline profile

The release build ships a list of the methods a launch actually runs, so the
installer compiles them ahead of time instead of leaving the JIT to catch up
while somebody is watching. It lives in
`androidApp/src/release/generated/baselineProfiles/` and is generated rather
than written:

```bash
ANDROID_SERIAL=<emulator-id> ./gradlew :androidApp:generateBaselineProfile
```

On an **emulator**, not a phone. Generating installs a release-signed build of
the app, and on a device that already carries a debug one that means uninstalling
it — which takes the library with it.

Worth regenerating when the app's opening changes shape. A stale profile is not
wrong, only less useful.

## Build locations at a glance

| Platform | Build | Location                                      |
| -------- | ----- | --------------------------------------------- |
| Android  | APK   | `androidApp/build/outputs/apk/debug/`         |
| macOS    | DMG   | `desktopApp/build/compose/binaries/main/dmg/` |
| Windows  | MSI   | `desktopApp/build/compose/binaries/main/msi/` |
| iOS      | App   | `iosApp/build/` or Xcode Derived Data         |
