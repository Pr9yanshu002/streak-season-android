package dev.priyanshu.arc

import android.app.Application
import android.content.pm.ApplicationInfo
import dev.priyanshu.arc.data.DebugSeeder
import dev.priyanshu.arc.di.appModules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

class StreakSeasonApp : Application() {
    private val seeder: DebugSeeder by inject()
    private val seedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Guards against a second Application instance in the same process
        // (e.g. Robolectric creating one per test) trying to start Koin
        // twice, which throws. Real launches only ever hit this once.
        if (GlobalContext.getOrNull() == null) {
            startKoin {
                androidContext(this@StreakSeasonApp)
                modules(appModules)
            }
        }

        // Debug seed only, per docs/tasks/08-today-screen.md. Checked via
        // the manifest's debuggable flag rather than BuildConfig.DEBUG, so
        // build.gradle.kts doesn't need buildFeatures.buildConfig turned on.
        // seedIfNeeded() bails if any arc already exists (docs/tasks/11-arc-creation.md),
        // so this never clobbers an arc created through the UI.
        if (isDebuggable()) {
            seedScope.launch { seeder.seedIfNeeded() }
        }
    }

    private fun isDebuggable(): Boolean =
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
