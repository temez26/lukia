package io.lukia.features.reader.presentation.translation

import io.lukia.core.domain.toAppError
import io.lukia.features.reader.domain.text.TextAnchor
import io.lukia.features.translate.application.TranslatePassageUseCase
import io.lukia.features.translate.domain.TranslationRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * How many translated passages one open book keeps.
 *
 * Far more than a reader gets through in a sitting, and small enough that the
 * map is cheap to copy - which it is on every single state change, because the
 * whole screen reads it. Without a ceiling, a long evening with a long book
 * turns every tap into a copy of every translation made since it was opened.
 *
 * What falls out is the least recently touched, which is the part of the book
 * furthest behind the reader. Tapping it again asks for it again - and by then
 * the answer is in the stored cache, so asking costs nothing.
 */
private const val MAX_KEPT_PASSAGES = 400

/**
 * How many passages a standing order has in flight at once.
 *
 * One tap has never needed this. Translating what is on screen does, and the
 * number matters more than it looks, because a model on the reader's own
 * machine is one machine: asking it four things at once does not answer them
 * four times faster, it answers all four at the end. Measured on a local model,
 * four blocks of a page:
 *
 *     one at a time    34s in total, and one arrived every 8s
 *     two at a time    23s in total, first one at 12s
 *     four at once     18s in total, and nothing at all until 18s
 *
 * Two, because a page filling in steadily is worth more to somebody reading it
 * than the same page finishing four seconds sooner all at once - and because
 * whatever the reader taps has to get past this, and the fewer are in front of
 * it the sooner it does.
 *
 * A hosted service would take more without noticing. It is not given more
 * because it does not need it: what is in flight here is now a paragraph at a
 * time rather than a sentence, so a screenful is a handful of requests.
 */
private const val MAX_AT_ONCE = 2

/**
 * How often a translation still being written is worth putting on the page.
 *
 * Often enough that it reads as text arriving rather than as a page that jumps,
 * and rare enough that the book's translations are not filed again for every
 * token - see [TranslationSession.showAsItArrives].
 */
private val SHOW_ARRIVING_EVERY = 80.milliseconds

/**
 * Every passage translated while one book is open, and what a tap on each of
 * them now means.
 *
 * A tap is one gesture with three meanings, decided by what the passage is
 * already showing: an untouched sentence is translated, a translated one flips
 * back to the original, and one showing the original flips forward again. That
 * is the whole of "back and forth" - and holding it here rather than in the
 * screen is what makes it something that can be tested without composing
 * anything.
 *
 * Translations are kept for as long as the book is open, up to
 * [MAX_KEPT_PASSAGES] of them. A reader comparing two renderings of the same
 * line flips between them for free, and a chapter read twice is not paid for
 * twice - the part that survives closing the book is
 * [io.lukia.features.translate.infrastructure.CachingTranslator]'s doing rather
 * than this one's.
 */
