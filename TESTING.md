# CI Testing Info

- CI runs `:shared:jvmCommonTest` — the tests in `commonTest`, and only those.
- It also compiles `androidMain`, `iosMain`, `jvmMain` and every test source set,
  including the platform tests it does not run: a test that no longer compiles
  still fails the build.
- The platform tests are run on a local machine. `jvmTest` adds 659 tests to the
  814 common ones and 30 seconds to the 31 — nearly all of it the 40 Compose UI
  suites, which want a display.
- iOS is the exception: `:shared:iosSimulatorArm64Test` runs on CI and takes the
  9 `iosTest` files with it. The framework has to be built and linked either way,
  and running the common tests a second time against the iOS `actual`
  implementations is most of why the job is worth its minutes.
- Android device tests need a connected device or emulator, so they are local only.

# Manual Testing

Run everything the JVM can run — the common tests and the platform ones CI skips:

```bash
./gradlew :shared:jvmTest
```

Run only what CI runs, to reproduce a CI failure:

```bash
./gradlew :shared:jvmCommonTest
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

## Before opening a pull request

CI does not run the platform tests, so this is the check that is only yours to
make:

```bash
./gradlew :shared:jvmTest
```
