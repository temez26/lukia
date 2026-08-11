# Testing

Run JVM tests:

```bash
./gradlew :shared:jvmTest
```

With an Android emulator or device connected, run Android tests:

```bash
./gradlew :shared:connectedAndroidDeviceTest
```

On macOS with an iOS Simulator available, run iOS tests:

```bash
./gradlew :shared:iosSimulatorArm64Test
```

Run both:

```bash
./gradlew :shared:jvmTest :shared:connectedAndroidDeviceTest
```
