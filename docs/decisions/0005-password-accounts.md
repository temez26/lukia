# 0005: Username and password accounts with rotating tokens

Status: Accepted, 2026-10-14. Spec: [sync-server](../specs/sync-server.md).

## Context

A self-hosted server can't assume an identity provider or an email service.

## Decision

- Passwords are hashed with Argon2id.
- Both tokens are 256 random bits that the server stores only as SHA-256
  hashes. An access token lasts 15 minutes. A refresh token lasts 30 days, and
  each refresh rotates it. Reusing a refresh token that has already been
  rotated revokes every token from the same sign-in.
- The first account is created through `/setup` and is the admin. Later
  accounts need an invite code that the admin creates.
- The client keeps its tokens in the platform `SecretStore`.

## Consequences

- No outside service is needed, and the install stays as simple as ADR 0001
  intends.
- There is no self-service password reset. The admin resets passwords.
- Each request costs one indexed lookup of its token. For one instance that
  is cheaper than it sounds, and signing out or deleting an account takes
  effect immediately, with no signing key to keep or rotate.

## Alternatives

- JWT access tokens: they save the lookup, which only matters when several
  instances can't share a database. That isn't true here (ADR 0001), and a
  JWT can't be revoked before it expires.

- OIDC: better for an organisation that already runs one, but too much to
  require for a household. It can be added later beside passwords.
