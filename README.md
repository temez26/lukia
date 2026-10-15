# Lukia

A reader for the books you already have, on Android, iOS and desktop, built for
reading in a language you are still learning.

- **Reads** PDF, EPUB, Word (`.docx`), Markdown and plain text. A PDF can be
  read as its own pages or re-set as text.
- **Translates as you read.** Tap a word for a dictionary answer (Wiktionary or
  Google), or a sentence or paragraph for a translation from a service you set
  up: Anthropic, OpenAI, or a model on your own machine.
- **Keeps a library** on the device: covers, search, filters, favourites and
  your place in each book.
- **Counts your reading** (time, sittings, streaks).
- **Syncs, if you want it to,** with a server you run yourself: one library
  shared by everyone with an account, and each person's place in every book,
  favourites and reading statistics on all their devices. See
  [SERVER.md](docs/SERVER.md).

Without a server, nothing leaves the device except the words and passages you
ask about. Signed in to one, your place in each book, favourites and reading
sessions go to that server too - see [what it stores](docs/SERVER.md#what-the-server-stores).

## Platforms

| Platform | Minimum | Distributed |
| --- | --- | --- |
| Android | 6.0 (API 23) | yes |
| Desktop | Windows, macOS, Linux (bundled runtime) | yes |
| iOS | 15.0 | no: built and tested in CI, build it yourself ([BUILD.md](docs/BUILD.md#ios)) |

## Built with

Kotlin Multiplatform, Compose Multiplatform, Koin, SQLDelight, Ktor and Kotlin
coroutines.

## Architecture

Feature-first, with Clean Architecture layers inside each feature.

| Module | Holds |
| --- | --- |
| `shared` | The app: every feature, its UI and its storage |
| `formats` | Reading book files - ZIP, EPUB, PDF, DOCX - for the app and the server alike |
| `api` | The types the app and the sync server send each other |
| `server` | The self-hosted sync server ([spec](docs/specs/sync-server.md)) |
| `androidApp`, `desktopApp`, `iosApp` | Each platform's entry point |

```
shared/src/commonMain/kotlin/io/lukia
├── App.kt          application entry point
├── Routes.kt       navigation destinations
├── navigation/     mobile and desktop shells
├── di/             application composition
├── core/           code shared by more than one feature
├── shared/         design system and shared UI components
└── features/       library, reader, translate, settings, stats, account, sync
```

Each feature holds up to four layers; a layer with only a file or two is left
out.

| Layer | Holds |
| --- | --- |
| `domain` | Entities, value objects, repository contracts |
| `application` | Use cases |
| `infrastructure` | Database, network, file formats, repository implementations |
| `presentation` | Screens, view models, components |

Rules, enforced by `ArchitectureTest`:

- `domain` imports no other layer.
- `core` and `shared` never import a feature.
- Features do not depend on each other in a circle. What two features both need
  goes in `core`.

## Documentation

| | |
| --- | --- |
| [BUILD.md](docs/BUILD.md) | Building and signing each platform, release checks |
| [TESTING.md](docs/TESTING.md) | What runs where, and how to run it |
| [CODING_STYLE.md](docs/CODING_STYLE.md) | Style rules and the tools that enforce them |
| [CONTRIBUTING.md](docs/CONTRIBUTING.md) | Branches and pull requests |
| [DIAGNOSTICS.md](docs/DIAGNOSTICS.md) | What the app records, and what it never does |
| [SECURITY.md](docs/SECURITY.md) | Reporting a vulnerability |
| [SERVER.md](docs/SERVER.md) | Running, backing up and upgrading the sync server |
| [specs/](docs/specs) | Specifications for changes in progress |
| [decisions/](docs/decisions) | Architecture decision records |

## Licence

Copyright 2026 Teemu Kalmari. Licensed under the
[Apache License 2.0](LICENSE).

Third-party software is listed in [NOTICE](NOTICE) and in the app under
Settings > About. The `checkAttribution` build task keeps that list in step with
the dependencies.
