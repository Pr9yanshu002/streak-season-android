package dev.priyanshu.arc.di

import androidx.room.Room
import dev.priyanshu.arc.data.ArcRepository
import dev.priyanshu.arc.data.AppDatabase
import dev.priyanshu.arc.data.Clock
import dev.priyanshu.arc.data.DebugSeeder
import dev.priyanshu.arc.data.SystemClock
import dev.priyanshu.arc.ui.create.CreateArcViewModel
import dev.priyanshu.arc.ui.recap.RecapViewModel
import dev.priyanshu.arc.ui.today.TodayViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val databaseModule = module {
    single {
        Room.databaseBuilder(androidContext(), AppDatabase::class.java, AppDatabase.NAME).build()
    }
    single { get<AppDatabase>().arcDao() }
    single { get<AppDatabase>().commitmentDao() }
    single { get<AppDatabase>().dayDao() }
    single { get<AppDatabase>().entryDao() }
    single { get<AppDatabase>().milestoneDao() }
}

val clockModule = module {
    single<Clock> { SystemClock() }
}

val repositoryModule = module {
    single { ArcRepository(get(), get(), get(), get(), get(), get()) }
}

val debugModule = module {
    single { DebugSeeder(get(), get(), get()) }
}

val uiModule = module {
    viewModel { TodayViewModel(get(), get()) }
    // remixFromArcId (docs/tasks/12): null for a plain "start an arc", the
    // source arc's id when opened via Remix.
    viewModel { (remixFromArcId: String?) -> CreateArcViewModel(get(), get(), remixFromArcId) }
    viewModel { RecapViewModel(get()) }
}

val appModules = listOf(databaseModule, clockModule, repositoryModule, debugModule, uiModule)
