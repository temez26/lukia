# Sync server

Status: Done

## Goal

Several people can share one self-hosted library. Each person gets an account,
and their place in every book, their per-book choices and their reading
statistics follow that account from device to device. Today all of these exist
on one device only, so a book started on a phone has to be found again by hand
on the desktop, and the statistics only count one device's reading.

Signing in is optional. Without an account the app behaves exactly as it does
now.

## Acceptance Criteria

Server

1. One Docker container holds the whole server. It stores its database and book
   files in one volume and starts without any outside service.
2. The first start has no accounts. The first account created becomes the
   admin. After that, accounts can only be created with an invite code made by
   the admin.
3. Signing in with a username and password returns a short-lived access token
   and a refresh token. Each refresh rotates the refresh token. Signing out
   revokes the refresh token. A wrong password returns the same error as an
   unknown username.
4. Every request except health, setup, sign-in and redeeming an invite needs a
   valid access token. A request without one gets `401`.
5. The library is the same for every account. Any signed-in account can upload
   a book. The book's identity is the SHA-256 of its bytes, so uploading the
   same file twice adds nothing new and returns the existing book.
6. An upload over the size limit (default 200 MB, configurable) or in a format
   the app can't read is rejected with a clear error, and nothing is stored.
7. Progress, PDF layout and favourite are stored per account and per book. One
   account's state can never be read or written with another account's token.
8. Reading sessions are stored per account. Sending the same session twice
   (same client-generated id) updates it instead of duplicating it.
9. Deleting an account removes its progress, choices, sessions and tokens. The
   books it uploaded stay, because they belong to the shared library.

Client

10. Settings has an Account section where you enter a server address, sign in,
    see who is signed in, and sign out. Signing out keeps everything on the
    device.
11. Signed in, the library shows the server's books beside the device's own.
    The first time you open a server book it is downloaded and then read like a
    local one, including offline after that.
12. Progress, layout and favourite changes made offline are kept in a queue and
    sent when the server can be reached. Reading never waits for the network.
13. When two devices disagree about where you are in a book, the change made
    later wins, judged by the time the change was made, not when it arrived.
14. The statistics screen counts sessions from every device on the account,
    with no session counted twice.
15. The first time you sign in, the app asks whether to add this device's
    existing reading history to the account. If you say no, that history stays
    on the device and is still counted locally.
16. A server that can't be reached, an expired session or a revoked token shows
    as a status in the Account section and never as an error in the reader.
    When the refresh token is rejected, the app asks you to sign in again.
17. Translation API keys, settings and diagnostics never go to the server.

## Out of Scope

- Uploading your own device's books to the server from the app's library. The
  first version only uploads from the server's admin web form or `curl`; an
  in-app upload follows in a later change.
- A web reader, or any server UI beyond a minimal admin page.
- Per-account private books, shelves, or sharing between accounts.
- Syncing settings, translation caches or dictionary caches.
- Password reset by email. The admin can reset a password.
- Federation, or more than one server per app install.
- Public hosting concerns: email verification, rate limiting beyond sign-in,
  S3, Postgres.

## Flow

Connecting a device

1. Settings > Account > Connect to a server.
2. Enter the server address. The app calls `/api/v1/health` and shows the
   server's name and version, or why it couldn't connect.
3. Sign in, or create an account with an invite code. On a new server, the
   first screen offers to create the admin account instead.
4. If the device has reading history, ask: "Add this device's reading history
   to <username>?" with the answers Add and Keep on this device.
5. The library refreshes and the server's books appear, labelled as on the
   server until they have been downloaded.

Reading a server book

1. Tap a server book. The book's detail screen shows its progress from the
   account.
2. Open it. If it hasn't been downloaded yet, show download progress, which can
   be cancelled. A failed download shows a retry.
3. Read. Progress saves locally as it does now and goes into the outbox.
   Pending changes are sent shortly afterwards, and again when the app
   returns to the foreground.

States the client adds: Account section (signed out, connecting, signed in,
offline, needs sign-in), download in progress or failed on a book, and an
"on server" badge on library cards.

