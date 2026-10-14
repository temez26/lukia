# 0004: The wire types live in a shared `:api` module

Status: Accepted, 2026-10-14. Spec: [sync-server](../specs/sync-server.md).

## Context

The app and the server must agree on every request and response. Writing the
types twice would let them drift apart quietly.

## Decision

A Kotlin Multiplatform module, `:api`, holds the `@Serializable` request and
response types and the error codes. `:server` and `:shared` both depend on it.
The module holds no logic and depends on nothing but kotlinx.serialization.

## Consequences

- A change to the contract is a compile error on whichever side hasn't caught
  up.
- `:api` builds for every target the app has (Android, iOS, JVM), so it can't
  use JVM-only types.
- An app already installed may talk to a newer server. Fields are only ever
  added, with defaults, inside `/api/v1`. A change that would break that goes
  under `/api/v2`.
