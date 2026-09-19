package dev.priyanshu.arc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.priyanshu.arc.ui.create.CreateArcRoute
import dev.priyanshu.arc.ui.nav.AppRoute
import dev.priyanshu.arc.ui.theme.StreakSeasonTheme
import dev.priyanshu.arc.ui.today.TodayRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StreakSeasonTheme {
                var route by remember { mutableStateOf<AppRoute>(AppRoute.Today) }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when (route) {
                        is AppRoute.Today ->
                            TodayRoute(
                                onCreateArc = { route = AppRoute.CreateArc },
                                modifier = Modifier.padding(innerPadding),
                            )

                        is AppRoute.CreateArc ->
                            CreateArcRoute(
                                onCreated = { route = AppRoute.Today },
                                onCancel = { route = AppRoute.Today },
                                modifier = Modifier.padding(innerPadding),
                            )
                    }
                }
            }
        }
    }
}
