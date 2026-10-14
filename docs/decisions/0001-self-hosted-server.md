# 0001: A self-hosted server on SQLite and files on disk

Status: Accepted, 2026-10-14. Spec: [sync-server](../specs/sync-server.md).

## Context

Accounts, a shared library and per-account reading state need a server. It is
run by a reader for their household or friends, on a home server or a small
VPS, not as a public service.

## Decision

The server is a Kotlin Ktor application in the `:server` module, shipped as one
Docker image. It stores its data in SQLite, through SQLDelight's JVM driver,
and keeps the book files in a directory beside the database. Everything it
writes lives in one volume.

## Consequences

- Installing it is one container and one volume. Backing it up means copying
  one directory.
- The server is written in the same language as the app, with the same
  database tool, so the people who maintain the app can maintain the server.
- One instance only. SQLite allows one writer at a time, which is plenty for a
  household. Running more than one instance, or serving the public, means
  moving to Postgres and object storage. That move is a new decision.

## Alternatives

- Postgres and S3: what a public service needs, but they turn a one-container
  install into three services for a use that doesn't need them.
- A backend-as-a-service such as Firebase or Supabase: the reader's history
  would sit with a third party, which goes against the app's privacy stance.
