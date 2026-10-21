package dev.priyanshu.arc.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.priyanshu.arc.domain.model.DayState
import dev.priyanshu.arc.ui.theme.Space
import dev.priyanshu.arc.ui.theme.StreakTheme
import java.time.LocalDate
import java.util.Locale
import java.time.format.TextStyle as DateTextStyle

/**
 * One day as a disc (docs/design/today, `disc()`). The shape carries the
 * meaning, so it still reads in greyscale or for someone who can't tell the
 * colours apart:
 *
 * - complete: filled evergreen
 * - partial: bottom half larch, top half larch-soft
 * - missed: hollow ring
 * - rest: small dot in the centre of a sunken disc
 * - future / pending: plain sunken disc
 * - out of range: nothing (an empty slot the same size)
 *
 * [isToday] adds the larch ring on top of whichever of those applies.
 * Stateless: the caller passes the engine's DayState as is.
 */
@Composable
fun DayDisc(
    state: DayState,
    isToday: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
) {
    if (state == DayState.OUT_OF_RANGE) {
        Spacer(modifier.size(size))
        return
    }
    val colors = StreakTheme.colors
    val evergreen = MaterialTheme.colorScheme.primary
    val larch = MaterialTheme.colorScheme.tertiary

    Canvas(modifier = modifier.size(size)) {
        val radius = this.size.minDimension / 2f
        when (state) {
            DayState.COMPLETE -> drawCircle(color = evergreen, radius = radius)
            DayState.PARTIAL -> {
                drawCircle(color = colors.larchSoft, radius = radius)
                // Angles run clockwise from 3 o'clock, so 0..180 is the bottom half.
                drawArc(color = larch, startAngle = 0f, sweepAngle = 180f, useCenter = true)
            }
            DayState.MISSED -> {
                val ring = 1.5.dp.toPx()
                drawCircle(color = colors.missed, radius = radius - ring / 2f, style = Stroke(width = ring))
            }
            DayState.REST -> {
                drawCircle(color = colors.sunken, radius = radius)
                drawCircle(color = colors.rest, radius = radius * 0.42f)
            }
            DayState.FUTURE, DayState.PENDING -> drawCircle(color = colors.sunken, radius = radius)
            DayState.OUT_OF_RANGE -> Unit // returned early above
        }
        if (isToday) {
            val ring = 2.5.dp.toPx()
            drawCircle(color = larch, radius = radius - ring / 2f, style = Stroke(width = ring))
        }
    }
}

// One slot in the week strip. state is the engine's DayState for that date,
// OUT_OF_RANGE for a date outside the arc (drawn as an empty slot).
data class WeekStripDay(
    val date: LocalDate,
    val state: DayState,
    val isToday: Boolean,
)

/**
 * Monday–Sunday row of [DayDisc]s with day letters (design 01). Today's letter
 * is bold larch. Each column is one TalkBack item, read as e.g.
 * "Monday, complete" — the disc and letter are not read separately.
 */
@Composable
fun WeekStrip(days: List<WeekStripDay>, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, StreakTheme.colors.line),
    ) {
        Row(modifier = Modifier.padding(vertical = Space.s4, horizontal = Space.s2)) {
            days.forEach { day ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clearAndSetSemantics { contentDescription = dayDiscDescription(day) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Space.s2),
                ) {
                    DayDisc(state = day.state, isToday = day.isToday)
                    Text(
                        text = day.date.dayOfWeek.getDisplayName(DateTextStyle.NARROW, Locale.getDefault()),
                        style = if (day.isToday) {
                            MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                        } else {
                            MaterialTheme.typography.bodySmall
                        },
                        color = if (day.isToday) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

// "Monday, complete" / "Wednesday, today, in progress". Pure, so it's unit
// tested without Compose.
fun dayDiscDescription(day: WeekStripDay): String {
    val dayName = day.date.dayOfWeek.getDisplayName(DateTextStyle.FULL, Locale.getDefault())
    val stateText = when (day.state) {
        DayState.COMPLETE -> "complete"
        DayState.PARTIAL -> "partial"
        DayState.MISSED -> "missed"
        DayState.REST -> "rest day"
        DayState.PENDING -> "in progress"
        DayState.FUTURE -> "still to come"
        DayState.OUT_OF_RANGE -> "not part of this arc"
    }
    return if (day.isToday) "$dayName, today, $stateText" else "$dayName, $stateText"
}

@PreviewLightDark
@Composable
private fun DayDiscPreview() {
    PreviewFrame {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            DayDisc(DayState.COMPLETE, isToday = false)
            DayDisc(DayState.PARTIAL, isToday = false)
            DayDisc(DayState.MISSED, isToday = false)
            DayDisc(DayState.REST, isToday = false)
            DayDisc(DayState.FUTURE, isToday = false)
            DayDisc(DayState.PENDING, isToday = true)
            DayDisc(DayState.REST, isToday = true)
            DayDisc(DayState.COMPLETE, isToday = true)
        }
    }
}

// Design 01's week: Mon, Tue complete, Wed today in progress, Thu–Sun to come.
@PreviewLightDark
@Composable
private fun WeekStripPreview() {
    val monday = LocalDate.of(2026, 1, 19)
    val states = listOf(
        DayState.COMPLETE, DayState.COMPLETE, DayState.PENDING,
        DayState.FUTURE, DayState.FUTURE, DayState.FUTURE, DayState.FUTURE,
    )
    PreviewFrame {
        WeekStrip(
            days = states.mapIndexed { i, state ->
                WeekStripDay(date = monday.plusDays(i.toLong()), state = state, isToday = i == 2)
            },
        )
    }
}

// Every shape in one week, with the arc starting mid-week so the first slots
// are empty.
@PreviewLightDark
@Composable
private fun WeekStripMixedPreview() {
    val monday = LocalDate.of(2026, 1, 19)
    val states = listOf(
        DayState.OUT_OF_RANGE, DayState.COMPLETE, DayState.PARTIAL,
        DayState.MISSED, DayState.REST, DayState.PENDING, DayState.FUTURE,
    )
    PreviewFrame {
        WeekStrip(
            days = states.mapIndexed { i, state ->
                WeekStripDay(date = monday.plusDays(i.toLong()), state = state, isToday = i == 5)
            },
        )
    }
}
