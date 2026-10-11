# Diagnostics

How a maintainer learns what went wrong on someone else's device without that
device sending anything.

There is no telemetry, no crash-reporting SDK and no network path. Recording is
off by default, kept in memory, and leaves the device only when the reader
copies it.

## What is recorded

| Area | Event | When |
| --- | --- | --- |
| `Reading` | `text-decode-fallback` | A text file's encoding was guessed |
| `Reading` | `pdf-pages-unreadable` | Pages that would not parse, counted per book |
| `Reading` | `reflow-cache-write-failed` | A converted book could not be stored |
| `Import` | `metadata-extraction-failed` | Extraction threw |
| `Import` | `cover-extraction-failed` | Extraction threw |
| `Import` | `cover-shrink-failed` | Artwork no decoder recognised |
| `Import` | `import-read-partial` | A file yielded no cover or no title |
| `Translation` | `request-failed` | A provider's request did not come back |
| `Storage` | `schema-created` | First run |
| `Storage` | `schema-migrated` | An update changed the database |
| `Storage` | `migration-failed` | A migration failed |

An entry holds a constant event name, an exception **type**, and details such as
document type, byte sizes, page counts, HTTP statuses and durations.

## What is never recorded

- Book text, translated text, prompts or responses
- API keys and tokens, including inside a URL or header
- File paths and file names
- Book titles and authors
- Server addresses the reader typed

Three things enforce this:

1. `DiagnosticEntry` keeps an exception's type, never its message.
2. `event` is a constant, never an interpolated string.
3. `DiagnosticsRedactionTest` imports a fixture with a known title, author and
   file name and fails if any of them reaches an entry or the report.

## The buffer

- Off by default (`AppSettings.diagnostics`). Turning it off clears it.
- Holds the last 200 entries in memory. There is no log file.
- Shown in Settings once developer settings are on: seven presses on the
  version in About. The list shows exactly what the export copies.

## The crash file

`CrashRecorder` writes one app-private file, `last-crash.txt`, overwritten each
time. The next launch offers it in Settings to read, copy or delete.

- It holds the exception type and stack trace, never the message.
- It is written whether or not recording is on.
- The crash hook chains to the platform's own, so Android's crash dialog and
  Play's reports still work.

Apple and Google Play already collect crashes with the consent the reader gave
the platform, which is why no crash-reporting service is used.

## Adding a call site

1. Record where a failure is **absorbed** (a `runCatching { }.getOrNull()`, an
   `?: return null`), not where it is raised, and not in `presentation` or
   `domain`.
2. Record once per document, not once per page or item.
3. Cover both the throw and the `null` result.
4. Use a constant `event`. Put counts, formats, statuses and durations in
   `detail`.
5. Take `diagnostics: Diagnostics = Diagnostics.None` as a parameter.

If an entry would need a title, a path or a passage to be useful, it is the
wrong entry.

## Where the code is

| Piece | Location |
| --- | --- |
| `Diagnostics`, `DiagnosticEntry`, `DiagnosticArea`, `DiagnosticLevel` | `core/domain/diagnostics` |
| `DiagnosticBuffer`, `RecordingDiagnostics`, `DiagnosticReport`, `RecordingSchema` | `core/infrastructure/diagnostics` |
| `CrashStore`, `CrashRecorder`, `CrashReport` | `core/{domain,infrastructure}/diagnostics` |
| Settings group | `features/settings/presentation` |
