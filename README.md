## Architecture

Lukia follows a feature-first architecture based on Clean Architecture and Domain-Driven Design (DDD).

```
shared/
└── io.lukia
    ├── App.kt
    ├── Routes.kt
    ├── Platform.kt
    ├── navigation/
    ├── di/
    ├── shared/
    ├── core/
    └── features/
```

### Project structure

- **features** – Self-contained business features (Library, Reader, ...)
- **core** – Shared functionality used across multiple features
- **shared** - Shared ui components mostly
- **navigation** – The app shells and the graph they share. Mobile gets a bottom
  tab bar over Home, Search and Library; desktop keeps the library as its one
  screen
- **di** – Dependency injection and application composition
- **App.kt** – Application entry point
- **Routes.kt** – Navigation definitions
- **Platform.kt** - is the platform functionality template

Each feature try to be self-contained.

```
feature/
├── application/
├── domain/
├── infrastructure/
└── presentation/
```

### Layer responsibilities
if there is just couple of files per responsibility, layer is not needed

| Layer | Responsibility |
|--------|----------------|
| application | Use cases, services and DTOs |
| domain | Business entities, value objects and repository contracts |
| infrastructure | Database, external services and repository implementations |
| presentation | UI, ViewModels and reusable components |

### Core

The `core` module contains reusable functionality that is shared across multiple features and is not tied to any specific business domain.

```
core/
├── application/
├── domain/
├── infrastructure/
└── presentation/
```

Examples of shared code include:

- Common UI components
- Design system
- Shared domain models
- Base interfaces
- Utilities
- Shared services
- Cross-feature infrastructure

## Supported platforms

| Platform | Minimum                                           |
| -------- | ------------------------------------------------- |
| Android  | 6.0 Marshmallow (API 23)                          |
| iOS      | 15.0                                              |
| Desktop  | Windows, macOS and Linux with the bundled runtime |

Both floors are set by the platform code rather than by taste: Android needs the
keystore's AES support (API 23) to hold the reader's translation key, and iOS
needs `UTType` and the content-type document picker (14.0), with 15.0 taken as
the nearest whole release above it. `IPHONEOS_DEPLOYMENT_TARGET` lives in
[Config.xcconfig](iosApp/Configuration/Config.xcconfig), the Android versions in
[libs.versions.toml](gradle/libs.versions.toml).

## Technologies

- Kotlin Multiplatform
- Compose Multiplatform
- Jetpack Compose
- Koin for dependency injection
- Kotlin Coroutines for asynchronous programming
- Native Kotlin platform APIs

## Code style

See [CODING_STYLE.md](CODING_STYLE.md) for the full guide - size limits, naming, Compose
conventions, correctness rules, and how the baselines for existing violations work.

## Diagnostics

See [DIAGNOSTICS.md](DIAGNOSTICS.md) for what the app records when something goes
wrong, what it must never record, and where a call belongs. Nothing is collected
unless the reader turns it on, and nothing leaves the device unless they copy it
out themselves.

## Contributing

See our [Guidelines](CONTRIBUTING.md) for details on how to submit changes, write clear PR titles, and structure your commits.

## Testing

(See the full details in [TESTING.md](TESTING.md).)