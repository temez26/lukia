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

Where one feature does depend on another, the arrow should read as a question
one of them can ask. The reader asks the translator for words back and for how
translating is set up; it does not borrow the translator's types to describe its
own pages. Anything both of them need in order to ask - a passage unit, a book's
catalogue - belongs in `core`, not in whichever of the two wrote it first.

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

A repository interface read by more than one feature belongs here, and its
implementation stays with the storage it wraps. `ReadingProgressRepository` and
`DocumentCatalogue` are both in `core/domain` and both implemented under
`features/`. This is what keeps two features that need the same thing from
pointing at each other: the reader opens a book by asking `DocumentCatalogue`,
not by importing the library.

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

## License

Copyright 2026 Teemu Kalmari.

Licensed under the Apache License, Version 2.0 (the "License"); you may not use
this file except in compliance with the License. You may obtain a copy of the
License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed
under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
CONDITIONS OF ANY KIND, either express or implied. See the License for the
specific language governing permissions and limitations under the License.

Lukia bundles third-party software, each under its own terms. [NOTICE](NOTICE)
carries the licence and copyright line of every one, and the app repeats the
list under Settings > About. Both come from
[`config/attribution/dependencies.txt`](config/attribution/dependencies.txt),
which the `checkAttribution` build task holds against the version catalogue - so
a dependency cannot arrive without the terms it comes under.
