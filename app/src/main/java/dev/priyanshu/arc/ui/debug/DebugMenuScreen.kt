package dev.priyanshu.arc.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.priyanshu.arc.data.DebugScenario
import dev.priyanshu.arc.data.DebugSeeder
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

// Debug-only (MainActivity only ever reaches this route when the app is
// debuggable). One button per flow worth testing on-device: picking one
// wipes every table and reseeds exactly that scenario, then hands control
// back so the caller can navigate to Today and see it.
@Composable
fun DebugMenuRoute(
    onSeeded: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    seeder: DebugSeeder = koinInject(),
) {
    val scope = rememberCoroutineScope()
    var isSeeding by remember { mutableStateOf(false) }

    DebugMenuScreen(
        isSeeding = isSeeding,
        onPick = { scenario ->
            isSeeding = true
            scope.launch {
                seeder.seed(scenario)
                isSeeding = false
                onSeeded()
            }
        },
        onCancel = onCancel,
        modifier = modifier,
    )
}

@Composable
private fun DebugMenuScreen(
    isSeeding: Boolean,
    onPick: (DebugScenario) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Debug: jump to a scenario", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Wipes the local database and reseeds exactly one scenario.",
            style = MaterialTheme.typography.bodyMedium,
        )
        DebugScenario.entries.forEach { scenario ->
            Button(
                onClick = { onPick(scenario) },
                enabled = !isSeeding,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(scenario.label())
            }
        }
        TextButton(onClick = onCancel, enabled = !isSeeding) { Text("Cancel") }
    }
}

private fun DebugScenario.label(): String = when (this) {
    DebugScenario.EMPTY -> "Empty state"
    DebugScenario.NOT_STARTED -> "Not started yet"
    DebugScenario.ACTIVE_MID_ARC -> "Active, mid-arc"
    DebugScenario.MILESTONE_PENDING -> "Milestone pending"
    DebugScenario.ENDED_COMPLETED -> "Ended — completed"
    DebugScenario.ENDED_FAILED -> "Ended — failed"
    DebugScenario.ENDED_RECAP -> "Ended — recap showcase"
}
