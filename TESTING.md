# Testing

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
