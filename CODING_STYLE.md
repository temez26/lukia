# Coding Style

How Kotlin is written in this project, and what enforces it.

Two tools split the work. **ktlint** owns formatting — whitespace, wrapping, imports,
commas — and can fix everything it reports. **detekt** owns structure — size, complexity,
naming, correctness, Compose usage — and reports what a formatter cannot decide for you.
Two project-specific checks cover what neither tool can: a Gradle task for the file
length limit, and a test for the dependency directions between packages.

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

[`config/file-length/baseline.txt`](config/file-length/baseline.txt) lists paths allowed
to exceed it, which report as warnings instead of failures. **It is empty, and the
intention is that it stays that way** — it carried the files that predated the rule, and
every one of them has since been split.

Other size limits, enforced by detekt:

| Limit | Value |
|---|---|
| Function length | 60 lines |
| Cyclomatic complexity per function | 15 |
| Nested block depth | 4 |
| Required parameters per function | 6 (composables exempt — see below) |
| Required constructor parameters | 7 |
| Class length | 400 lines |
| Functions per file / class | 25 / 20 |

Parameters with default values do not count toward the parameter limits, so anything may
expose more than six as long as the caller is not forced to pass them.

`@Composable` functions are exempt from the parameter limit, because a composable's
parameters are its API and detekt cannot hold a screen section and a plain function to
different numbers. **They are held to eight required parameters in review instead.** Past
that, gather the related ones into a state holder or an actions object — which is also
what makes a long screen splittable, rather than trading one long file for several long
signatures.

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

## Architecture

The layers and feature boundaries described in the [README](README.md#architecture) are
held by [`ArchitectureTest`](shared/src/jvmTest/kotlin/io/lukia/ArchitectureTest.kt), which
reads the `package` and `import` lines of every main source set:

- No two features depend on each other in a circle.
- `core` and `shared` never import from `features`.
- A `domain` package never imports `application`, `infrastructure` or `presentation`.
- An `infrastructure` package never imports `presentation`.
- `core` never imports `shared`.

None of these is something the compiler can fail, and the import that breaks one is always
the convenient thing to write at the time. Test sources are not read — a fixture reaching
across features is how a test builds its world, not a dependency the app carries.

[`PlatformModuleParityTest`](shared/src/jvmTest/kotlin/io/lukia/di/PlatformModuleParityTest.kt)
reads the same way, and holds the three `PlatformModule.kt` files to the same set of
bindings. [`AppGraphTest`](shared/src/jvmTest/kotlin/io/lukia/di/AppGraphTest.kt) beside it
is the stronger check and can only ever resolve one platform's graph — it runs on the JVM,
and Android's module needs a `Context`, iOS's a keychain. So what the desktop proves by
resolving, the other two prove by matching it.

Both read files that are not on the test's classpath, so `jvmTest` declares the Kotlin
sources as an input in [`shared/build.gradle.kts`](shared/build.gradle.kts). Without it a
change to `iosMain` alone leaves them reported as up to date, and a check that does not run
when the thing it checks changes is not a check.

Where a rule is broken today, the exact pair — or the exact cycle — is allow-listed in the
test with a comment saying whose job it is to remove. The allow-list is read both ways: an
entry that no longer describes anything fails too, so fixing a violation tightens the rule
rather than leaving a hole where it was. Adding an entry is a design decision, not a way
past a red build.

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

[`config/detekt/baseline-shared.xml`](config/detekt/baseline-shared.xml) records the
detekt findings that predate these rules, so the checks can be strict about new code
without the whole codebase having to be fixed first. Regenerate it with
`./gradlew detektBaseline`.

It shrinks over time and does not grow. When a change makes a baselined finding clean,
regenerate the baseline in the same pull request.

A baseline entry is a finding waiting to be fixed, so nothing else belongs in it. Two
things that look like baseline material are not:

- **A rule that is right about the code.** Fix the code. If it is right about the code and
  the code is right anyway — a boundary that catches `Throwable` on purpose — suppress it
  where it is, with the comment [Suppressions](#suppressions) asks for. A suppression says
  *why* on the line it applies to; a baseline entry says nothing anywhere.
- **A rule that is wrong about this codebase.** Configure it in
  [`config/detekt/detekt.yml`](config/detekt/detekt.yml) and say why in a comment there.
  Silencing the same misfit once per occurrence hides how many there are and reads as debt
  that will never be paid, because there is nothing to fix.

The one to watch is `excludes`. detekt's defaults exempt test sources from the rules that
are only meaningful about production code, but the list they ship names `commonTest`,
`jvmTest` and `iosTest` and has never heard of this project's `androidDeviceTest` — so a
rule silenced everywhere else fires there alone. `testSources` at the top of the config is
that list, corrected; a new test source set is added to it and nowhere else.

The file length baseline beside it is already empty — see [Size](#size).
