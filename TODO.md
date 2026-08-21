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

## 8. Paragraph translations report themselves as "cut short" ✅

**Reported:** paragraph translation still cuts out, while the same local model
translates the passage perfectly well when asked directly.

**Cause:** found by replaying the app's exact request against the running LM
Studio server. `OpenAiTranslator` sent `max_tokens = 1024` — but that is a
ceiling on the model's **thinking and its answer together**, and Gemma-4 streams
its thinking on `reasoning_content`, a field the app never reads. So the budget
was being spent invisibly, and the stream ended saying `length` with nothing to
show. Measured against `google/gemma-4-e4b` on the reported paragraph:

| passage | `max_tokens` | outcome |
|---|---|---|
| one sentence | 1024 | `stop`, **31 tokens** — never came near the ceiling |
| one paragraph | 1024 | **`length`** — 3,094 chars of thinking, answer half written |
| one paragraph | 4096 | `stop`, 497 tokens |

That is exactly the shape reported: sentences translated, paragraphs did not.
The Anthropic translator already documented this ("a ceiling on the thinking and
the reply together") and budgeted 4096; the OpenAI path never got the same
treatment, and local models are now overwhelmingly reasoning models.

- [x] New shared `tokenAllowanceFor`, used by both translators: the passage's
      own length over a floor of 4096, capped at 8192. A ceiling, not a target —
      a model that answers in fifty tokens is charged for fifty.
- [x] The message no longer leads with "try a shorter passage", which pointed at
      the wrong thing. It names the model and says it is charged for thinking.
- [x] Verified against the running server: the reported paragraph goes from
      `finish_reason: length` to `stop` with a translation the app parses.

**Noticed while reproducing, not fixed** — `openai/gpt-oss-20b` on this machine
fails perhaps half the time with an LM Studio engine error mid-stream (`event:
error`, "output that does not match the expected peg-native format"). That is a
server/model fault rather than this app's, but the app reads it as a generic
parse failure because it ignores SSE `error` events. Worth its own look.

## 9. On a PDF, tapping the translation does not put the words back ✅

**Reported:** the translation floats over the page, and tapping it does nothing
— it only closes when the *original* text is tapped. Worst on single words.

**Asked:** is there no way to replace the words in place, as in an EPUB?

**Answer:** no, and there cannot be. A PDF page is a rendered picture with a
list of glyph positions; there is no text to re-set, so a translation can only
be laid *over* the page. The floating panel is the right shape for it. What was
wrong is that only the panel's *appearance* was over the page — its *taps* were
not.

**Cause:** the panel starts at the passage's corner and is free to run wider and
further down than the passage ever did, because a translation is rarely the
length of what it stands for. But a tap was hit-tested against the original
glyph rectangles, so most of the visible panel was over nothing that answered.
For a single word it was hopeless: the panel is several times the size of the
word beneath it, so nearly every tap landed on the sentence around it instead —
and with the tap set to Sentence, that translated the sentence rather than
putting the word back.

- [x] The panel answers for itself, and names the passage it covers by a point
      the page recognises as inside it. No rectangle bookkeeping: Compose's own
      hit-testing decides, and a tap the panel answers never reaches the page.
- [x] `PagePassages.translationAt` — what a tap on a translation asks. It takes
      no gesture setting, because a covered passage leaves nothing ambiguous.
- [x] The same rule now applies to taps on whatever is left uncovered: a passage
      with its translation drawn over it is addressed as itself, whatever the
      gesture was set to.
- [x] A hold on the panel means the same as a tap — with the words underneath
      covered, there is no second thing for a second gesture to mean.
- [x] Both new tests were confirmed to **fail** without the fix.

`PageTranslationOverlay` was over the file-length limit after this, so the panel
moved to `PageTranslationPanel.kt` — which is a cleaner seam anyway: one file
decides *which* passages are marked, the other draws *what* stands in for them.

---

## Verification

- [x] `:shared:compileAndroidMain`, `:shared:compileKotlinJvm`,
      `:shared:compileCommonMainKotlinMetadata`, `:desktopApp:compileKotlin`
- [x] `:shared:jvmTest` — 995 tests, all passing
- [x] `:shared:detekt`, `:shared:ktlintCheck`, `:shared:checkFileLength`
- [x] Detekt baseline regenerated; it lost five findings the refactoring cleaned

**Tests changed**, because they pinned behaviour this asked to change:

- Six tests asserted that one tap toggles the chrome — now double taps, plus a
  new test that a single tap leaves the chrome alone.
- `aTapSetToTheParagraphTakesTheSentenceOnAPage` becomes
  `aTapSetToTheParagraphTakesTheParagraphOnAPage`, plus a new test that a gesture
  inside a translated paragraph on a page asks about the paragraph.
- `anAnswerCutShortSaysWhyRatherThanFailingToParse` now checks the new wording,
  plus three new tests pinning the floor, the scaling and the ceiling of the
  token allowance.

Not verified by running the app against a real PDF — the block inference is
tested against glyphs laid out by hand, so how it divides a particular book's
pages is worth a look on real files.
