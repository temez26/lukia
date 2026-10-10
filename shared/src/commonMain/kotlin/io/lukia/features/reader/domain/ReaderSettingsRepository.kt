package io.lukia.features.reader.domain

import kotlinx.coroutines.flow.StateFlow

/**
 * How the reader is set up, live and kept between launches.
 *
 * One for the process, so a change made on one screen is what every other
 * screen reads on its very next look.
 */
interface ReaderSettingsRepository {

    val settings: StateFlow<ReaderSettings>

    fun update(settings: ReaderSettings)
}
