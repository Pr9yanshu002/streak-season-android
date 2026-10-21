package dev.priyanshu.arc.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import dev.priyanshu.arc.domain.model.DayState
import dev.priyanshu.arc.ui.theme.Space
import dev.priyanshu.arc.ui.theme.StreakTheme

/**
 * The task-17 rest-day row, both forms, restyled (docs/tasks/18):
 *
 * - [resting] = false (design 01, bottom): "Need a rest day?", "N left this
 *   week", a "Rest today" pill button, and the helper line.
 * - [resting] = true (design 03, top): a card outlined in the rest colour,
 *   "Today is a rest day", the reassurance, and "Not resting after all".
 *
 * Whether to show it at all, and which form, is the caller's decision
 * (TodayUiState.Loaded.restDayRow). Copy is exactly task 17's.
 */
@Composable
fun RestDayCard(
    resting: Boolean,
    tokensLeft: Int,
    onSetRestDay: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (resting) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.5.dp, StreakTheme.colors.rest),
        ) {
            Column(
                modifier = Modifier.padding(Space.s4 + Space.s1),
                verticalArrangement = Arrangement.spacedBy(Space.s4),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.s4),
                ) {
                    DayDisc(state = DayState.REST, isToday = false, size = 48.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Today is a rest day", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Your rest day for this week",
                            style = MaterialTheme.typography.labelLarge,
                            color = StreakTheme.colors.rest,
                        )
                    }
                }
                Text(
                    "Your streak carries on. If you end up finishing everything anyway, " +
                        "the rest day isn't used and you keep it.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PillButton(text = "Not resting after all", onClick = { onSetRestDay(false) })
            }
        }
    } else {
        StreakCard(modifier = modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s2 + Space.s1),
            ) {
                DayDisc(state = DayState.REST, isToday = false)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Need a rest day?",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    Text(
                        "$tokensLeft left this week",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PillButton(text = "Rest today", onClick = { onSetRestDay(true) })
            }
            Text(
                "It's only used if today ends unfinished. Finish everything anyway and you keep it.",
                modifier = Modifier.padding(top = Space.s2 + Space.s1),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// The design's outlined pill: 44 dp tall, line-strong edge, ss-label text.
@Composable
private fun PillButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = CircleShape,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = PaddingValues(horizontal = Space.s4),
        modifier = Modifier.heightIn(min = TouchTarget),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@PreviewLightDark
@Composable
private fun RestDayOfferPreview() {
    PreviewFrame {
        RestDayCard(resting = false, tokensLeft = 1, onSetRestDay = {})
    }
}

@PreviewLightDark
@Composable
private fun RestDayRestingPreview() {
    PreviewFrame {
        RestDayCard(resting = true, tokensLeft = 0, onSetRestDay = {})
    }
}
