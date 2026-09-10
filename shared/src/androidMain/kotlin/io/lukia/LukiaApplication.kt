package io.lukia

import android.app.Application
import android.content.ComponentCallbacks2
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import io.lukia.di.commonModule
import io.lukia.di.platformModule
import io.lukia.features.reader.presentation.rendering.FigureImageCache
import io.lukia.shared.presentation.components.cover.CoverArtCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class LukiaApplication : Application() {

    /**
     * For work that belongs to the process rather than to a screen or a
     * ViewModel, which so far is only letting go of memory on request below.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        // Reading the text off a PDF page needs the glyph tables that map a
        // font's own character codes to Unicode, and on Android those ship as
        // assets rather than on the classpath. Pointed at the app once here,
        // because the alternative is every book whose fonts need them coming
        // back as gibberish - or as nothing at all.
        PDFBoxResourceLoader.init(applicationContext)

        startKoin {
            androidContext(this@LukiaApplication)
            modules(
                commonModule,
                platformModule,
            )
        }
    }

    /**
     * Gives the decoded covers back when the system asks for memory.
     *
     * They are the largest thing the app holds that it can do without: the cache
     * is bounded at some thirty megabytes of bitmaps, and every one of them can
     * be read and decoded again from the book it came out of. Android's answer to
     * an app that keeps them anyway is to end the process, which costs the reader
     * a cold start and the place they were at - so a shelf that has to redraw its
     * covers is by far the cheaper of the two.
     *
     * The levels sort by number into two groups rather than one line, and this
     * takes the upper one: from [ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN] up
     * the app is off screen, where nothing is worth keeping covers for and
     * keeping them is what makes a backgrounded reader the first thing killed.
     * The smaller numbers below it arrive while the reader is still looking at
     * the shelf, and emptying it under them would blank every cover in front of
     * somebody to save memory the system has not yet said it needs - and those
     * levels are in any case no longer delivered from Android 15 on.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        if (level < ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) return

        // The plates of whatever book is open go the same way and for the same
        // reason: bounded, re-readable from the book, and worth far less than
        // the process staying alive.
        FigureImageCache.clear()

        scope.launch { CoverArtCache.clear() }
    }
}
