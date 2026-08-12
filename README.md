## Architecture

Lukia follows a feature-first architecture based on Clean Architecture and Domain-Driven Design (DDD).

```
shared/
└── io.lukia
    ├── App.kt
    ├── Routes.kt
    ├── Platform.kt
    ├── di/
    ├── shared/
    ├── core/
    └── features/
```

### Project structure

- **features** – Self-contained business features (Library, Reader, ...)
- **core** – Shared functionality used across multiple features
- **shared** - Shared ui components mostly
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

## Technologies

- Kotlin Multiplatform
- Compose Multiplatform
- Jetpack Compose
- Koin for dependency injection
- Kotlin Coroutines for asynchronous programming
- Native Kotlin platform APIs

## Contributing

See our [Guidelines](CONTRIBUTING.md) for details on how to submit changes, write clear PR titles, and structure your commits.

## Testing

(See the full details in [TESTING.md](TESTING.md).)