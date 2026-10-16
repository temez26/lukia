package io.lukia.features.sync

import io.lukia.core.domain.document.ServerBookDownloader
import io.lukia.core.domain.document.ServerBookUploader
import io.lukia.features.account.infrastructure.ServerHttp
import io.lukia.features.account.infrastructure.serverHttpClient
import io.lukia.features.sync.application.DownloadServerBookUseCase
import io.lukia.features.sync.application.ForgetServerUseCase
import io.lukia.features.sync.application.PullServerLibraryUseCase
import io.lukia.features.sync.application.SyncBookStateUseCase
import io.lukia.features.sync.application.SyncCoordinator
import io.lukia.features.sync.application.SyncRoundUseCase
import io.lukia.features.sync.application.SyncSessionsUseCase
import io.lukia.features.sync.application.UploadNewBooksUseCase
import io.lukia.features.sync.application.UploadServerBookUseCase
import io.lukia.features.sync.infrastructure.BookStateApi
import io.lukia.features.sync.infrastructure.BookStateStore
import io.lukia.features.sync.infrastructure.HistoryChoice
import io.lukia.features.sync.infrastructure.ServerEvents
import io.lukia.features.sync.infrastructure.ServerLibraryApi
import io.lukia.features.sync.infrastructure.SessionApi
import io.lukia.features.sync.infrastructure.SessionStore
import io.lukia.features.sync.infrastructure.SyncCursors
import io.lukia.features.sync.infrastructure.UploadChoice
import io.lukia.features.sync.presentation.HistoryViewModel
import io.lukia.features.sync.presentation.SyncStatusViewModel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.merge
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val syncModule = module {

    single { ServerLibraryApi(ServerHttp(serverHttpClient())) }

    single { SyncCursors(get()) }

    single { PullServerLibraryUseCase(get(), get(), get(), get(), get(), get()) }

    single<ServerBookDownloader> { DownloadServerBookUseCase(get(), get(), get(), get(), get()) }

    single<ServerBookUploader> {
        UploadServerBookUseCase(get(), get(), get(), get(), get(), get())
    }

    single { BookStateApi(ServerHttp(serverHttpClient())) }

    single { BookStateStore(get()) }

    single { SyncBookStateUseCase(get(), get(), get(), get(), get()) }

    single { ForgetServerUseCase(get(), get(), get()) }

    single { UploadChoice(get()) }

    single { UploadNewBooksUseCase(get(), get(), get(), get()) }

    single { SyncRoundUseCase(get(), get(), get(), get(), get(), get()) }

    single { SessionApi(ServerHttp(serverHttpClient())) }

    single { SessionStore(get()) }

    single { HistoryChoice(get()) }

    single { SyncSessionsUseCase(get(), get(), get(), get(), get()) }

    viewModel { HistoryViewModel(get(), get()) }

    viewModel { SyncStatusViewModel(get(), get(), get(), get(), get()) }

    single {
        SyncCoordinator(
            session = get(),
            round = get(),
            syncSessions = get(),
            forgetServer = get(),
            // Each store's count at launch is dropped: the round at launch or
            // sign-in covers what was already waiting.
            waiting = merge(get<BookStateStore>().waiting.drop(1), get<SyncSessionsUseCase>().waiting.drop(1)),
            events = ServerEvents(ServerHttp(serverHttpClient())),
        )
    }
}