class TranslationSession(
    private val translate: TranslatePassageUseCase,
    private val scope: CoroutineScope,
) {

    /**
     * What is on screen, and the order it was last touched in - which is what
     * decides who is forgotten first once [MAX_KEPT_PASSAGES] is reached.
     *
     * Only ever read and written from whatever thread the taps arrive on, which
     * is the one the reader's [scope] runs on. The flow beside it is what
     * everything else sees.
     */
    private val kept = LinkedHashMap<TextAnchor, PassageTranslation>()

    /** What keeps a whole page's worth of taps from arriving as a whole page of requests. */
    private val inFlight = Semaphore(MAX_AT_ONCE)

    /**
     * The requests still on their way, so that forgetting the book can stop
     * them.
     *
     * Every one of them ends by putting its answer into [kept], so without this
     * a translation asked for before a book was put away arrives into the book
     * that replaced it - anchored at a sentence that may no longer be there.
     *
     * A list of jobs rather than a scope of their own, because a scope of their
     * own is a job nobody ever completes, and anything waiting for the reader's
     * work to finish waits for that one forever. Held to the same rule as
     * [kept]: only ever touched from the thread the taps arrive on, which is
     * what [prune] is for - finished jobs are dropped as new ones are added
     * rather than as each of them ends, since the end of one happens wherever
     * it happened to end.
     */
    private val asking = mutableListOf<Job>()

    private val _passages = MutableStateFlow<Map<TextAnchor, PassageTranslation>>(emptyMap())
    val passages: StateFlow<Map<TextAnchor, PassageTranslation>> = _passages.asStateFlow()

    /**
     * [request] is built by the caller because only the reader knows which book
     * the sentence came out of - and is passed by name so that a tap which
     * merely flips an already-translated passage never builds one.
     */

    /**
     * Translates a passage nobody has asked about yet, and leaves every other
     * one exactly as it is.
     *
     * This is what "translate everything on screen" is made of, which is why it
     * is not [onPassageTapped]: a page holding a sentence the reader has flipped
     * back to the original must not have it flipped forward again, and one
     * holding a failure must not retry it silently forty times over.
     */
    fun onPassageRequested(anchor: TextAnchor, request: () -> TranslationRequest) {
        if (kept.containsKey(anchor)) return

        fetch(anchor, request(), waitsItsTurn = true)
    }

    fun onPassageTapped(anchor: TextAnchor, request: () -> TranslationRequest) {
        when (val current = kept[anchor]) {
            // Tapping again while it is still coming back is not a second
            // request. It is somebody wondering whether the first one landed.
            is PassageTranslation.Loading -> Unit

            is PassageTranslation.Ready -> flip(anchor)

            // A failure is not a state worth keeping. Tapping it is how the
            // reader retries, so it behaves exactly as the first tap did.
            null, is PassageTranslation.Failed -> fetch(anchor, request(), waitsItsTurn = false)
        }
    }

    /**
     * Puts one passage back to the book's own words, or forward to its
     * translation again.
     *
     * What a second gesture on an answered passage has always done, named so
     * that something other than a gesture can ask for it: the reader can now
     * hold a word to open what else it means, and the way back to the book's own
     * word went with it into that panel.
     *
     * Only a passage with an answer. One still arriving and one that failed have
     * nothing to flip between.
     */
    fun flip(anchor: TextAnchor) {
        val current = kept[anchor] as? PassageTranslation.Ready ?: return

        put(anchor, current.copy(showingOriginal = !current.showingOriginal))
    }

    /**
     * [waitsItsTurn] is false for the passage the reader pointed at.
     *
     * They are looking at it and it is one request, while the standing order
     * behind it may be a page of them. Measured on a local model, a tap made to
     * queue behind a page being translated took twenty seconds where the same
     * tap on its own took six - which is the reader's own gesture stuck in a
     * queue they did not ask for, and the thing [MAX_AT_ONCE] exists to bound
     * rather than to cause.
     */
    private fun fetch(anchor: TextAnchor, request: TranslationRequest, waitsItsTurn: Boolean) {
        put(anchor, PassageTranslation.Loading())

        prune()

        asking += scope.launch {
            // The translation arrives on whatever thread the connection is read
            // on, and [kept] belongs to this one. So the pieces are handed over
            // through a value that can be written from anywhere, and turned back
            // into state here - which is what keeps a streamed answer from
            // touching the map from under the taps.
            val arriving = MutableStateFlow<String?>(null)

            val showing = launch { arriving.filterNotNull().showAsItArrives(anchor) }

            val state = translating(waitsItsTurn) {
                translate(request) { arriving.value = it }
            }.fold(
                onSuccess = { PassageTranslation.Ready(it) },
                onFailure = { PassageTranslation.Failed(it.toAppError()) },
            )

            // Stopped before the finished translation is put, so a last piece
            // still in the flow cannot land on top of the whole answer.
            showing.cancelAndJoin()

            put(anchor, state)
        }
    }

    /**
     * Shows every translation at once, or puts every one of them back to the
     * book's own words.
     *
     * A real change to each passage rather than a filter laid over them, because
     * a filter would be a second opinion about what is on screen: a tap while it
     * was on would flip a sentence underneath it and the reader would not find
     * out until they turned the filter off. What this does instead is what the
     * reader would have done by tapping every marked passage in turn.
     *
     * Only the ones that have an answer. A passage still arriving is left to
     * arrive, and one that failed is left failed - neither has anything to show
     * or to hide.
     */
    fun showTranslations(showing: Boolean) {
        var changed = false

        kept.entries.forEach { entry ->
            val state = entry.value

            if (state is PassageTranslation.Ready && state.showingOriginal == showing) {
                entry.setValue(state.copy(showingOriginal = !showing))
                changed = true
            }
        }

        // Rewritten in place, so nothing has moved and the order these were last
        // touched in - which is what decides who is forgotten first - still holds.
        if (changed) _passages.value = kept.toMap()
    }

    /**
     * Forgets everything translated so far - for when the book underneath is
     * replaced, and the anchors that pointed into it no longer point anywhere.
     *
     * What is still on its way is stopped first. Emptying the map alone left
     * every request in flight to put its answer back into it a moment later, so
     * a reader who tapped three sentences and then re-opened the book the other
     * way found them arriving into the new one.
     */
    fun clear() {
        asking.forEach { it.cancel() }
        asking.clear()

        kept.clear()
        _passages.value = emptyMap()
    }

    /** Runs the translation, holding a place in the queue only if it has to. */
    private suspend fun <T> translating(waitsItsTurn: Boolean, translate: suspend () -> T): T =
        if (waitsItsTurn) inFlight.withPermit { translate() } else translate()

    /**
     * Puts a streamed answer on the page as it is written, but no more often
     * than [SHOW_ARRIVING_EVERY].
     *
     * Every piece used to be published, and each one costs far more than it
     * looks: [put] copies the whole map, and the screen above files every
     * passage in the book by where it belongs each time that map changes - see
     * `BookTranslations`. At a token apiece that is the book's translations
     * rebuilt tens of times a second, on the thread that draws, for the sake of
     * a word or two of text.
     *
     * On a clock rather than on a count of pieces, because how much text a
     * piece carries is the service's business rather than this one's. The first
     * goes up at once all the same, because it is the one that tells the reader
     * the tap landed. The last is not waited for: the finished answer is put
     * straight afterwards and says everything a final piece would have.
     */
    private suspend fun Flow<String>.showAsItArrives(anchor: TextAnchor) {
        var shown: TimeMark? = null

        collect { text ->
            val last = shown

            if (last != null && last.elapsedNow() < SHOW_ARRIVING_EVERY) return@collect

            shown = TimeSource.Monotonic.markNow()
            put(anchor, PassageTranslation.Loading(text))
        }
    }

    /** Drops the ones that have already finished, so the list is the ones in flight. */
    private fun prune() {
        asking.removeAll { it.isCompleted }
    }

    private fun put(anchor: TextAnchor, state: PassageTranslation) {
        // Removed before it is added so that touching a passage moves it to the
        // end, which is what makes forgetting least-recently-used rather than
        // oldest-first.
        kept.remove(anchor)
        kept[anchor] = state

        while (kept.size > MAX_KEPT_PASSAGES) kept.remove(kept.keys.first())

        _passages.value = kept.toMap()
    }
}
