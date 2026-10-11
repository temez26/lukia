# Testing

## Running tests

Everything the JVM can run, including the Compose UI suites. Run this before
opening a pull request, since CI does not:

```bash
./gradlew :shared:jvmTest
```

What CI runs, to reproduce a CI failure:

```bash
./gradlew :shared:jvmCommonTest :shared:testAndroidHostTest
```

On a connected Android device or emulator:

```bash
./gradlew :shared:connectedAndroidDeviceTest
```

On macOS with an iOS Simulator:

```bash
./gradlew :shared:iosSimulatorArm64Test
```

## Source sets

| Source set | On CI | For |
| --- | --- | --- |
| `commonTest` | yes, on JVM, Android host and iOS | Anything that needs no platform |
| `androidHostTest` | yes | Android code that needs only a `Context` (Robolectric) |
| `iosTest` | yes | iOS implementations and cinterop code |
| `jvmTest` | compiled only | Compose UI suites, PDFBox, the JDBC driver, `java.io` |
| `androidDeviceTest` | no | What only a device can answer: keystore, `BitmapFactory`, pdfbox-android |

A test in `jvmTest` that imports nothing platform-specific belongs in
`commonTest`, where it runs on all three platforms.

CI also compiles every source set, so a platform test that no longer compiles
fails the build even though CI does not run it.

## Not tested

- **`IOSSecretStore`.** The simulator's test executable has no keychain. The
  same contract is tested on JVM and Android.
- **The app after R8.** CI proves the release build compiles, not that it runs.
  That is a manual step before a release: see
  [BUILD.md](BUILD.md#before-a-release).

## Coverage

```bash
./gradlew :shared:koverHtmlReport
```

Writes `shared/build/reports/kover/html/index.html`. It covers the `domain`,
`application` and `infrastructure` layers on JVM and Android host only.
`koverVerify`, part of `check`, fails below 80%.
