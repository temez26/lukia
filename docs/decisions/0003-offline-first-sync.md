# 0003: Offline-first sync through an outbox

Status: Accepted, 2026-10-14. Spec: [sync-server](../specs/sync-server.md).

## Context

Reading has to keep working on a train. The app already stores everything in
local SQLite behind repository ports, and signing in is optional.

## Decision

On a device, local SQLite remains the source of truth. A local write is also
recorded in a `sync_outbox` table, which is sent to the server when it can be
reached. Changes from other devices are pulled with a cursor, which is the
server's `seq`.

- Book state (progress, layout, favourite) is last-writer-wins on `changedAt`,
  the time on the device's clock when the change was made.
- Reading sessions are append-only and identified by a UUID the client
  generates. The server keeps the longest `durationMs` it has seen for each
  session.

## Consequences

- Reading never waits for the network. Repeating a request is always safe.
- A device with a badly wrong clock can win or lose an argument it shouldn't.
  That is the same trade-off `ReadingSession.sq` already makes, and only one
  book's position is ever at stake.
- No merge UI and no conflict records.

## Alternatives

- Server-authoritative state, with the client as a cache: simpler, but it
  breaks offline reading and leaves the app without account-free mode.
- CRDTs: they would fix the clock trade-off, but at a cost far beyond what one
  page number per book needs.
