# 0002: A book is identified by the hash of its content

Status: Accepted, 2026-10-14. Spec: [sync-server](../specs/sync-server.md).

## Context

On a device, a book is identified by its `location`, which is a file path or a
content URI. Another device, or another person, has the same book at a
different location, so a location can't carry progress or statistics from one
device to the next.

## Decision

Across devices, a book is identified by the lowercase hex SHA-256 of its file's
bytes. The server uses that hash as the book's id. The client stores it beside
the location and keeps using the location for everything that stays on the
device.

## Consequences

- Two copies of the same file are the same book wherever they are, so uploading
  the same file twice is a no-op.
- A file whose bytes change, such as an EPUB edited in Calibre, becomes a new
  book. Its progress doesn't carry over. That is accepted, because a changed
  file may also have changed pages.
- Hashing reads the whole file once. That happens at import and, for books
  already in the library, once in the background.

## Alternatives

- Metadata such as ISBN, or title and author: often missing, and it can't tell
  two editions apart.
- Ids assigned by the server: these can't match a book the device already had
  before it signed in.
