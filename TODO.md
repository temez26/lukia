# Bug fixes — reader and translation

Branch: `fix/reader-and-translation-bugs` (from `dev`).

Each entry says what was reported, what was actually wrong, and what was done
about it.

---

## 1. Paragraph translation still translates sentences on a PDF ✅

**Reported:** setting the gesture to Paragraph translates a sentence instead, at
least in a PDF.

**Cause:** deliberate, and wrong. `ReaderPageList` fell back to
`PassageUnit.Sentence` on a page because "a PDF records no paragraphs" — but
`pageSentences` already infers blocks from the space left between lines (it has
to, or a heading and the footnote under it run into one sentence). It simply
threw that grouping away instead of keeping it.

- [x] `PageSentence` carries the `blockIndex` it was read out of, so the
      inference that was already being made is no longer discarded.
- [x] `TextAnchor.Page` gains a `blockIndex`, giving a whole paragraph of a page
      somewhere to be filed. Only a paragraph carries it — a sentence is still
      addressed by its number on the page alone, so the same sentence tapped and
      asked for in bulk stays one passage rather than two.
- [x] `SentenceTranslations` files whole paragraphs of a page by block.
- [x] A paragraph gesture on a page sends the whole block, and
      `PageTranslationOverlay` draws one panel over all of its lines.
- [x] New `PagePassages` holds "what is on this page and what a point on it
      means", which also brought `ReaderPageList` back under the 400-line limit.

## 2. The wait for a translation is too short ✅

- [x] Default silence allowed raised: hosted providers 60s → 120s, a local model
      300s → 600s.
- [x] Ceiling the reader may set raised from half an hour to a full one
      (`TimeoutSecondsRange` 5..3600).

The timeout was already a measure of *silence* rather than of total time, so a
long answer still never times out while it is arriving.

## 3. A paragraph being translated shows no animation ✅

**Cause:** a paragraph waiting on its first words has nothing to put on the page,
so it is drawn as the book's own sentences — and the mark for it was filed under
the whole-block index, which none of those sentences carries. The sweep
animation was running the whole time and had nothing to draw.

- [x] `MarkedPassages.marksFor` marks the whole of the drawn paragraph when the
      block is marked but its own sentences are still on the page.
- [x] The same paragraph on a PDF gets its sweep from the overlay change in 1.

## 4. Tapping a finished translation opens the menu instead of flipping back ✅

**Cause:** two of them, both fixed.

- [x] A paragraph translated whole is drawn as one span filed under `WHOLE_BLOCK`
      (-1). The tap handler looked -1 up in the book's sentences, found nothing,
      and treated the tap as a tap on the page — which toggled the chrome. A
      gesture inside a paragraph translated whole is now a gesture on that
      paragraph, whatever unit it was set to.
- [x] Same rule on a PDF: a gesture inside a block that already has a
      translation addresses the block.
- [x] As asked, the chrome now needs a **double** tap. A new `detectReaderTaps`
      does this without the cost `detectTapGestures` would charge: given a
      double-tap handler it delays *every* tap by the double-tap window, so
      instead the tap callback itself reports whether the text answered it, and
      only an unanswered tap waits to see if a second follows. A tap on a
      passage is still acted on immediately.

## 5. The reader's toolbar loses its controls to a long chapter name ✅

**Cause:** the page indicator sits between the back button and the settings and
translation controls with no width of its own to give up. A `Row` measures its
unweighted children in order, handing each everything still unclaimed — so a long
chapter name took the whole bar and the controls after it were measured at
nothing. Not a bar that overflows: a bar whose controls silently disappear.

- [x] The indicator is held to half the bar and ellipsises inside it.
- [x] The book title in the wide layout yields the same way.

## 6. Desktop shortcuts stop working, especially once the chrome is hidden ✅

**Cause:** the reader took keyboard focus once, when it opened. Pressing a
toolbar button moves focus into the chrome — and hiding the chrome then takes
that focused control out of the window, leaving nothing focused and no key
handler to reach.

- [x] The reader takes focus back whenever it loses it entirely. Focus on a
      control *inside* the reader is left alone, so Space on a focused segment
      still chooses it rather than turning a page.

## 7. The import card is enormous in list view ✅

**Cause:** the import slot is a blank book cover — `fillMaxWidth` at a cover's
aspect ratio — and in list view the grid is one column, so it was drawn as wide
as the window and half again as tall.

- [x] New `ImportBookRow`: a row among rows, at a book row's height, with the
      same menu hung off it. The grid still gets the card.

---

## Verification

- [x] `:shared:compileAndroidMain`, `:shared:compileKotlinJvm`,
      `:shared:compileCommonMainKotlinMetadata`, `:desktopApp:compileKotlin`
- [x] `:shared:jvmTest` — 988 tests, all passing
- [x] `:shared:detekt`, `:shared:ktlintCheck`, `:shared:checkFileLength`
- [x] Detekt baseline regenerated; it lost five findings the refactoring cleaned

**Tests changed**, because they pinned behaviour this asked to change:

- Six tests asserted that one tap toggles the chrome — now double taps, plus a
  new test that a single tap leaves the chrome alone.
- `aTapSetToTheParagraphTakesTheSentenceOnAPage` becomes
  `aTapSetToTheParagraphTakesTheParagraphOnAPage`, plus a new test that a gesture
  inside a translated paragraph on a page asks about the paragraph.

Not verified by running the app against a real PDF — the block inference is
tested against glyphs laid out by hand, so how it divides a particular book's
pages is worth a look on real files.
