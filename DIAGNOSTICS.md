# Diagnostics

How a maintainer finds out what went wrong on somebody else's device, without
that device sending anything anywhere.

Nothing here is telemetry. There is no server, no crash-reporting SDK and no
network path out of any of it. A record is collected only if the reader turns it
on, it lives in memory, and the only way it leaves the device is the reader
copying it and choosing where to paste it.

## What the app records

The app absorbs failures on purpose in a dozen places, because none of them is
worth failing an import or closing a book over. What the reader gets is a grey
placeholder, a blank page, mojibake. What a maintainer got, before this, was a
bug report describing one of those and nothing else.

| Area | Event | When |
|---|---|---|
| `Reading` | `text-decode-fallback` | a text file's encoding was guessed at |
| `Reading` | `pdf-pages-unreadable` | pages that would not parse, counted per book |
| `Reading` | `reflow-cache-write-failed` | a converted book could not be stored |
| `Import` | `metadata-extraction-failed` | an extraction threw |
| `Import` | `cover-extraction-failed` | an extraction threw |
| `Import` | `cover-shrink-failed` | artwork no decoder recognised |
| `Import` | `import-read-partial` | a container format yielded no cover or no title |
| `Translation` | `request-failed` | any provider's request did not come back |
| `Storage` | `schema-created` | first run of an installation |
| `Storage` | `schema-migrated` | an update changed the database |
| `Storage` | `migration-failed` | it did not |

