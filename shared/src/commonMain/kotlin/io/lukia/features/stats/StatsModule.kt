package io.lukia.features.stats

import io.lukia.features.stats.application.GetReadingHabitsUseCase
import io.lukia.features.stats.application.GetReadingStatsUseCase
import io.lukia.features.stats.presentation.StatsViewModel
import io.lukia.features.stats.presentation.WeekGlanceViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val statsModule = module {

    single {
        GetReadingStatsUseCase(get(), get(), get())
    }

    single {
        GetReadingHabitsUseCase(get())
    }

    viewModel {
        StatsViewModel(get())
    }

    viewModel {
        WeekGlanceViewModel(get())
    }
}
