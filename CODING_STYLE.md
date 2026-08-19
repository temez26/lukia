# Coding Style

How Kotlin is written in this project, and what enforces it.

Two tools split the work. **ktlint** owns formatting — whitespace, wrapping, imports,
commas — and can fix everything it reports. **detekt** owns structure — size, complexity,
naming, correctness, Compose usage — and reports what a formatter cannot decide for you.
A third, project-specific check enforces the file length limit, which neither tool covers.

Everything below is enforced on every pull request unless it says otherwise.

## Running the checks

```bash
./gradlew check
```

That runs the whole verification set, tests included. While working, the individual tasks
are faster:

```bash
./gradlew ktlintFormat
```

```bash
./gradlew ktlintCheck detekt checkFileLength
```

`ktlintFormat` rewrites files in place and fixes nearly every formatting complaint, so run
it before reading a ktlint report. detekt writes an HTML report to
`<module>/build/reports/detekt/detekt.html`.

## Size

**A Kotlin file stays under 400 lines. Aim for 350.**

This is the rule the project cares about most, and the one that drives most of its
structure. A file past 400 lines is doing more than one job: a screen that owns its own
list rows, a view model that also formats its output, a parser holding both its tokens and
its grammar. Splitting it is not cosmetic — it is the point at which the pieces get names.

`checkFileLength` warns from 350 lines and fails the build at 400.

Files that were already over the limit when the rule was introduced are listed in
[`config/file-length/baseline.txt`](config/file-length/baseline.txt). They report as
warnings instead of failures. **Split one and delete its line; never add a line for code
you are writing now.**

Other size limits, enforced by detekt:

| Limit | Value |
|---|---|
| Function length | 60 lines |
| Cyclomatic complexity per function | 15 |
| Nested block depth | 4 |
| Required parameters per function | 6 |
| Required constructor parameters | 7 |
| Class length | 400 lines |
| Functions per file / class | 25 / 20 |

Parameters with default values do not count toward the parameter limits, so a composable
may expose more than six as long as the caller is not forced to pass them. Keep the
required ones — the ones without defaults — at six or fewer, and stay under roughly eight
parameters total before reaching for a state holder.

## Splitting a file

When a file crosses the limit, the split follows the architecture in the
[README](README.md#architecture) rather than the line count:

- **A screen** keeps its layout and its state wiring. Move each self-contained section into
  `presentation/components/` as its own composable — a panel, a row, a toolbar, an overlay.
- **A view model** keeps state and the events the UI raises. Move business rules into
  `application/` as a use case, and formatting into a mapper or an extension.
- **A parser or reader** keeps the entry point. Move each stage — tokenizing, indexing,
  decoding — into its own type in the same `infrastructure/` package.
- **A test class** splits by the behaviour under test, not by the class under test. Four
  focused test files beat one that covers everything a view model can do.

Splitting to satisfy the counter is worse than the long file. If the only split available
is arbitrary, the file is telling you the design is wrong somewhere above it.

## Formatting

Set by [`.editorconfig`](.editorconfig), which the IDE reads too — no separate IDE setup
is needed.

- Four-space indent, no tabs. Lines wrap at **120** characters.
- **Trailing commas everywhere** — parameter lists, argument lists, collection literals.
  Adding a parameter should touch one line, not two.
- **No wildcard imports**, ever. Imports are ordered lexicographically, with `java`,
  `javax`, `kotlin` and aliases last.
- Files end with a newline; no trailing whitespace.

## Naming

- `PascalCase` for types and for `@Composable` functions — a composable names a piece of
  UI, not an action.
- `camelCase` for everything else, including regular functions and local values.
- Design system tokens are `PascalCase`, not `SCREAMING_SNAKE_CASE`, following Compose and
  Material: `LukiaAlpha.Disabled`, `Motion.Fast`.
- A file may hold several related composables or extensions, so its name is not required to
  match one declaration inside it. Name it for what the group is.

## Compose

Enforced by the [compose-rules](https://mrmans0n.github.io/compose-rules/) detekt plugin.

- Every composable that emits UI takes a `modifier: Modifier = Modifier` — named exactly
  that, first among the optional parameters, and applied to the root element.
- Parameter order: required parameters, then `modifier`, then the rest of the optional ones,
  and a trailing `content` lambda last.
- **No mutable state as a parameter.** Pass a value and a callback; a composable that
  receives `MutableState` cannot be reasoned about or previewed.
- A composable either emits UI or returns a value — never both.
- Don't forward a whole view model down the tree. Pass the state a composable needs and the
  callbacks it raises, so it stays previewable and testable.
- `remember` anything whose identity must survive recomposition.
- `@Preview` functions are `private` and named `<Something>Preview`.
- `CompositionLocal` is opt-in: adding one means adding it to the allowlist in
  [`config/detekt/detekt.yml`](config/detekt/detekt.yml), and explaining why the value
  cannot be a parameter.

## Correctness

- **Never catch or throw generic exceptions.** `catch (e: Exception)` and
  `throw RuntimeException(...)` both fail the build; model the failure instead — the
  project already has `AppError` for this.
- Never swallow an exception. If it is genuinely ignorable, say why in a comment.
- Use `require`, `check` and `error` rather than throwing `IllegalArgumentException` or
  `IllegalStateException` by hand.
- Prefer an exhaustive `when` over an `else` branch. When a new case is added, the compiler
  should be the one to find every place that has to handle it.
- At most three `return`s in a function, guard clauses excluded, and at most two `throw`s.
- No `println`. Console output is invisible on a device.
- No `GlobalScope`. Suspending work takes a scope from its caller, and a dispatcher is
  injected rather than hard-coded, so tests can replace it.

## Visibility

Not machine-enforced — no rule in either tool expresses it — but expected in review:

Anything that is not part of a feature's public surface is `internal` or `private`. A
feature exposes its screen, its navigation entry and its DI module; its view models,
components, mappers and infrastructure stay inside. `public` is a decision, not a default.

## Comments

Restated from the [project instructions](.claude/rules/CLAUDE.md), because it is the rule
most often broken:

Comments explain **why**, never **what**. `// increment the index` earns nothing. Write the
reason a non-obvious decision was made, the constraint that forced it, or the consequence
of changing it. If code needs a comment to explain what it does, rename something instead.

KDoc on public API when the name alone cannot carry the meaning. It is not required, and
detekt does not ask for it.

`FIXME:` and `STOPSHIP:` fail the build. Fix it, or open an issue and reference it.

## Suppressions

Both tools can be silenced per declaration, and occasionally should be:

```kotlin
// Named for the Objective-C entry point Xcode calls, not for Kotlin's convention.
@Suppress("ktlint:standard:function-naming")
fun MainViewController() = ...
```

A suppression carries a comment saying why the rule does not apply here. A suppression
without one is a rule violation wearing a hat.

## Baselines

Existing violations are recorded so the checks can be strict about new code without
requiring the whole codebase to be fixed first:

- [`config/detekt/baseline-shared.xml`](config/detekt/baseline-shared.xml) — detekt findings
  that predate the rules. Regenerate with `./gradlew detektBaseline`.
- [`config/file-length/baseline.txt`](config/file-length/baseline.txt) — files that were
  already over 400 lines.

Both shrink over time and neither grows. When a change makes a baselined file clean, drop
its entry in the same pull request — `checkFileLength` warns about entries it no longer
needs.
