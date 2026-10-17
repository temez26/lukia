# Building

## Android

```bash
./gradlew :androidApp:assembleDebug
```

Output: `androidApp/build/outputs/apk/debug/`

## Desktop

```bash
./gradlew :desktopApp:packageMsi
```

```bash
./gradlew :desktopApp:packageDmg
```

```bash
./gradlew :desktopApp:packageDeb
```

Each package builds only on its own operating system. Output:
`desktopApp/build/compose/binaries/main/`

### Signing the Mac build

An unsigned DMG opens only on the Mac that built it. To sign, set:

| Variable | Value |
| --- | --- |
| `MACOS_SIGNING_IDENTITY` | Certificate name, e.g. `Developer ID Application: Your Name (TEAMID)`. Setting it turns signing on. |
| `MACOS_SIGNING_KEYCHAIN` | Optional. Keychain to search, for CI. |
| `MACOS_NOTARIZATION_APPLE_ID` | Apple ID the certificate belongs to |
| `MACOS_NOTARIZATION_PASSWORD` | An app-specific password, not the account password |
| `MACOS_NOTARIZATION_TEAM_ID` | Ten-character team ID |

Notarizing is a separate task, since it waits on Apple:

```bash
./gradlew :desktopApp:notarizeDmg
```

## iOS

Requires macOS and Xcode. Open `iosApp` in Xcode, or:

```bash
./gradlew :shared:embedAndSignAppleFrameworkForXcode
```

To run on a device, put your team ID in a git-ignored file. A free Apple ID is
enough; the Simulator needs none of this.

```bash
echo 'TEAM_ID=YOURTEAMID' > iosApp/Configuration/Local.xcconfig
```

## Android release signing

The release build is signed when these are set, and unsigned otherwise:

| Variable | Value |
| --- | --- |
| `ANDROID_KEYSTORE_PATH` | Path to the keystore |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Key alias |
| `ANDROID_KEY_PASSWORD` | Key password |

The release workflow reads the same values from repository secrets, with the
keystore base64-encoded as `ANDROID_KEYSTORE_BASE64`.

## Android baseline profile

Regenerate when the app's startup changes. Use an emulator: it installs a
release-signed build, which replaces any debug build and its library.

```bash
ANDROID_SERIAL=<emulator-id> ./gradlew :androidApp:generateBaselineProfile
```

## Releasing

1. Set the version in `gradle.properties` (`lukia.version`) and
   `iosApp/Configuration/Config.xcconfig` (`MARKETING_VERSION`).
2. Run the check below.
3. Tag `vX.Y.Z` on `main` and push the tag. The release workflow builds the
   signed Android bundle and APK and the desktop packages and drafts a GitHub
   release.

### Before a release

CI proves the shrunk Android build compiles, not that it runs. A missing R8
keep rule fails only at run time. Install the same R8 output with a debug
signature:

```bash
./gradlew :androidApp:installBenchmarkRelease
```

Then check, in order:

| Do this | It proves |
| --- | --- |
| Open the app and reach the library | Koin, the database schema, Compose |
| Change the palette, force-stop, reopen | kotlinx.serialization rules |
| Open a PDF as pages, then as text | pdfbox-android rules |
| Open an encrypted PDF with no open password (restrictions only) as text | PDFBox decryption |
| Open an EPUB and a DOCX | ZIP and XML readers, embedded fonts |
| Translate a passage | Ktor rules |
| Settings > About | The version is the one being released |
