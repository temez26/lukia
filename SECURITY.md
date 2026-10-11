# Security

Lukia has no server, no account and no telemetry. Books stay on the device. The
only network traffic is a word or passage the reader asks about, sent to the
dictionary or translation service they chose. See [DIAGNOSTICS.md](DIAGNOSTICS.md).

## Reporting

Open a [private security advisory](https://github.com/temez26/lukia/security/advisories/new).
Do not open a public issue for anything in scope.

Include what you did, what happened, the platform and the version (Settings >
About).

## What to expect

This is a one-person project.

- Acknowledgement within a week.
- An assessment within a month.
- A fix in the next release for anything that puts API keys or the library at
  risk. No promised date for anything else.

There is no bounty. Credit in the release notes if you want it.

## In scope

- **API keys.** Reading one out of the app, a diagnostic report or a crash file.
  Keys are held in the platform's secret storage.
- **The translation path.** A key or passage sent anywhere the reader did not
  configure.
- **The parsers** (EPUB, PDF, DOCX, Markdown, plain text). A crafted document
  that reads or writes outside the app's storage, or reaches the network. One
  that only fails to open is a bug, not a vulnerability.
- **The library.** A document reaching another app's data, or another app
  reaching the library.
- **Diagnostics.** A record, report or crash file containing anything
  DIAGNOSTICS.md says must never be recorded.

## Out of scope

- Anything that needs an unlocked device in someone else's hands.
- What a translation service does with a passage once it has it.
- Pointing the app at a server on the reader's own network.
- Scanner output with no argument that the app reaches the vulnerable code.
- The absence of certificate pinning, root detection or obfuscation. The app
  talks to whichever server the reader chose, so there is nothing to pin.

## Dependencies

Everything shipped is listed in [NOTICE](NOTICE), with versions in
[gradle/libs.versions.toml](gradle/libs.versions.toml).
