# 0005: Username and password accounts with rotating tokens

Status: Accepted, 2026-10-14. Spec: [sync-server](../specs/sync-server.md).

## Context

A self-hosted server can't assume an identity provider or an email service.

## Decision

- Passwords are hashed with Argon2id.
- An access token is a JWT signed by the server, and lasts 15 minutes. A
  refresh token is 256 random bits, stored only as a hash, and lasts 30 days.
  Each refresh rotates it. Reusing a refresh token that has already been
  rotated revokes every token descended from it.
- The first account is created through `/setup` and is the admin. Later
  accounts need an invite code that the admin creates.
- The client keeps its tokens in the platform `SecretStore`.

## Consequences

- No outside service is needed, and the install stays as simple as ADR 0001
  intends.
- There is no self-service password reset. The admin resets passwords.
- The JWT signing key is generated on the first start and kept in the data
  volume. Restoring a backup restores it too, so tokens issued before the
  backup stay valid.

## Alternatives

- OIDC: better for an organisation that already runs one, but too much to
  require for a household. It can be added later beside passwords.
