package dev.priyanshu.arc

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import dev.priyanshu.arc.data.ArcRepository
import dev.priyanshu.arc.ui.create.CreateArcRoute
import dev.priyanshu.arc.ui.debug.DebugMenuRoute
import dev.priyanshu.arc.ui.nav.AppRoute
import dev.priyanshu.arc.ui.recap.RecapRoute
import dev.priyanshu.arc.ui.theme.StreakSeasonTheme
import dev.priyanshu.arc.ui.today.TodayRoute
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val repository: ArcRepository by inject()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StreakSeasonTheme {
                var route by remember { mutableStateOf<AppRoute>(AppRoute.Today) }
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    floatingActionButton = {
                        // Same debuggable-flag check as DebugSeeder's
                        // auto-seed — never rendered in a release build.
                        if (isDebuggable() && route !is AppRoute.DebugMenu) {
                            FloatingActionButton(onClick = { route = AppRoute.DebugMenu }) {
                                Text("🐞")
                            }
                        }
                    },
                ) { innerPadding ->
                    when (val current = route) {
                        is AppRoute.Today ->
                            TodayRoute(
                                onCreateArc = { route = AppRoute.CreateArc() },
                                onRemix = { sourceArcId -> route = AppRoute.CreateArc(remixFromArcId = sourceArcId) },
                                onViewRecap = { sourceArcId -> route = AppRoute.Recap(sourceArcId) },
                                modifier = Modifier.padding(innerPadding),
                            )

                        is AppRoute.Recap ->
                            RecapRoute(
                                arcId = current.arcId,
                                // Both actions go through the create flow; neither
                                // touches the ended arc's status (docs/tasks/15).
                                onRunAgain = { route = AppRoute.CreateArc(remixFromArcId = current.arcId) },
                                onStartNew = { route = AppRoute.CreateArc() },
                                onBack = { route = AppRoute.Today },
                                modifier = Modifier.padding(innerPadding),
                            )

                        is AppRoute.CreateArc ->
                            CreateArcRoute(
                                remixFromArcId = current.remixFromArcId,
                                onCreated = { route = AppRoute.Today },
                                onCancel = { route = AppRoute.Today },
                                modifier = Modifier.padding(innerPadding),
                            )

                        is AppRoute.DebugMenu ->
                            DebugMenuRoute(
                                onSeeded = { route = AppRoute.Today },
                                onCancel = { route = AppRoute.Today },
                                modifier = Modifier.padding(innerPadding),
                            )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            repository.getActiveArcId()?.let { repository.reconcile(it) }
        }
    }

    private fun isDebuggable(): Boolean =
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
