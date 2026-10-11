# Contributing

## Branches

- Branch from `main` for new work, and merge back into `main` through a pull
  request. Do not commit directly to `main`.
- Name the branch for what it does: `fix/reader-position`, `feat/word-lookup`.
- Merging to `main` does not release anything. A release is cut by pushing a
  version tag: see [BUILD.md](BUILD.md#releasing).

## Pull requests

The description is pre-filled from
[`.github/pull_request_template.md`](../.github/pull_request_template.md).

- Keep every heading, in order. Write `None` where a section does not apply.
- Say what changed for the user, what changed in the code, and how it was tested.
- Keep it short. The diff has the details.

Before opening one, run what CI does not:

```bash
./gradlew :shared:jvmTest
```

See [TESTING.md](TESTING.md) and [CODING_STYLE.md](CODING_STYLE.md).

## Licence

Lukia is under the [Apache License 2.0](../LICENSE). Contributions are accepted
under the same terms (section 5 of the licence); opening a pull request is all
it takes.
