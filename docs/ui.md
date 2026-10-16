# UI

The design system lives in `shared/src/commonMain/kotlin/io/lukia/shared/presentation/`:
`theme/` for tokens, `components/` for shared controls. Built on Compose
Multiplatform 1.12 and Material 3, styled in Apple's proportions.

## Themes

A theme is an `AppPalette`, chosen in Settings > Appearance, in light and dark.

| Palette | Colour | Shapes | Type |
| --- | --- | --- | --- |
| Crisp (default) | Apple system greys, blue accent | 4-20dp, round icon buttons | System sans, bold headings |
| Paper | Warm paper and ink, deep blue accent | 6-28dp, round icon buttons | Noto Serif regular headings |
| Slate | Cool neutral greys, amber accent | 2-8dp, rounded-square icon buttons | Heavier, tighter sans headings |
| Ledger | Black on white, true black at night, red accent | 0-4dp, square icon buttons | Noto Serif bold headings |

Body text and labels are the system sans in every palette.

- Colours: six surface roles per palette and lightness in `theme/Color.kt`.
  `ContrastTest` holds every palette to 4.5:1 for text.
- Shapes and type: `shapesFor` and `iconButtonShapeFor` in `PaletteShapes.kt`,
  `typographyFor` in `Type.kt`. See [ADR 0006](decisions/0006-palettes-own-shapes-and-type.md).
- Use `MaterialTheme.shapes` and `MaterialTheme.typography` rather than literal
  shapes and fonts, so a palette can change them. Icon buttons read
  `LocalIconButtonShape`: a circle, square in Ledger. Literal circles are for
  things that are round in every theme, such as swatches and selection marks.
- The reader's page has its own Light, Sepia and Dark themes, independent of
  the palette.

## Adding a palette

1. Add the entry to `AppPalette` with a KDoc saying what it is.
2. Add its light and dark `Surfaces` and themed accent in `Color.kt`.
3. Give it corners, an icon-button shape and heading type in `shapesFor`,
   `iconButtonShapeFor` and `typographyFor`.
4. Add `palette_<name>` and `palette_<name>_detail` to every locale.
5. Run `ContrastTest`.

See also `.claude/rules/ui-quality.md`.
