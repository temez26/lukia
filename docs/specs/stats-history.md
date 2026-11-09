# Reading statistics: months and years

Status: Done

Mockups: <https://claude.ai/artifact/TUyXpzwRUzucyPBrbDJVgQ> (picked: option A
merged with layout 2, "Calendar").

## Goal

The statistics page shows only the current week, so on a Monday its chart is a
single bar. Readers should be able to see how much they read in any week, month
or year since the app began counting, and which books this year took their time
and which they finished.

## Acceptance Criteria

Reading time card (replaces "This week"):

- [x] Today's time and the current run stay at the top of the card, whichever
      period is shown.
- [x] A `SegmentedControl` switches between Week, Month and Year. Week is
      selected on first open, and the choice holds while the app runs.
- [x] ‹ and › step to the previous and next period. › is disabled on the
      current period. ‹ is disabled on the period holding the first recorded
      sitting.
- [x] The period is named as "This week", "Last week" or "3 weeks ago" for a
      week, "November 2026" for a month and "2026" for a year. Month names come
      from the platform, as weekday names already do.
- [x] **Week:** the seven bars as today, then time read, daily average and days
      read ("4 of 7").
- [x] **Month:** a Monday-first calendar. Each day is shaded in four steps
      relative to the month's busiest day, today is outlined, and days still to
      come are faint. Then time read, daily average, days read and the best run
      within the month.
- [x] **Year:** twelve month bars and a grid of every day of the year, shaded
      the same way. Then time read, days read and the best month.
- [x] Month and year show the change against the same span of the previous
      period ("Compared with the same days of October: +18%"), and leave it out
      when that period has no reading or the change is zero.
- [x] Averages divide by the days reached, never by days still to come.
- [x] A screen reader hears one sentence per calendar day ("Monday 3: 42m",
      the month being named above the calendar). The year grid is announced once, as a summary ("Read on 214 days in
      2026"), not as 365 cells.

Books this year (replaces "Recently read"):

- [x] One card, titled with the year, with Most read and Finished as segments.
- [x] **Most read:** the five books with the most reading time this year, with
      cover, title and time, most first.
- [x] **Finished:** books whose position is at the last page and whose last
      sitting falls in this year, newest first, each with that date ("3 Nov").
- [x] A book removed from the library still appears under its stored title,
      without a cover, and doesn't open. A book on the shelf opens on tap, as
      Recently read did.
- [x] Each segment has its own empty line ("Nothing read yet this year",
      "No books finished yet this year").

Page:

- [x] Order on a phone: Reading time, All time, Your library, Books this year.
      A wide window pairs them as now: [Reading time | All time], then
      [Your library | Books this year].
- [x] With no sittings at all, the time sections stay hidden as they are now.
- [x] Home's week glance is unchanged.
- [x] Every new string is in all 11 locales, with German and Dutch addressing
      the reader formally. Checked at the largest system font size.

## Out of Scope

- A daily goal or progress ring.
- Pages read per period (sittings don't record pages).
- A stored finish date. Finished is dated by the last sitting instead (see
  Decisions).
- Remembering the selected period across launches.
- Changes to Home, the reader or the sync server.

## Flow

1. The reader opens Statistics. The Reading time card shows this week.
2. They tap Month. The card shows this month's calendar and figures.
3. They tap ‹ twice. The card shows the month two months back. › brings them
   forward again, and stops at the current month.
4. They tap Year, and the card shows the current year.
5. Further down, Books this year lists the most-read books. Tapping Finished
   lists what they finished this year.

States: loading, failure and empty are the page's existing ones (`CountedContent`).
A period with no reading shows its empty chart or calendar and dashes in its
figures.

## Impact

| Area | Change |
| --- | --- |
| Features | `stats` only. `core`: `ReadingSession` gains the `title` its table already stores, and a platform `monthName` beside `weekdayName`. |
| Contracts | None. |
| Data | No schema change. The repository maps the existing `title` column. |
| Performance | Sittings are already read whole. They are grouped by day once per refresh, and each period is a lookup over at most 366 days. |
| Security | None. |
| UI | Reuses `GroupSection`, `SegmentedControl`, `StatFigure`, `StatusBadge` and the book rows of `RecentReadingList` (now `BookRows.kt`). New stats components: the period pager, the month calendar and the year grid. `ReadingChart` takes generic bars so months can be charted as well as days. |
| Rollout | One release. |

## Decisions

- **Finish date = date of the last sitting in a finished book.** Nothing new is
  stored or synced. A book reread after finishing moves to its latest date. A
  book finished before the app began counting has no sittings, so it has no
  date and isn't listed.
- **Shading is relative to the period's busiest day**, as the week chart already
  is, rather than fixed minute thresholds.
- **The selected period lives in `StatsViewModel`**, which already outlives a
  trip into a book.

## Slices

- [x] 1. Domain: daily totals and the period model (range, days, totals, days
      read, best run, previous-period comparison), with tests.
- [x] 2. Reading time card: Today headline, segmented control, pager and the Week
      view, replacing This week.
- [x] 3. Month view: the calendar.
- [x] 4. Year view: month bars and the year grid.
- [x] 5. Books this year: most read and finished, with `ReadingSession.title`.
      Remove Recently read.
- [x] 6. Strings in every locale, wide layout, `docs/ui.md`, largest-font check.

## Open Questions

- None.
