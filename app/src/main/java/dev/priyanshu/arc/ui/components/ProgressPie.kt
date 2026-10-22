package dev.priyanshu.arc.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.priyanshu.arc.ui.theme.Space
import dev.priyanshu.arc.ui.theme.StreakTheme

/**
 * A pie filled clockwise from 12 o'clock to [fraction] (0..1). Two looks from
 * the designs:
 *
 * - [track] given (designs 04/05, milestones): a solid [track] disc with the
 *   [color] wedge on top — larch on larch-soft.
 * - [track] null (design 07, the no-arc row): an outline ring in [color] with
 *   the wedge inside it — evergreen, and a full disc at 1.
 *
 * Decorative: it carries no content description. The text beside it says the
 * same thing.
 */
@Composable
fun ProgressPie(
    fraction: Float,
    color: Color,
    track: Color?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val radius = this.size.minDimension / 2f
        if (track != null) {
            drawCircle(color = track, radius = radius)
        } else {
            // The design's ring: 2 units wide on a 36-unit icon.
            val ring = this.size.minDimension * 2f / 36f
            drawCircle(color = color, radius = radius - ring / 2f, style = Stroke(width = ring))
        }
        val sweep = 360f * fraction.coerceIn(0f, 1f)
        if (sweep > 0f) {
            drawArc(color = color, startAngle = -90f, sweepAngle = sweep, useCenter = true)
        }
    }
}

@PreviewLightDark
@Composable
private fun ProgressPiePreview() {
    PreviewFrame {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s2 + Space.s1)) {
            listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach {
                ProgressPie(fraction = it, color = MaterialTheme.colorScheme.primary, track = null, size = 36.dp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s2 + Space.s1)) {
            listOf(0.25f, 0.5f, 0.75f, 1f).forEach {
                ProgressPie(
                    fraction = it,
                    color = MaterialTheme.colorScheme.tertiary,
                    track = StreakTheme.colors.larchSoft,
                    size = 32.dp,
                )
            }
        }
    }
}