Plus one file, written when the app dies: see [The crash file](#the-crash-file).

## What it must never record

| Never | Where it would come from |
|---|---|
| **Book text** — passages, sentences, words, headings, page content | the reader's parsers |
| **Translated text** — prompts, responses, cached entries | the translation providers |
| **API keys and tokens**, including inside a URL or a header dump | `SecretStore`, Ktor exception messages |
| **File paths and file names** — a desktop path holds the reader's own name, and a file name is usually an author's | `PickedDocument`, `DocumentInfo` |
| **Book titles and authors** | `BookMetadata` — a library is a political and a medical record |
| **Server addresses the reader typed** | a local model's `baseUrl` is a home LAN address |

Recorded instead: `DocumentType`, byte sizes, page counts, HTTP statuses,
durations, and exception **types**.

Three things enforce that rather than hoping for it.

1. **`DiagnosticEntry` keeps a throwable's type and never its message.** A
   message is uncontrolled text from whatever threw: Ktor puts the request URL
   in one, a file failure puts the path in one. The type is the half that helps
   and the half that is safe, and keeping only it means no call site can opt
   back into the other.
2. **`event` is a constant, never an interpolated sentence.** It is the half of
   an entry a maintainer greps for, and the half nothing about the reader can
   reach.
3. **`DiagnosticsRedactionTest`** runs a real import and a real decode over a
   fixture whose title, author and file name are known strings, and fails if any
   of them reaches a recorded entry *or the assembled report*. It also fails if
   the fixture stops recording, so it cannot pass by proving nothing.

## Where a call belongs

**Log where a failure is absorbed, not where it is raised.** Every site above is
an absorption point — a `runCatching { }.getOrNull()`, a `getOrElse`, an
`?: return null`. That makes the rule greppable: a `catch` that returns a
degraded result and records nothing is the bug.

Three corollaries, each of which cost something to learn:

- **Do not record at the deepest frame.** `Inflate` and `ZipArchiveReader`
  swallow a failed inflate, and that frame knows a stream did not inflate and
  nothing about why anyone wanted it. The caller records
  `cover-extraction-failed, EPUB`, which is the sentence a maintainer needs.
- **One entry per document, not one per unit.** A corrupt 900-page PDF would
  otherwise evict every other entry in the buffer. Count and record once.
- **Absorption is usually a `null`, not a throw.** This is the one that was got
  wrong first. `extractMetadata` and `extractCover` return null for a file they
  cannot read far more often than they throw, so a hook on the throw alone fires
  almost never. What is worth recording is the pair, at the funnel:
  `import-read-partial, EPUB, cover none, metadata none`.

**Nothing in `presentation`, and nothing in `domain`.** By the time a throwable
reaches `toAppError()` in a ViewModel it has crossed a `withContext` and the
frames that identified the parser are gone; the useful record was the one made
where the throwable was still next to the bytes. Presentation also runs at
recomposition rates, where a call one lambda too high fires per frame.

**The gap this leaves**, stated plainly: a throwable that reaches
`runCatchingApp` in a ViewModel having passed no absorption point is recorded
nowhere. That is deliberate. An unmodelled throwable arriving at a ViewModel
means an absorption point is missing, and the fix is to add one where it
belongs. The subset that kills the process is caught by the crash file.

## The parts

| Piece | Where |
|---|---|
| `Diagnostics`, `DiagnosticLevel`, `DiagnosticArea`, `DiagnosticEntry` | `core/domain/diagnostics` |
| `DiagnosticBuffer`, `RecordingDiagnostics`, `DiagnosticReport` | `core/infrastructure/diagnostics` |
| `RecordingSchema` — database creation and migration | `core/infrastructure/diagnostics` |
| `CrashStore`, `CrashRecorder`, `CrashReport`, `installCrashHook` | `core/{domain,infrastructure}/diagnostics` |
| The settings group | `features/settings/presentation` |

`Diagnostics` is one method, three levels and a closed set of four areas. It is
**not suspending and takes no dispatcher**: every call site is inside a parser
or a decoder that is not a coroutine and has no business becoming one to say
that a cover failed to decode. A dispatcher is injected for work, and an entry
is a record rather than work.

It is threaded as a parameter defaulting to `Diagnostics.None`, which records
nothing. That default is what lets `decodeText` stay silent inside the PDF
string decoder — which runs per *string object* rather than per book — and what
keeps every test that is not about diagnostics unchanged.

**No logging dependency was taken.** Kermit and Napier mostly sell platform
sinks and severity plumbing; this design has no platform sink at all, and the
parts that matter — the buffer, the type-not-message rule, the switch, the
redaction test, the report format — are not in either library. Their `e { "..." }`
lambda API also invites exactly the interpolated sentences rule 2 above forbids.
Revisit if the app ever wants log-file rotation or a logger the Swift side can
write to.

There is deliberately **no debug-only platform log sink**. It would need a
build-type signal that KMP common code does not have cleanly, and it would add a
passive channel — `adb logcat` is readable by anyone with the unlocked device —
next to an export that requires a deliberate act.

## The buffer and the switch

Off by default, as `AppSettings.diagnostics`, read per entry rather than bound
once: the graph hands out one recorder for the life of the process.

The buffer holds the last 200 entries **in memory and nowhere else**. No log
file, so nothing outlives the session, gets swept into a backup, or turns up in
a file manager months later. Turning the setting off clears it — otherwise "off"
would quietly mean "stopped adding to it".

Reading and copying live in Settings, under whichever group is open rather than
as a fifth segment. The list shows the same lines the export copies, because
what a reader reviews before handing something over has to *be* what they hand
over.

## The crash file

The buffer dies with the process, so the one failure it can never show anybody
is the one that takes the app down. `CrashRecorder` writes one file,
`last-crash.txt`, app-private and overwritten each time; the next run offers it
in Settings to read, copy or delete.

The consent split needs no code. The stack trace is written whether or not
diagnostics are on — the crash worth reading is usually from the reader who
never turned anything on, and writing it costs no more consent than a
preferences file does. The run-up is the buffer, which is empty unless they
asked for it.

A failure's **message is left out**, exactly as `causeType` leaves it out; the
type and the `at` lines are what anybody works from.

**Every hook chains to the one already installed.** Replacing Android's would
take away its crash dialog and the reports Play collects — which are the crash
reporting this app relies on instead of shipping an SDK. On iOS the hook is
Kotlin/Native's `setUnhandledExceptionHook`; `NSSetUncaughtExceptionHandler` is
the obvious thing to reach for and is wrong, because it catches Objective-C
exceptions and would install cleanly and never fire.

## Why no crash-reporting service

Every such SDK is a network client that starts before the user can consent and
carries a device or installation identifier — "opt-in Crashlytics" is largely a
fiction, since the SDK initialises to catch the early crashes, which is the
point of it. Lukia has no server, so there is nowhere to send a crash that is
not a third party, and adding one to a reader whose library never leaves the
device would be a larger change than everything above.

Apple and Play already collect crashes under consent the reader gave the
platform, and those reports are already in front of the maintainer. The desktop
build is the only one without that, and the local crash file covers it.

## Adding a call site

1. Find the **absorption point** — where a failure becomes a degraded result.
2. Ask whether it is per document or per unit; if per unit, count and record
   once.
3. Ask whether the failure is a throw or a `null`. Usually both need covering.
4. Write a constant `event`. Put counts, formats, statuses and durations in
   `detail`, and nothing else.
5. Take `diagnostics: Diagnostics = Diagnostics.None` if the function has no
   other dependencies; otherwise take it as a constructor parameter.

If the entry would need a title, a path or a passage to be useful, it is the
wrong entry.
