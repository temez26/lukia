# 0006: A palette may bring its own shapes and type

Status: Accepted, 2026-10-16.

## Context

Crisp, Paper and Slate differ only in colour. A fourth theme, Ledger, was
wanted that feels like a different app: an editorial black-and-white look with
serif headings, square corners and a red accent. Three directions were mocked
up (Ink, Sage, Ledger) and Ledger was chosen.

## Decision

`LukiaTheme` asks the palette for its `Shapes` and `Typography` as well as its
colours (`shapesFor`, `typographyFor`). Ledger supplies square corners (0-4dp
on the same five steps) and the platform serif, bold, for display, headline and
title styles. Body text and labels stay in the system sans. The other palettes
return the shared `LukiaShapes` and `LukiaTypography`.

The serif is bundled: Noto Serif Bold, under the SIL Open Font License 1.1
(about 480 KB). The platform serif was tried first, and on a OnePlus phone the
maker's font setting mapped it to the system sans, so Ledger's headings came
out looking like everyone else's.

## Consequences

- Components that read `MaterialTheme.shapes` and `MaterialTheme.typography`
  follow the palette for free. Icon buttons, which are circles elsewhere, read
  `LocalIconButtonShape` and are square in Ledger. Other circles - swatches,
  selection marks, chart bars - stay round.
- The reader keeps its own page themes (Light, Sepia, Dark) and its own chrome,
  as it already did for every palette.
- Ledger's accent red sits near the error red, so a destructive button and a
  primary one are close in colour. Destructive actions are still confirmed in a
  dialog that names them.
- A future palette can change shapes or type without touching any screen.
