# 0007: Home leads with the book being read

Status: Accepted, 2026-10-16.

## Context

The home screen had grown to six sideways rows (Recent, Recently Added, Almost
Done, Unread, Favorites, Finished) and a row of categories. Each row carried an
orange "See All", the rows overlapped so one book could show three times, and
the book being read had no more weight than any other. Three directions were
mocked up: a continue-reading card above a few shelves (Apple Books, Kindle),
a reading dashboard with stats and an in-progress list (Goodreads, Readwise),
and the current rows cleaned up.

## Decision

The continue-reading card with shelves. Home opens on one card for the book
being read, with a Resume button, then Favorites, Recently Opened, Unread and
Recently Added. Row titles are the links to the library; "See All" goes. A book
shows once, on its first row. Almost Done and Finished leave Home and stay as
library shelves and filters. Browse by category moves to the Search tab.

The dashboard was turned down because the stats page already exists and would
be duplicated; the cleanup because it keeps the overlap between rows and still
gives the current book no priority.

## Consequences

- Home holds at most five sections.
- The Resume button is the only accent-filled control on Home.
- Every book is either opened or unread, so Recently Added, coming last,
  shows only arrivals past the Unread row's dozen and is usually hidden. This
  was chosen over letting it repeat books from the rows above.
