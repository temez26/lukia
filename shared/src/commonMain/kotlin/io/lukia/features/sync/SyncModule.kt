package io.lukia.features.sync

import io.lukia.core.domain.document.ServerBookDownloader
import io.lukia.features.account.infrastructure.ServerHttp
import io.lukia.features.account.infrastructure.serverHttpClient
import io.lukia.features.sync.application.DownloadServerBookUseCase
import io.lukia.features.sync.application.PullServerLibraryUseCase
import io.lukia.features.sync.application.SyncCoordinator
import io.lukia.features.sync.infrastructure.ServerLibraryApi
import io.lukia.features.sync.infrastructure.SyncCursors
import org.koin.dsl.module

val syncModule = module {

    single { ServerLibraryApi(ServerHttp(serverHttpClient())) }

    single { SyncCursors(get()) }

    single { PullServerLibraryUseCase(get(), get(), get(), get(), get(), get()) }

    single<ServerBookDownloader> { DownloadServerBookUseCase(get(), get(), get(), get(), get()) }

    single { SyncCoordinator(get(), get(), get(), get(), get(), get()) }
}
