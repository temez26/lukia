# Coding Style

**ktlint** owns formatting. **detekt** owns structure, naming, correctness and
Compose usage. `checkFileLength` enforces the file size limit and
`ArchitectureTest` the package dependencies. All run on every pull request.

## Running the checks

Everything, tests included:

```bash
./gradlew check
```

Fix formatting in place:

```bash
./gradlew ktlintFormat
```

The style checks alone:

```bash
./gradlew ktlintCheck detekt checkFileLength
```

detekt's report is at `<module>/build/reports/detekt/detekt.html`.

## Size

**A Kotlin file stays under 400 lines.** `checkFileLength` warns from 350 and
fails at 400. [`config/file-length/baseline.txt`](../config/file-length/baseline.txt)
lists exceptions and is empty; keep it that way.

| Limit | Value |
| --- | --- |
| Function length | 60 lines |
| Cyclomatic complexity per function | 15 |
| Nested block depth | 4 |
| Required parameters per function | 6 |
| Required constructor parameters | 7 |
| Class length | 400 lines |
| Functions per file / class | 25 / 20 |

Parameters with defaults do not count. `@Composable` functions are exempt from
the parameter limit in detekt and held to eight required parameters in review;
past that, group them into a state holder or an actions object.

### Splitting a file

Split along the architecture, not the line count:

- **Screen:** keeps layout and state wiring. Sections move to
  `presentation/components/`.
- **View model:** keeps state and events. Rules move to a use case in
  `application/`, formatting to a mapper.
- **Parser or reader:** keeps the entry point. Each stage becomes its own type.
- **Test class:** splits by the behaviour under test.

If the only split available is arbitrary, the design is wrong somewhere above.

## Formatting

Set by [`.editorconfig`](../.editorconfig).

- Four-space indent, lines wrap at 120.
- Trailing commas everywhere.
- No wildcard imports.

## Naming

- `PascalCase` for types, `@Composable` functions and design tokens
  (`LukiaAlpha.Disabled`).
- `camelCase` for everything else.
- A file holding several related declarations is named for the group.

## Compose

Enforced by the [compose-rules](https://mrmans0n.github.io/compose-rules/)
detekt plugin.

- A composable that emits UI takes `modifier: Modifier = Modifier`, first among
  its optional parameters, applied to the root element.
- Parameter order: required, `modifier`, optional, trailing `content` lambda.
- No `MutableState` parameters. Pass a value and a callback.
- A composable emits UI or returns a value, never both.
- Do not pass a view model down the tree.
- `@Preview` functions are `private` and named `<Something>Preview`.
- A new `CompositionLocal` must be added to the allowlist in
  [`config/detekt/detekt.yml`](../config/detekt/detekt.yml).

## Correctness

- Do not catch or throw generic exceptions. Model the failure with `AppError`.
- Do not swallow an exception without a comment saying why.
- Use `require`, `check` and `error`.
- Prefer an exhaustive `when` over `else`.
- No `println`. No `GlobalScope`.
- Inject dispatchers and clocks (`TimeSource`) so tests can replace them.

## Architecture

[`ArchitectureTest`](../shared/src/jvmTest/kotlin/io/lukia/ArchitectureTest.kt)
reads every main source file's imports and fails when:

- two features depend on each other in a circle,
- `core` or `shared` imports from `features`,
- a `domain` package imports `application`, `infrastructure` or `presentation`,
- an `infrastructure` package imports `presentation`,
- `core` imports `shared`.

[`PlatformModuleParityTest`](../shared/src/jvmTest/kotlin/io/lukia/di/PlatformModuleParityTest.kt)
holds the three platform DI modules to the same bindings, and
[`AppGraphTest`](../shared/src/jvmTest/kotlin/io/lukia/di/AppGraphTest.kt) resolves
the desktop graph.

Their allow-lists are empty. Adding an entry is a design decision, not a way
past a failing build.

## Visibility

Expected in review, not machine-enforced: a feature exposes its screen, its
navigation entry and its DI module. Everything else is `internal` or `private`.

## Comments

Comments say **why**, never what. Keep them short. If code needs a comment to
explain what it does, rename something.

`FIXME:` and `STOPSHIP:` fail the build.

## Suppressions

A suppression carries a comment saying why the rule does not apply:

```kotlin
// Named for the Objective-C entry point Xcode calls.
@Suppress("ktlint:standard:function-naming")
fun MainViewController() = ...
```

## Baselines

[`config/detekt/baseline-shared.xml`](../config/detekt/baseline-shared.xml) holds
findings that predate the rules. It shrinks and does not grow.

- When a change fixes a baselined finding, remove its entry in the same pull
  request.
- A rule that is right about the code: fix the code, or suppress it in place
  with a reason.
- A rule that is wrong for this codebase: configure it in
  [`config/detekt/detekt.yml`](../config/detekt/detekt.yml) with a comment.
- A new test source set is added to `testSources` at the top of that file.
