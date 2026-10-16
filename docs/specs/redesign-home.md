# Home screen redesign

Status: Done

## Why

The mobile home screen stacks up to six sideways rows, each with its own orange
"See All", plus a row of category chips. The accent repeats until it means
nothing, the same book sits on three rows at once, a row with one book leaves a
blank strip, and carrying on with the current book - the main reason to open
the app - is not given more weight than any other row.

## Scope

- The mobile home screen only (`HomeScreen`, `HomeRows`, `HomeShelfRow`,
  `HomeShelves`, `HomeCategoryRow`) and moving category browsing to the Search
  tab. Desktop, the library tab and the reader are unchanged.
- Layout changes allowed; no new features. Every destination stays reachable:
  Almost Done and Finished remain library shelves and filters.
- Existing theme tokens and components; no new design system.

## Direction

Continue + shelves (see [ADR 0007](../decisions/0007-home-continue-and-shelves.md)):

1. **Header** - "Home", stats and the library menu, both as plain icon buttons.
2. **Continue reading** - one card for the most recently read book still being
   read: cover, title, author, progress bar and percentage, and a Resume button
   (the screen's one accent action). Left out when nothing is being read.
3. **Favorites**.
4. **Recently Opened** - every opened book, finished ones included.
5. **Unread**.
6. **Recently Added** - only what the rows above do not already show.

Rows:

- The title is the link to the library tab, shown as `Title ›` with a muted
  chevron. No "See All" label.
- A book appears once on the screen, on the first row that holds it.
- A row with no books is left out.

## Goals to check at the end

- [x] No "See All" text on the home screen; no accent colour on row headers.
- [x] No book appears twice on the screen.
- [x] Resuming the current book is one tap from launch.
- [x] At most five sections below the header.
- [x] Categories are browsable from Search, not Home.
- [x] Every string in every locale; checked at the largest font size.

## Screens

- [x] Shared: tappable row header component
- [x] Home: continue-reading card
- [x] Home: row model (dedupe, fewer rows)
- [x] Search: browse by category
- [x] Home: header buttons match (menu button plain, on Home and the library tab)
- [x] Update `docs/ui.md`
- [x] Polish pass, before/after screenshots
