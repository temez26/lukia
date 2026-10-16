# UI

The design system lives in `shared/src/commonMain/kotlin/io/lukia/shared/presentation/`:
`theme/` for tokens, `components/` for shared controls. Built on Compose
Multiplatform 1.12 and Material 3, styled in Apple's proportions.

## Themes

A theme is an `AppPalette`, chosen in Settings > Appearance, in light and dark.

| Palette | Colour | Shapes | Type |
| --- | --- | --- | --- |
| Crisp (default) | Apple system greys, blue accent | `LukiaShapes` (4-20dp) | `LukiaTypography`, system sans |
| Paper | Warm paper and ink, deep blue accent | `LukiaShapes` | `LukiaTypography` |
| Slate | Cool neutral greys, amber accent | `LukiaShapes` | `LukiaTypography` |
| Ledger | Black on white, true black at night, red accent | `LedgerShapes` (0-4dp) | Serif bold headings, sans body |

- Colours: six surface roles per palette and lightness in `theme/Color.kt`.
  `ContrastTest` holds every palette to 4.5:1 for text.
- Shapes and type: `shapesFor` and `typographyFor` in `LukiaTheme.kt` and
  `Type.kt`. See [ADR 0006](decisions/0006-palettes-own-shapes-and-type.md).
- Use `MaterialTheme.shapes` and `MaterialTheme.typography` rather than literal
  shapes and fonts, so a palette can change them. Circles are the exception,
  for things that are round in every theme.
- The reader's page has its own Light, Sepia and Dark themes, independent of
  the palette.

## Adding a palette

1. Add the entry to `AppPalette` with a KDoc saying what it is.
2. Add its light and dark `Surfaces` and themed accent in `Color.kt`.
3. Add its shapes or type to `shapesFor` / `typographyFor` if it changes them.
4. Add `palette_<name>` and `palette_<name>_detail` to every locale.
5. Run `ContrastTest`.

See also `.claude/rules/ui-quality.md`.
