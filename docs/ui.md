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
- The reader has its own Light, Sepia and Dark themes. In Light and Dark its
  chrome is the chosen palette and accent at that lightness (`LocalThemeChoice`,
  `readerChromeColorScheme`); the page itself is fixed per reader theme
  (`ReaderPageTheming`) and Sepia keeps its own chrome.

## Pushed screens

Screens pushed over the app (settings, statistics, book details, a home row's
shelf) start with `BackBar`: the back button, an optional title and the screen's
own actions, held still while the content scrolls below it. Don't float a back
button over scrolling content; headings slide under it.

## Settings

Phones and windows show settings differently (`SettingsDestinations`):

- Phone: `SettingsHub`, a grouped list. An account card on top, then one row
  per group with its glyph on an accent-tinted tile, its current value and a
  chevron. Each row pushes the group as its own page (`SettingsAreaScreen`,
  route `settings/{area}`), named in the `BackBar`.
- Window: `SettingsBody`, a sheet of one fixed height (`SettingsOverlay`)
  with the groups down a sidebar, each with its glyph (`SettingsArea.Glyph`),
  Account at the sidebar's foot, and the chosen group beside it.

Both build the groups from the same `SettingsAreaContent`, so a setting reads
the same either way. On a phone each group page provides `LocalPhoneSettingsPage`,
and the groups adapt to it rather than forking:

- `SettingsSection` shows its explanation as a footnote instead of behind ⓘ,
  and drops a heading that only repeats the page title.
- A long list becomes one value row that opens its own page (`SettingsValueRow`,
  the languages at `settings/translating/language`).
- Reading leads with a sample set by the reader's own `readerTextStyle`, shows
  theme and typeface as tiles, and puts the rest behind disclosure rows.
  Appearance shows palettes as a grid of previews (`PaletteGrid`).

## Desktop window

`DesktopShell` opens on the library and pushes everything else over it. It
has no Home and no tab bar: the phone's pages drawn across a monitor read as a
phone stretched out, so the window keeps a layout of its own.

- `ContinueReadingBand`: the last three books being read, as one slim row
  between the filters and the grid. Hidden while the shelf is narrowed or
  being selected from, and on a phone, which has Home for it.
- Settings open over whatever is showing; the shell hosts the overlay.
- A book's details dock at the trailing edge (`BookDetailSidePanel`) and the
  page narrows beside them, so another book can be picked with them open.
- Menus are focusable popups on desktop (`menusTakeFocus`): arrows, Home, End
  and Escape work inside them. A right click opens one at the pointer
  (`MenuPointer`).
- A control's `ShortcutTooltip` is `suppressed` while the panel it opened is
  open.

## Home screen

The mobile home screen leads with the book being read
([ADR 0007](decisions/0007-home-continue-and-shelves.md)):

- `HomeContinueCard`: the most recently read unfinished book, with a Resume
  button. Resume is the only accent-filled control on Home.
- Rows from `homeLayout`: Favorites, Recently Opened, Unread, Recently Added. Each book
  appears once, on its first row, and an empty row is left out.
- A row's title, shown with a muted chevron, opens that row's own screen
  (`HomeShelfScreen`, route `shelf/{name}`): every book the row samples
  (`onShelf`), in a cover grid, without the 12-book cap or the one-book-once rule. Don't add "See All" labels or accent-coloured
  headers.
- Browsing by category lives on the Search tab (`BrowseCategories`), fed by
  `LibraryFacets.browseCategories`: subjects at least two books share, at most 20.

## Reader bar

The reader has one bar, at the top: back, the page indicator (chapter, page,
and how far through the book), and reading settings. The progress through the
book is a thin line along the bar's bottom edge (`ReaderToolbar(progress = …)`).
There is no bar along the bottom of the page.

## Headings in the book

Where a book is split into sections, the headings a section opens with are
marked with a `ChapterRole` (`withChapterOpening`):

- **Title:** set large and bold, with space above and a short accent rule under it.
- **Label:** a short numbered line over the title ("CHAPTER 5:"), set small,
  letter-spaced and in the accent colour.
- **None:** a heading inside a chapter, set in the page theme's accent colour.

Measuring (`BlockMetrics`) and drawing (`HeadingBlock`) both read
`headingStyle` and `headingTopPadding`, so pages are cut to what is drawn.
A `p` or `div` whose class starts with `heading` is read as a heading, which is
how Kindle conversions write them. So is one whose class the book's stylesheet
sets bold and at least 1.15em (`headingClasses`), up to 120 characters long.

A heading never ends a page: when the block after it moves to the next page,
the headings at the foot of the page move with it (`PageBuilder.breakPage`).

## Reader contents panel

`ReaderContentsPanel` is a `TopSheet` on a phone and a popover hanging from
the page indicator on a wider window. On a phone the reader's bar is the
sheet's top row (`ReaderToolbar(merged = true)` drops its own capsule), so bar
and contents are one block. Under the bar: the book (cover, title, author), the
scrubber, then the book's contents.

- Over the scrubber: the chapter at the thumb (it follows a drag), the page and,
  when it is known, how many pages are left in this chapter.
- The list is as tall as the window allows and no taller than its rows. Don't
  give it a fixed height.
- "Pages left in this chapter" counts to the end of the top-level chapter.
- It opens with every chapter folded except the one being read, scrolled so a
  couple of rows above that chapter are visible.
- Chapters already read are in the muted colour. Each chapter shows its length
  in pages, and the row being read has a progress bar under its title.
- A chapter the layout hasn't reached yet (`OutlineEntry.placed == false`)
  shows no page number. Choosing it goes there once the layout reaches it.

`TopSheet` (`components/surface/`) drops from the top and leaves
`topClearance` empty for the bar drawn over it. It is in-window rather than a
popup, so a keyboard and a switch control can reach into it. The handle at its
foot closes it when dragged up or pressed. It is opaque under the glass, because
a sheet tall enough to cover the page would otherwise be read over the page's
text.

## Adding a palette

1. Add the entry to `AppPalette` with a KDoc saying what it is.
2. Add its light and dark `Surfaces` and themed accent in `Color.kt`.
3. Give it corners, an icon-button shape and heading type in `shapesFor`,
   `iconButtonShapeFor` and `typographyFor`.
4. Add `palette_<name>` and `palette_<name>_detail` to every locale.
5. Run `ContrastTest`.

See also `.claude/rules/ui-quality.md`.
