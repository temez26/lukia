# Optimization audit

**Date:** 2026-10-16
**Scope:** the changes on `fix/ui-polish` against `main` (UI polish, the Ledger theme, per-palette shapes and type, the themed reader chrome).
**Stack:** Kotlin Multiplatform with Compose Multiplatform 1.12 (Android, desktop, iOS).

## Findings

| # | Finding | Category | Location | Status |
| --- | --- | --- | --- | --- |
| 1 | The bundled serif was read on every launch, for every palette | Unneeded work | `theme/LukiaTheme.kt`, `theme/Type.kt` | Fixed, 3e68d08e |
| 2 | The sign-in form's "can submit" rule was written twice | Duplicated logic | `account/presentation/SignInForm.kt` | Fixed, 1f24a137 |
| 3 | Every segment label was auto-sized on every layout | Recomputation | `controls/SegmentedControl.kt` | Fixed, 7e4b48af |
| 4 | Seven exhaustive `when (palette)` blocks | Possible duplication | `Color.kt`, `PaletteShapes.kt`, `Type.kt`, `ThemeChoicesDisplay.kt` | Won't fix |

### 1. The bundled serif was read on every launch

`LukiaTheme` called `bundledSerif()` whatever the palette. In Compose
Multiplatform's resource library, desktop reads a resource font with
`runBlocking` during the first composition, and iOS reads it asynchronously,
then swaps it in, which redraws every line of text. Crisp and Slate paid for
about 960 KB of fonts they never draw. Android reads fonts lazily and was not
affected.

Fix: `rememberTypography` reads the serif only when `setsHeadingsInSerif()`.
`typographyFor` is still the one mapping from palette to type, and
`PaletteStyleTest` holds the two to each other.

**Measured** on desktop, cold first composition of `LukiaTheme` with Crisp,
5 runs each: median 527 ms before, 499 ms after (about 28 ms per launch). The
iOS saving (no font read and no two whole-app redraws) is **estimated** from
the library source; it could not be measured without a Mac.

### 2. The "can submit" rule was written twice

The keyboard's Go action and the submit button's enabled state each checked
`complete && !busy`. It is now worked out once as `canSubmit`. A new test in
`AccountSettingsTest` holds the Go key to the same terms as the button.

### 3. Every segment label was auto-sized on every layout

Shrink-to-fit lays a label out at several sizes on every layout, and it ran
for every segment of every segmented control, although almost every label
fits. A segment now sets its label at full size and switches to auto-sizing
only after a layout shows it overflowing. The rendered result is identical
before and after (compared from screenshots).

**Measured** on desktop, 20 controls of 5 segments re-laid out 20 times,
5 runs each: median 1017 ms before, 670 ms after.

### 4. Seven exhaustive `when (palette)` blocks

Won't fix. Each answers a different question (surfaces, accent, shapes,
icon-button shape, type, name, description), and the compiler fails the build
when a new palette misses one. That is the project's own convention
(`CODING_STYLE.md`: prefer an exhaustive `when` over `else`), not duplicated
logic.

## Found along the way

Measuring finding 3 showed that shrink-to-fit, added in the polish pass, did
not shrink a label that was too wide: with `TextOverflow.Ellipsis` a label cut
short counted as fitting, so the selected "Appearance" in a 300dp control was
shown as "Appeara…". It had only seemed to work on a phone at 200% text,
where the label overflowed in height.

Fixed separately: a label is set at full size, shrinks (clipped, so the
shrinking can see it does not fit) when it overflows, and is ellipsised at the
smallest size only if it still does not fit there. It is fitted afresh when
the text, the segment's width or the system text size changes.
`SegmentedControlFitTest` covers all three cases. Re-fitting on a width change
costs some of finding 3's saving back: the same benchmark measures a median of
771 ms (1017 ms originally, 670 ms with the earlier version that never shrank).

Checked on a phone at 200% text: "Aussehen" now shrinks whole; "Appearance",
"Translating" and "Übersetzen" do not fit even at the smallest size and are
ellipsised there.

# Sync: the server and the app's connection to it

**Date:** 2026-10-16
**Scope:** the sync server (`server/`, Ktor and SQLite) and the app's side of it
(`features/sync`, `features/account`), including the live event stream.

A sync round asks for three change feeds - the library, the account's book
states and its sessions - plus one write per book or session with something to
send. The figures below count rounds, so three requests each, for one person
reading on a phone with a second device open.

| # | Finding | Category | Location | Status |
| --- | --- | --- | --- | --- |
| S1 | A device was told about its own changes, and ran a round for each | Duplicate trigger | `events/ChangeEvents.kt`, `events/EventRoutes.kt` | Fixed, a1e96746 |
| S2 | An app on screen polled every two minutes while its live line was open | Polling where push exists | `App.kt`, `SyncCoordinator` | Fixed, 659df06d |
| S3 | Two rounds at every launch, and one per request queued during a round | Duplicate trigger | `SyncCoordinator.run`, `syncNow` | Fixed, a5091fa8 |
| S4 | Writes that stored nothing were announced to every device | Unneeded work | `BookStateService`, `SessionService` | Fixed, 17343d6e |
| S5 | Every hint runs the full round, whatever kind of change it names | Over-fetching | `SyncCoordinator.listenForChanges` | Won't fix |
| S6 | One write per changed book or session rather than a batch | Chatty API | `SyncBookStateUseCase`, `SyncSessionsUseCase` | Won't fix |
| S7 | `MAX(seq) + 1` on `book_state` and `reading_session` reads the whole `(accountId, seq)` index | Database | `BookState.sq`, `ReadingSession.sq` | Won't fix |

