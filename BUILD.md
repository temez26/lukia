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

### Signing the Mac build

**A DMG built without the variables below will not open on any Mac but the one
that built it.** Gatekeeper refuses a downloaded application with no Developer ID
signature, and since macOS Sequoia the reader cannot right-click past the
refusal - the app is simply blocked. So this is not a hardening step that can be
left for later: it is what makes the Mac build something anybody else can run.

The build works without them, deliberately. Absent, `packageDmg` produces an
unsigned DMG, which is fine for trying a change on your own machine and is the
same arrangement `ANDROID_KEYSTORE_PATH` makes for Android - see
`androidApp/build.gradle.kts`. Nobody without a certificate is stopped from
building.

| Variable | Holds |
| --- | --- |
| `MACOS_SIGNING_IDENTITY` | The certificate's common name, e.g. `Developer ID Application: Your Name (TEAMID)`. Its presence is what turns signing on. |
| `MACOS_SIGNING_KEYCHAIN` | Optional. The keychain to look in, for a CI runner that imported the certificate into one of its own. Your login keychain is found without it. |
| `MACOS_NOTARIZATION_APPLE_ID` | The Apple ID the certificate belongs to. |
| `MACOS_NOTARIZATION_PASSWORD` | An **app-specific password**, not the account password. Made at [appleid.apple.com](https://appleid.apple.com) under Sign-In and Security. |
| `MACOS_NOTARIZATION_TEAM_ID` | The ten-character team ID, the same one `TEAM_ID` holds for iOS. |

Signing is what lets the DMG open at all; notarization is what stops macOS
warning about it the first time. The first three are enough to sign:

```bash
MACOS_SIGNING_IDENTITY="Developer ID Application: Your Name (TEAMID)" ./gradlew :desktopApp:packageDmg
```

Notarizing asks Apple's service, which takes minutes rather than seconds, so it
is a separate task rather than part of packaging:

```bash
./gradlew :desktopApp:notarizeDmg
```

Nothing extra is needed for the hardened runtime that notarization requires. A
JVM has to write executable memory, and the Compose plugin's own default
entitlements already allow exactly that - so there is no entitlements file in
this repository, and adding one would only be a way to get it wrong.

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

## Before a release: does the shrunk app run?

`:androidApp:assembleRelease` proves R8 *compiled* the app. It cannot prove the
app still works, and that is the half that matters: every rule in
[`androidApp/proguard-rules.pro`](androidApp/proguard-rules.pro) covers a lookup
R8 cannot see - a class named in a string, a service file, a member reached
reflectively - and a missing one fails at run time, on a reader's device, as a
blank page or a translation that never arrives. Nothing in the build catches it.

So this is a step before a release rather than a check on a pull request, and it
is the only thing standing behind the keep rules.

Run it against `benchmarkRelease`, not `release`. It is the same R8 run - the
baseline profile plugin derives it from the release build type, and
`minifyBenchmarkReleaseWithR8` is in its task graph - but it carries a debug
signature, so it installs without the release keystore:

```bash
./gradlew :androidApp:installBenchmarkRelease
```

Then, on the device, and in this order:

| Do this | And it proves |
| --- | --- |
| Open the app and reach the library | Koin's graph, the SQLDelight schema and the Compose runtime all survived |
| Change the palette in Settings, then force-stop and reopen | the kotlinx.serialization rules. Settings are stored as JSON and the generated serializers are reached through a companion R8 cannot see used |
| Open a PDF as pages, then re-set the same one as text | the pdfbox-android rules, which are the broadest in the file. Filters, fonts and `COSName` constants are all resolved by name |
| Open a password-protected PDF | Bouncy Castle, which pdfbox-android reaches reflectively for encrypted documents |
| Open an EPUB and a DOCX | the ZIP and XML readers, and the fonts an EPUB carries |
| Translate a passage | the Ktor rules. The engine is found through `META-INF/services`, and without its container kept, every translation fails with *Failed to find HTTP client engine implementation* |
| Read Settings > About | the version is the one being released |

Two of those rows are already automated against the same shrunk build:

```bash
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest \
    -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
```

`PageTurnBenchmark` opens a real PDF and a real reflowed book out of a real
library, so it walks the pdfbox path and the reader on the minified app. It is
described as a performance tool and is also this. It needs a device that already
holds the books it names, which is why it cannot run on a fresh emulator and is
not part of `connectedCheck` - and why the rest of the table is a person's job.

**Why none of this is in CI.** The rows that matter need what CI has not got: a
library with real books in it, and translation credentials. A Gradle Managed
Device would answer the first row and nothing below it, at the cost of an
emulator image and several minutes on every run - which would buy the weakest
row in the table and leave the pdfbox rules exactly as unproven as they are now.
When somebody wants to narrow those rules, this is the run that earns it.

## Build locations at a glance

| Platform | Build | Location                                      |
| -------- | ----- | --------------------------------------------- |
| Android  | APK   | `androidApp/build/outputs/apk/debug/`         |
| macOS    | DMG   | `desktopApp/build/compose/binaries/main/dmg/` |
| Windows  | MSI   | `desktopApp/build/compose/binaries/main/msi/` |
| iOS      | App   | `iosApp/build/` or Xcode Derived Data         |
