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

## Build locations at a glance

| Platform | Build | Location                                      |
| -------- | ----- | --------------------------------------------- |
| Android  | APK   | `androidApp/build/outputs/apk/debug/`         |
| macOS    | DMG   | `desktopApp/build/compose/binaries/main/dmg/` |
| Windows  | MSI   | `desktopApp/build/compose/binaries/main/msi/` |
| iOS      | App   | `iosApp/build/` or Xcode Derived Data         |
