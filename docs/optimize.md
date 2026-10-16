# Optimization audit

**Date:** 2026-10-16
**Scope:** the changes on `fix/ui-polish` against `main` (UI polish, the Ledger theme, per-palette shapes and type, the themed reader chrome).
**Stack:** Kotlin Multiplatform with Compose Multiplatform 1.12 (Android, desktop, iOS).

## Findings

| # | Finding | Category | Location | Status |
| --- | --- | --- | --- | --- |
| 1 | The bundled serif was read on every launch, for every palette | Unneeded work | `theme/LukiaTheme.kt`, `theme/Type.kt` | Fixed, 3e68d08e |
| 2 | The sign-in form's "can submit" rule was written twice | Duplicated logic | `account/presentation/SignInForm.kt` | Fixed, 1f24a137 |
| 3 | Every segment label was auto-sized on every layout | Recomputation | `controls/SegmentedControl.kt` | Fixed, 7e4b48af |
| 4 | Seven exhaustive `when (palette)` blocks | Possible duplication | `Color.kt`, `PaletteShapes.kt`, `Type.kt`, `ThemeChoicesDisplay.kt` | Won't fix |

### 1. The bundled serif was read on every launch

`LukiaTheme` called `bundledSerif()` whatever the palette. In Compose
Multiplatform's resource library, desktop reads a resource font with
`runBlocking` during the first composition, and iOS reads it asynchronously,
then swaps it in, which redraws every line of text. Crisp and Slate paid for
about 960 KB of fonts they never draw. Android reads fonts lazily and was not
affected.

Fix: `rememberTypography` reads the serif only when `setsHeadingsInSerif()`.
`typographyFor` is still the one mapping from palette to type, and
`PaletteStyleTest` holds the two to each other.

**Measured** on desktop, cold first composition of `LukiaTheme` with Crisp,
5 runs each: median 527 ms before, 499 ms after (about 28 ms per launch). The
iOS saving (no font read and no two whole-app redraws) is **estimated** from
the library source; it could not be measured without a Mac.

### 2. The "can submit" rule was written twice

The keyboard's Go action and the submit button's enabled state each checked
`complete && !busy`. It is now worked out once as `canSubmit`. A new test in
`AccountSettingsTest` holds the Go key to the same terms as the button.

### 3. Every segment label was auto-sized on every layout

Shrink-to-fit lays a label out at several sizes on every layout, and it ran
for every segment of every segmented control, although almost every label
fits. A segment now sets its label at full size and switches to auto-sizing
only after a layout shows it overflowing. The rendered result is identical
before and after (compared from screenshots).

**Measured** on desktop, 20 controls of 5 segments re-laid out 20 times,
5 runs each: median 1017 ms before, 670 ms after.

### 4. Seven exhaustive `when (palette)` blocks

Won't fix. Each answers a different question (surfaces, accent, shapes,
icon-button shape, type, name, description), and the compiler fails the build
when a new palette misses one. That is the project's own convention
(`CODING_STYLE.md`: prefer an exhaustive `when` over `else`), not duplicated
logic.

## Found along the way

Measuring finding 3 showed that shrink-to-fit, added in the polish pass, did
not shrink a label that was too wide: with `TextOverflow.Ellipsis` a label cut
short counted as fitting, so the selected "Appearance" in a 300dp control was
shown as "Appeara…". It had only seemed to work on a phone at 200% text,
where the label overflowed in height.

Fixed separately: a label is set at full size, shrinks (clipped, so the
shrinking can see it does not fit) when it overflows, and is ellipsised at the
smallest size only if it still does not fit there. It is fitted afresh when
the text, the segment's width or the system text size changes.
`SegmentedControlFitTest` covers all three cases. Re-fitting on a width change
costs some of finding 3's saving back: the same benchmark measures a median of
771 ms (1017 ms originally, 670 ms with the earlier version that never shrank).

Checked on a phone at 200% text: "Aussehen" now shrinks whole; "Appearance",
"Translating" and "Übersetzen" do not fit even at the smallest size and are
ellipsised there.
