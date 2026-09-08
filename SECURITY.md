# Security

Lukia is a reading app with no server. Books stay on the device, the library
never leaves it, and the only thing that goes out over the network is a passage
the reader asked to have translated, sent to a translation service the reader
chose and configured themselves. There is no account, no telemetry and no
crash-reporting SDK - see [DIAGNOSTICS.md](DIAGNOSTICS.md).

What that leaves worth reporting is below.

## Reporting

Open a [private security advisory](https://github.com/temez26/lukia/security/advisories/new)
on this repository. **Please do not open a public issue for anything in scope.**

Tell me what you did, what happened, and which platform and version - Settings >
About names the version, and a bug report is much easier to act on with it.

## What to expect

This is one person's project, worked on in their own time. So, honestly:

- **An acknowledgement within a week.** If you have heard nothing after that,
  assume it went astray and say so again.
- **An assessment within a month**, saying whether I agree it is a problem and
  what I intend to do about it.
- **A fix in the next release** for anything that puts a reader's keys or their
  library at risk, and no promised date for anything else.

There is no bounty. Credit in the release notes if you would like it, and none
if you would rather not.

## In scope

The parts where a mistake reaches something the reader would mind losing:

- **The reader's API keys**, held in each platform's own secret storage -
  Android's keystore, iOS's keychain, and the JVM's store on desktop. A way to
  read one out of the app, out of a diagnostic report, or out of a crash file.
- **The translation path.** A key or a passage sent anywhere the reader did not
  configure, or a request that leaves the address they typed.
- **The parsers.** Lukia opens EPUB, PDF, DOCX, Markdown and plain text, and a
  file it opens is a file somebody else wrote. A crafted document that reads or
  writes outside the app's own storage, or reaches the network, is in scope.
  A crafted document that merely fails to open is a bug rather than a
  vulnerability - report it as one.
- **The library.** Anything that lets a document handed to the app reach another
  application's data, or another application reach the library.
- **Diagnostics.** A recorded entry, a report or a crash file carrying anything
  from the list DIAGNOSTICS.md says must never be recorded - a title, a path, a
  passage, a key.

## Out of scope

- **Anything needing an unlocked device in somebody else's hands.** Lukia has no
  passcode of its own and does not claim to protect a library from whoever is
  holding the phone.
- **The translation services themselves.** Whatever OpenAI, Google or a local
  model does with a passage is between the reader and them; the app's part is
  sending it only where the reader said. Report their problems to them.
- **A local model's own address.** Pointing Lukia at a server on the reader's
  own network is what that setting is for.
- **Reports from an automated scanner with nothing behind them.** A dependency
  named in an advisory that the app does not reach is worth telling me about;
  a list of CVEs with no reachability argument is not.
- **The absence of certificate pinning, root detection or an obfuscation
  scheme.** These are deliberate: the app talks to whatever server the reader
  chose, so there is no certificate to pin.

## Dependencies

Everything Lukia ships is listed in [NOTICE](NOTICE), and the version of each is
in [gradle/libs.versions.toml](gradle/libs.versions.toml), whose comments say why
several are pinned where they are. The two that parse files the app did not write
- PDFBox and its Android port - are kept as current as their releases allow, and
the comments there say what that does and does not buy.
