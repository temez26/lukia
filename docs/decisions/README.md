# Decisions

Architecture decision records: one file per decision that would be costly to
reverse. A decision is never edited once accepted. A new record supersedes it
and says so.

| | |
| --- | --- |
| [0001](0001-self-hosted-server.md) | A self-hosted server on SQLite and files on disk |
| [0002](0002-content-hash-book-identity.md) | A book is identified by the hash of its content |
| [0003](0003-offline-first-sync.md) | Offline-first sync through an outbox |
| [0004](0004-shared-api-module.md) | The wire types live in a shared `:api` module |
| [0005](0005-password-accounts.md) | Username and password accounts with rotating tokens |
| [0006](0006-palettes-own-shapes-and-type.md) | Each palette brings its own shapes and type |
