# Reading statistics: one zoomable calendar

Status: Done (revision 2)

Builds on [stats-history.md](stats-history.md). Revision 1 (Week / Month / Year
tabs with a selectable week chart) was built on this branch and tried on the
desktop app; this revision replaces its tabs with one view.

## Goal

Readers should see their reading as one calendar they zoom through rather than
three tabs: the year at a glance, any month in it, and for any day exactly how
long they read.

## Acceptance Criteria

The view:

- [x] The Reading time card has one view with two levels: the **year** and a
      **month** of it. The Week / Month / Year control, the week bar chart and
      the Today headline are removed.
- [x] It opens zoomed into the current month with today selected.
- [x] A day is always selected. Paging to a past month selects its last day
      with reading, or its last day when it has none; the current month selects
      today.
- [x] The selection, the month shown and the zoom live in `StatsViewModel`, so
      they hold through a trip into a book.

Year level:

- [x] Twelve mini months, each a small Monday-first heat map under its short
      name: 3 across on a phone, 4 across on a card 560dp or wider.
- [x] Each mini month is one target: tapping anywhere on it zooms into that
      month. It is announced as one button ("Show November").
- [x] The selected day is marked in its mini month.
- [x] Months still to come are faint; months before the first recorded sitting
      are shown but faint and not tappable.

Month level:

- [x] The compact, selectable calendar from revision 1 (cells capped at 40dp,
      only the weeks the month touches, sliding selection ring).
- [x] The period's name over the calendar, in the accent colour, toggles the
      level: tapping "November 2026" zooms out to the year, and tapping "2026"
      goes back into the same month with the same day selected.

Paging:

- [x] ‹ › step months when zoomed in and years when zoomed out, never past the
      current one or before the first recorded sitting.
- [x] A horizontal swipe does the same, at both levels, and the content slides
      with the finger.

Selected day:

- [x] The readout names the day and its exact time ("Tuesday 24 Nov / 1h 12m"),
      with the streak badge beside it.

Figures:

- [x] They follow the level: zoomed in, the month's time read, daily average,
      days read, change line and longest run; zoomed out, the year's, plus its
      sittings (under the time), best month and best day - six tiles.
- [x] The All time card is removed. Its sittings, best day and longest run
      are counted per year in the year view instead.

Layout and motion:

- [x] Phone: day card, calendar, then figures, in one column. A
      card 560dp or wider puts the calendar on the start side and the readout,
      figures on the end side.
- [x] Kept from revision 1: zoom grows the month out of its mini month's place
      and shrinks back into it; the selection ring slides; other days dim; the
      readout's digits roll; a haptic tick on each new day; the card springs to
      its new height. All fall back to the plain short tween under Reduce
      motion, except the haptic.

Preserved:

- [x] Your library, Books in <year>, the page's loading, failure and
      empty states, and Home's week glance behave as now.
- [x] Every new string is in all 11 locales, German and Dutch formal; strings
      no longer used are removed. Checked at the largest font size.

Detail column (revision 2, styling):

- [x] The selected day sits on an accent-tinted card: a calendar icon by its
      date, the rolling time, a flame chip for the run, a bar for the day
      against the period's busiest day, and "18m more than your average day"
      (or less, or "About your average day"), left out on a day with nothing
      read or still to come.
- [x] The figures are four tiles with icons (clock, chart, calendar, flame or
      sparkle): time read, daily average, days read, and the month's longest
      run or the year's best month. The comparison with the period before is a
      pill, in the accent for a rise and quiet for a fall.

- [x] The day card and the figures keep one height whichever day is tapped: the
      average-day line and the comparison pill hold their space when they have
      nothing to say.

## Out of Scope

- A week level or week chart (removed with revision 2).
- Pinch to zoom.
- The books read on a day. Built, then removed so the day's details keep one
  height; Books in <year> still lists the year's most-read books.
- New data, schema, sync or server changes.

## Impact

| Area | Change |
| --- | --- |
| Features | `stats` only. |
| Data | No schema change. |
| Domain | `ReadingHistory` gains the months and years paging can reach and the sittings on each day; `PeriodReading` gains its sittings. `ReadingHabits` loses the all-time sittings, best day and longest run that only the All time card used. The selection model changes from period kind and offset to month shown, selected day and zoom. |
| UI | Removed: `SegmentedControl` use, `ReadingChart`, `HourAxis`, the Today headline, the year strip `YearGrid`. New: `YearMonths` (twelve mini months), a paged calendar (`HorizontalPager`, Compose foundation), the day card and the figure tiles. |
| Performance | None: every figure is a lookup over the daily totals already grouped once per count. |

## Slices

- [x] 1. Domain: the selection model (month, day, zoom, paging
      bounds), with tests.
- [x] 2. Month level with paging (‹ › and swipe), readout with streak badge.
- [x] 3. Year level: twelve mini months, zoom in and out, year paging.
- [x] 4. Figures per level; remove the tabs, week chart, Today headline and
      unused strings; wide layout.
- [x] 5. Strings in every locale, `docs/ui.md`, UI tests, largest-font check,
      and a run of the desktop app on demo data.

## Decisions

- **Months with nothing to open are disabled**: those before the first sitting
  and those still to come.
- **The streak badge wraps under the readout** when the two don't fit one line,
  as at the largest type.

## Open Questions

- None. Haptics and swipe were checked in JVM UI tests and on the desktop app,
  not yet on an Android or iOS device.