## Impact

**Features and boundaries.** A new client feature, `features/account`, holds
the server connection, sign-in and the sync engine. Library, reader and stats
keep talking to the same ports (`ReadingProgressRepository`,
`ReadingSessionRepository`, `PdfLayoutRepository`, `DocumentCatalogue`). Sync
works behind those ports, recording local writes in the outbox, so none of
those features depends on `account`. The server is a new Gradle module,
`:server` (Ktor, JVM). The wire types go in a new KMP module, `:api`, used by
both sides so the contract is written once.

**Contracts.** A new HTTP API under `/api/v1`, JSON, versioned by path:

| Method | Path | |
| --- | --- | --- |
| GET | `/health` | name, version, whether setup is needed |
| POST | `/setup` | create the first (admin) account; `409` once any account exists |
| POST | `/auth/signin`, `/auth/refresh`, `/auth/signout` | tokens |
| POST | `/invites` (admin), `/auth/redeem` | invite codes |
| GET | `/books?since=<cursor>` | library changes since a cursor |
| POST | `/books` | streamed upload |
| GET | `/books/{id}/file`, `/books/{id}/cover` | download, supports `Range` |
| DELETE | `/books/{id}` (admin) | |
| PUT | `/accounts/{username}/password` (admin) | reset a password; signs that account out everywhere |
| GET/PUT | `/me/books?since=` / `/me/books/{id}` | progress, layout, favourite |
| GET/PUT | `/me/sessions?since=` / `/me/sessions/{uuid}` | reading sessions |
| DELETE | `/me` | delete the account |

Errors are sent as `{ "code": "...", "message": "..." }`. The client turns the
code into its own `AppError`, so it stays in the existing error model.

**Data, client.** Migration `11.sqm` (schema version 12):

- `document.contentHash TEXT`, nullable and indexed. The hash is the book's id
  on the server (ADR 0002), so no separate remote id is stored. It is worked out
  only after someone signs in, by `HashLibraryUseCase` in the background, never
  during an import. A reader who never syncs never pays for reading every byte.
  Re-importing a file that changed size clears its hash so it is worked out
  again. An edit that keeps the size exactly keeps a stale hash until the next
  re-import.
- `reading_session` gains `uuid` (unique, generated in the migration for
  sittings already recorded and by `start` from then on), `contentHash` and
  `title`, both taken from the book when the sitting starts. A sitting started
  before its book was hashed gets the hash when the book is.
- `sync_outbox(kind, key, payload)`, primary key `(kind, key)`. `kind` is
  progress, layout, favourite or session; `key` is the book's hash or the
  session's uuid. Writing the same thing again replaces the row, so a hundred
  page turns offline leave one position to send. The `changedAt` the server
  compares travels in `payload`. Rows go once the server accepts them.
- Sessions pulled from the account are stored with `location` set to the local
  location when this device has the book, or to `remote:<hash>` when it
  doesn't.

`readAt` stays a counter for ordering on one device. Comparing changes across
devices needs a wall clock, so each queued change carries `changedAt` in
milliseconds. That has the same clock-skew trade-off `ReadingSession.sq`
already accepts. A pulled change is applied unless a change to the same part is
still waiting in the outbox. The device pushes before it pulls, so the server
has already chosen between them.

**Data, server.** SQLite through SQLDelight's JVM driver, the same tool the
app uses. Tables:

- `account(id, username UNIQUE, passwordHash, isAdmin, createdAt)`
- `auth_family(id, accountId, createdAt)`: one sign-in. `access_token` and
  `refresh_token` hang off it, stored as hashes.
- `invite(codeHash, createdBy, expiresAt)`
- `book(id = sha256, fileName, type, sizeBytes, hasCover, metadata…, addedBy,
  addedAt, deleted, seq)` and `book_cover(bookId, bytes)`
- `book_state(accountId, bookId, pageIndex, pageCount, progressChangedAt,
  layout, layoutChangedAt, isFavorite, favoriteChangedAt, seq)`, primary key
  `(accountId, bookId)`
