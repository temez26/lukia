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