### S1. A device was told about its own changes

The server announced each change to every listening device on the account,
the one that made it included, and that device ran a full round for a change
it already had. Each change now carries the sign-in (auth family) that made
it, and a device's own stream leaves it out (`Change.isFor`).

**Saving (counted):** every send while reading - one every few seconds of page
turning - cost the sending device two rounds; it now costs one.
`LiveChangesTest.aDeviceIsNotToldOfItsOwnChange` fails without the fix.

### S2. Polling while the live line was open

The app ran a round every two minutes while on screen, alongside the event
stream that already announces every change. `SyncCoordinator.keepUp` now
catches up each time the line opens - on coming to the front and after a
reconnect, which before waited up to two minutes for the next check - and
checks every two minutes only while the line is down (not open within three
seconds, or dropped).

**Saving (estimated from the code, confirmed by `SyncRoundsTest`):** 30 rounds
an hour, about 90 requests, per device on screen with a working line. Catching
up as the line opens also closes a gap in which a change made between the
resume round and the line opening went unheard until the next check.

### S3. Overlapping rounds

At launch both the sign-in watcher and the app reaching the foreground ran a
round; the watcher now runs one only for an actual sign-in. And each request
made during a round queued a round of its own; a request is now answered by
any round that started after it was made.

**Counted at a real server (`SyncRoundsTest`):** a launch while signed in runs
1 round, not 2; three requests during a round run 2 rounds, not 4. Signing in
while the app is open still runs one.

### S4. Announcing writes that stored nothing

A book state or session sent again, or older than what was stored, still woke
every device for a full round. The services now announce only when the row's
`seq` moved. `ChangeAnnouncementsTest` counts 2 announcements where there were 4.

### Not fixed

- **S5.** A round per kind of change would save two requests per hint, but a
  state-only round after a missed library hint would skip a book it does not
  know yet and move its cursor past it for good. The full round's order is
  what makes it safe.
- **S6.** Rarely more than one book or session changes between rounds, so a
  batch endpoint would add API surface for little.
- **S7.** Each write scans the account-first index for the highest `seq`. On a
  self-hosted server's data (thousands of rows) this is microseconds; an index
  on `seq` alone would cost every write. Worth revisiting only for a large
  shared server.

### Checked on a phone

The server ran on a PC on the local network (the phone reached it at its LAN
address; sign-in went through an `adb reverse` tunnel), with a second client
signed in to the same account standing in for another device.

- A new book uploaded by the other device reached the phone the same second.
- Three page turns on the phone reached the other device within seconds
  (page 4 of 94); a page set on the other device reached the phone within a
  second (53%, page 50 of 94).
- In 5½ minutes on screen with the line open there was **no** two-minute
  check.
- Not tested behind a proxy that closes idle connections.

### Found on the phone: open

| # | Finding | Category | Location | Status |
| --- | --- | --- | --- | --- |
| S8 | A book left open runs 2 rounds every 20 seconds | Duplicate trigger, chatty sync | `SyncModule` (`waiting`), `SessionStore.waiting` | Empty rounds fixed, 85c9dca0; send interval unchanged by choice |
| S9 | The sign-in form asked for the server's health 6 times while one address was typed | Repeated calls | `AccountViewModel` (not yet traced) | Open |

**S8.** The reading session grows while a book is open, and each growth
counts as something waiting to send; five seconds later a round sends it. Then
sending it brings the waiting count back to zero, which counts as a change too,
and five seconds after that an empty round runs. Measured on the phone, idle
in the reader: a round with a session write every 20 seconds plus an empty one
after each - about 360 rounds, some 1,250 requests, an hour. This predates this
branch. Two parts to a fix: only a count above zero should start a round (the
empty follow-ups go, without changing what is sent or when), and a growing
session need not be sent more often than every few minutes (a change in how
soon statistics reach other devices, so the user's call).

Fixed: the empty rounds. On the phone the cause turned out to be reading done
before the first sign-in: held back until the reader answers the history
question, never sent, but counted as waiting - so the count never reached
zero. The coordinator now watches `SyncSessionsUseCase.waiting`, which counts
only what a round would send (`countUnsentAfter`, the same cutoff as
`selectUnsent`), and starts a round only when it settles above zero.
**Measured on the phone**, idle in the reader for 2½ minutes: 15 rounds before
(a send every 20 seconds and an empty round after each), 8 after (the sends
alone). The growing session is still sent every 20 seconds, as chosen.

Noticed on the way, not changed: the sync status card counts the held-back
reading as waiting too, so it shows changes waiting that will not go until the
reader answers; and the server logs a 500 when a device's event stream ends
because the device went away.