- `reading_session(accountId, id = uuid, bookId, title, startedAt, durationMs,
  seq)`, primary key `(accountId, id)`

Files live at `/data/books/<sha256>`. An upload is written to a temporary file,
its hash is checked, and it is renamed into place.

Each table has its own `seq`, one past the table's highest on every write. Each
`?since=` cursor is a `seq`, which makes pulling changes one indexed range
scan. Every query under `/me` filters on `accountId` taken from the token,
never from the request.

Progress, layout and favourite each carry their own `changedAt`, and each
part keeps whichever change has the larger one, in a conditional `UPDATE` per
part. A favourite set on one device is never undone by another device's
unrelated page turn. A `changedAt` later than the server's clock is stored as
the server's now, so a device with a fast clock cannot win for good.
For a session, the server keeps whichever `durationMs` is larger.

**Security.** Passwords are hashed with Argon2id. Both tokens are 256 random
bits, stored only as hashes. An access token lasts 15 minutes, and a refresh
token lasts 30 days. Each refresh rotates it, and reusing an old one revokes the whole
family. The client keeps its tokens in the existing `SecretStore`. Sign-in
attempts are throttled per username and per IP. The admin web page is the only
HTML the server serves, and it uses the same tokens. Uploaded files are
untrusted input. The server reads only enough of them to identify the format,
reusing the app's own analyser, and never runs them. The reading history now
leaves the device, so the README's privacy line, `DIAGNOSTICS.md` and a new
`docs/SERVER.md` all have to say what the server stores and how deleting an
account removes it.

**Validation and errors.** Usernames are 3 to 32 characters from `[a-z0-9_.-]`.
Passwords must be at least 10 characters. A `pageIndex` or `pageCount` must not
be negative, and `durationMs` is capped at 24 hours. New client errors are
`ServerUnreachable`, `SignInRejected`, `SessionExpired` and `UploadRejected`,
each with a message.

**Async and resilience.** Every request has a 15-second timeout, except
downloads, which only time out after 30 seconds with no data. The outbox is
retried with backoff whenever the app comes to the foreground and after local
writes, debounced by 5 seconds. Repeating a request is always safe, because
book state uses last-writer-wins and sessions use the UUID. A failed sync never
blocks reading. In this version there is no OS background job.

**Performance.** A pull returns at most 500 rows per page. Uploads and
downloads are streamed and never held in memory. Hashing reads the file once,
in chunks; for a 200 MB PDF that is about a second on a phone. The client's
library query and the stats arithmetic stay as they are.

**Non-functional.** The server is meant for a household, roughly 1 to 50
accounts and thousands of books. If it needs more than one instance, the move
to Postgres is the point to revisit.

**Observability.** The server logs one structured line per request (method,
route, status, duration, never a body or token) and exposes `/health`. On the
client, the sync outcome is recorded with the existing `Diagnostics`, as an
event constant and an error type only.

**UI.** The Account section in Settings, a sign-in form, the history prompt, an
"on server" badge on library cards, and download progress on the detail
screen. These reuse the existing design system components. The new text uses
the existing string resources.

**Rollout.** The server is a new image published by `release.yml`. The client
change is opt-in through Settings, so it needs no feature flag. Android needs a
network security config that allows cleartext only for private-network
addresses (see Open Questions).

## Decisions

Recorded in `docs/decisions/`:

1. The server is a self-hosted Ktor service using SQLite and files on disk.
   Postgres and S3 were rejected for this version because they make a
   household install harder (YAGNI).
2. A book is identified by the SHA-256 of its content, not by its location.
3. The client stays offline-first: local SQLite is the source of truth on the
   device, synced through an outbox and pulls by cursor. Book state uses
   last-writer-wins on `changedAt`, and sessions are append-only.
4. The wire DTOs live in a shared KMP module, `:api`.
5. Accounts sign in with a username and password, using opaque access tokens
   and rotating refresh tokens. The first account is the admin, and accounts after
   that need an invite.

