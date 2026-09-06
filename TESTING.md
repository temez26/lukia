# CI Testing Info

- CI runs `:shared:jvmCommonTest` — the tests in `commonTest`, and only those.
- It also runs `:shared:testAndroidHostTest`, which runs the same common tests a
  second time against the Android target and the `androidHostTest` ones beside
  them. That is the only thing anywhere that runs common code compiled for
  Android, it wants no display and no emulator, and it takes about ten seconds.
- It also compiles `androidMain`, `iosMain`, `jvmMain` and every test source set,
  including the platform tests it does not run: a test that no longer compiles
  still fails the build.
- The rest of `jvmTest` is run on a local machine. It adds roughly 590 tests to
  the common ones and 30 seconds — nearly all of it the 40 Compose UI suites,
  which want a display.
- iOS is the exception: `:shared:iosSimulatorArm64Test` runs on CI and takes the
  `iosTest` files with it. The framework has to be built and linked either way,
  and running the common tests a second time against the iOS `actual`
  implementations is most of why the job is worth its minutes.
- Android device tests need a connected device or emulator, so they are local only.

# The source sets, and what belongs in each

| Source set | Runs on CI | For |
|---|---|---|
| `commonTest` | yes, three times over | Anything that does not need a platform. It is run on the JVM, against the Android target on the host, and in the iOS simulator — so a test here is worth three. |
| `androidHostTest` | yes | Android tests that want nothing of a device but a `Context` — a content provider's answers, a file under a cache directory. Robolectric answers the framework on the host. |
| `jvmTest` | compiled only | Compose UI suites, PDFBox, the SQLDelight JDBC driver, anything reading a file through `java.io` — and the desktop half of a rule the platforms disagree about. |
| `iosTest` | yes | The iOS half of the same, plus the cinterop code that exists nowhere else. |
| `androidDeviceTest` | no | What only a device can answer: a CursorWindow's size limit, `BitmapFactory`, the keystore, pdfbox-android. |

A test in `jvmTest` that imports nothing platform-specific is a common test in
the wrong place — it runs once instead of three times, and never against the
`actual` implementations two of the three platforms use.

# What is deliberately not tested, and why

- **`IOSSecretStore`.** A Kotlin/Native test executable runs in the simulator
  without an app bundle, so it has no keychain: `SecItemAdd` answers `-25291`,
  `errSecNotAvailable`, for every write. Nothing about the store can be asserted
  from `iosTest` — a test there would pin the absence of a keychain rather than
  the code. `JvmSecretStoreTest` and `AndroidSecretStoreTest` cover the same
  contract on the two platforms where it can be asked; covering iOS means an
  XCTest target in `iosApp`, which is a bundle with entitlements.

# Where things stand

| Suite | Tests | Runs on CI |
|---|---|---|
| `:shared:jvmCommonTest` | 1,040 | yes |
| `:shared:testAndroidHostTest` | 1,039 | yes |
| `:shared:iosSimulatorArm64Test` | 1,067 | yes |
| `:shared:jvmTest` | 1,630 | no — local |
| `:shared:connectedAndroidDeviceTest` | 1,040 | no — needs a device |

The three that overlap are the common tests, run once per platform against that
platform's `actual` implementations.

# Manual Testing

Run everything the JVM can run — the common tests and the platform ones CI skips:

```bash
./gradlew :shared:jvmTest
```

Run only what CI runs, to reproduce a CI failure:

```bash
./gradlew :shared:jvmCommonTest :shared:testAndroidHostTest
```

With an Android emulator or device connected, run Android tests:

```bash
./gradlew :shared:connectedAndroidDeviceTest
```

Run both:

```bash
./gradlew :shared:jvmTest :shared:connectedAndroidDeviceTest
```

On macOS with an iOS Simulator available, run iOS tests:

```bash
./gradlew :shared:iosSimulatorArm64Test
```

# Coverage

```bash
./gradlew :shared:koverHtmlReport
```

Writes `shared/build/reports/kover/html/index.html`.

Read it, do not chase it. Kover instruments JVM and Android bytecode only, so
what `iosTest` and the device tests prove does not appear in the number at all.
The report also covers only the layers where a percentage means something:
`domain`, `application` and `infrastructure`. Composables, DI, navigation and
the generated database and resource classes are filtered out, because their
coverage is a fact about which suites happened to run rather than about the code.

Those layers stand at 88%, and `koverVerify` — which runs as part of `check` —
holds them to 80. A floor to notice a feature arriving with no tests, not a
target. It is not in the CI jobs: it measures `jvmTest`, and CI runs
`jvmCommonTest`.

It is lower than the common layers alone reach because `androidMain` is counted
too, and Kover sees only what the JVM and Android-host runs execute — so what the
device tests prove about the keystore and the page content reader does not appear
in it. That is worth seeing rather than filtering away, which is why the number is
left aggregated rather than measured per target.

## Before opening a pull request

CI does not run the platform tests, so this is the check that is only yours to
make:

```bash
./gradlew :shared:jvmTest
```
