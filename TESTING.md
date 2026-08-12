# CI Testing Info
- CI only runs `:shared:jvmTest` (covers commonTest + desktop/JVM tests)
- Android tests need a connected device/emulator and iOS tests need a macOS host — both must be run manually

# Manual Testing

Run JVM tests:

```bash
./gradlew :shared:jvmTest
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