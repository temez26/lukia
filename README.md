## Architecture

The project follows a feature-first Clean Architecture combined Domain-Driven Design.

```
shared/
└── io.lukia
    ├── App.kt
    ├── Routes.kt
    ├── di/
    ├── core/
    └── features/
```

### Project structure

- **features** – Self-contained business features (Library, Reader, ...)
- **core** – Shared functionality used across multiple features
- **di** – Dependency injection and application composition
- **App.kt** – Application entry point
- **Routes.kt** – Navigation definitions

Each feature is self-contained.

```
feature/
├── application/
├── domain/
├── infrastructure/
└── presentation/
```

### Layer responsibilities

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