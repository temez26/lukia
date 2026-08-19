package io.lukia

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import io.lukia.di.commonModule
import io.lukia.di.platformModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class LukiaApplication : Application() {

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
}
