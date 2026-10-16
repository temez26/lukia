# 0006: Each palette brings its own shapes and type

Status: Accepted, 2026-10-16.

## Context

Crisp, Paper and Slate differ only in colour. A fourth theme, Ledger, was
wanted that feels like a different app: an editorial black-and-white look with
serif headings, square corners and a red accent. Three directions were mocked
up (Ink, Sage, Ledger) and Ledger was chosen. Once Ledger had shapes and type
of its own, the other three looked like one theme in three colours, so each was
given an identity as well.

## Decision

`LukiaTheme` asks the palette for its `Shapes`, `Typography` and icon-button
shape as well as its colours (`shapesFor`, `typographyFor`,
`iconButtonShapeFor`). Only display, headline and title styles change; body
text and labels stay in the system sans in every palette.

| Palette | Corners | Icon buttons | Headings |
| --- | --- | --- | --- |
| Crisp | 4-20dp (`LukiaShapes`) | Circle | System sans, bold |
| Paper | 6-28dp, soft | Circle | Noto Serif, regular weight |
| Slate | 2-8dp, tight | Rounded square | System sans, heavier and tighter |
| Ledger | 0-4dp, square | Square | Noto Serif, bold |

The serif is bundled: Noto Serif Regular and Bold, under the SIL Open Font
License 1.1 (about 960 KB together). The platform serif was tried first, and on
a OnePlus phone the maker's font setting mapped it to the system sans, so
Ledger's headings came out looking like everyone else's.

## Consequences

- Components that read `MaterialTheme.shapes` and `MaterialTheme.typography`
  follow the palette for free. Icon buttons, which are circles elsewhere, read
  `LocalIconButtonShape`. Other circles - swatches,
  selection marks, chart bars - stay round.
- The reader's toolbar and panels wear the chosen palette and accent at the
  reader's own lightness (Light or Dark), through `LocalThemeChoice`. The page
  itself - its paper, ink and the fills of quotations and passage marks - stays
  fixed per reader theme in every palette, and Sepia keeps its own chrome.
- Ledger's accent red sits near the error red, so a destructive button and a
  primary one are close in colour. Destructive actions are still confirmed in a
  dialog that names them.
- A future palette can change shapes or type without touching any screen.
- Slate's character leans on weight, which a phone whose font has no heavier
  weights cannot show. There it stands apart by its corners alone.