## Slices

- [x] 1. `:api` and `:server` modules, `/health`, the server's SQLite schema,
      a Dockerfile, and a CI job that builds and tests the server.
- [x] 2. Accounts: setup, sign-in, refresh, sign-out, invites, delete account,
      admin password reset. The test that one account can't reach another's
      book state comes with slice 4, where that state first exists.
- [x] 3. Library: upload with hash check, listing by cursor, download with
      `Range`, cover, admin delete. Preceded by moving the format readers out
      of `:shared` into `:formats` (with fixtures in `:formats-testing`), with
      no behaviour change.
- [x] 4. Per-account book state: endpoints, last-writer-wins per part
      (progress, layout, favourite), with a change dated in the future counted
      as now. Includes the test that one account cannot reach another's state.
- [x] 5. Reading sessions: endpoints, idempotent by UUID, unique per account.
- [x] 5b. Admin page at the server's root: set up the admin, sign in, upload
      and remove books, make invites. Plain HTML and script served by the
      server, a strict Content-Security-Policy, tokens in memory only.
- [x] 6. Client: migration 11 (content hash, outbox, session uuid) and the
      background hashing pass, with SHA-256 in common code checked against
      the JDK's.
- [x] 7. Client: Account section, sign-in and sign-out, tokens in
      `SecretStore`, health check. Account is reached from a control beside
      the Settings title rather than a sixth segment, which would cut every
      label short on a phone; it arrives in Settings as a slot from the
      navigation, so Settings never depends on the account feature. The
      plain-HTTP rule moved to `core.domain.network.isAllowedEndpoint` and
      holds the server address to the translator's rule. A contract test runs
      the app's client against the real server on a real port.
- [x] 8. Client: server books in the library, download on open, offline after.
      A server book is a shelf row at the location its download will have
      (`document.onServer`, migration 12), so search, filters, favourites and
      progress need nothing new. Opening one that is not downloaded goes to its
      page, which downloads it with progress, cancel and retry; the bytes are
      streamed to disk and checked against the book's hash before the file
      appears. Signing out takes away what was never downloaded.
- [x] 9. Client: book state sync through the outbox and pull. The outbox
      (rebuilt in migration 13 as `kind, key, changedAt`) is written by the
      stores that save progress, layout and favourites, in the same
      transaction; values are read when sent. It is written whether or not
      anyone is signed in, and on first sign-in everything the device already
      knew is queued dated as old as possible, so the account's own changes win
      over it. The engine sends five seconds after the outbox last changed and
      whenever the app comes to the front; changes pulled from the account are
      applied without touching the outbox, and never over a change still
      waiting to be sent.
- [x] 10. Client: session sync, the history prompt on first sign-in, stats
      across devices. Each sitting remembers how much of it the server has
      (`sentDurationMs`, migration 14) and is sent again only once it has
      grown; sittings from other devices arrive as already sent, filed under
      the shelf's copy of the book or `remote:<hash>`, and the statistics count
      them with no change of their own. Where history before the first sign-in
      ends is fixed at the moment of signing in; until the reader answers the
      question under the Account page, none of it is sent, while reading done
      since goes either way.
- [x] 11. Docs: README, `DIAGNOSTICS.md`, `docs/SERVER.md` (running, backup,
      upgrading), ADRs, NOTICE and `checkAttribution` for the server's
      dependencies. The release workflow builds the server image for every
      run and pushes it to `ghcr.io/<owner>/lukia-server` for a tag.

## Resolved Questions

Confirmed with the proposed answers on 2026-10-14:

1. Plain `http://` is accepted only for private-network addresses, behind an
   explicit warning. Every other address needs `https://`.
2. Only the admin may delete a book from the shared library.
3. Sessions for books that aren't on the server still count in the account's
   stats. They are keyed by content hash, and the stats show the title the
   session was recorded with.
4. The server reads metadata and covers from uploads itself, reusing the app's
   analysers. Splitting the format code out of `:shared` into a module the
   server can depend on is part of slice 3.

## Open Questions

None.
