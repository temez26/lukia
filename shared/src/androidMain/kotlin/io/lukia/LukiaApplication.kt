package io.lukia

import android.app.Application
import io.lukia.di.commonModule
import io.lukia.di.platformModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class LukiaApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@LukiaApplication)
            modules(
                commonModule,
                platformModule
            )
        }
    }
}