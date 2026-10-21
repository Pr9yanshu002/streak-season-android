package dev.priyanshu.arc.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.priyanshu.arc.ui.theme.Space
import dev.priyanshu.arc.ui.theme.StreakTheme
import java.text.NumberFormat

// Small building blocks shared by the Today components (docs/tasks/18). Not
// public API: each is internal to ui/components.

// Every round button in the designs is 44 dp — the minimum touch target.
internal val TouchTarget = 44.dp

// The design's three icons (tick, plus, minus) are single stroked paths on a
// 24-unit grid. Drawn here rather than pulling in an icon library — the
// constraints say no new dependencies.
internal enum class StrokeGlyph { TICK, PLUS, MINUS }

@Composable
internal fun StrokeIcon(glyph: StrokeGlyph, color: Color, size: Dp, strokeWidth: Float) {
    Canvas(modifier = Modifier.size(size)) {
        // One design unit in pixels: the SVGs are drawn on a 24 x 24 grid.
        val u = this.size.width / 24f
        val path = Path().apply {
            when (glyph) {
                // M5 12.5 l4.5 4.5 L19 7.5
                StrokeGlyph.TICK -> {
                    moveTo(5f * u, 12.5f * u)
                    lineTo(9.5f * u, 17f * u)
                    lineTo(19f * u, 7.5f * u)
                }
                // M6 12 h12, plus M12 6 v12 for the plus sign
                StrokeGlyph.PLUS, StrokeGlyph.MINUS -> {
                    moveTo(6f * u, 12f * u)
                    lineTo(18f * u, 12f * u)
                    if (glyph == StrokeGlyph.PLUS) {
                        moveTo(12f * u, 6f * u)
                        lineTo(12f * u, 18f * u)
                    }
                }
            }
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth * u, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

// The 44 dp round button used by the steppers: filled evergreen for "add",
// outlined for "remove" (designs 01–03).
@Composable
internal fun RoundIconButton(
    glyph: StrokeGlyph,
    filled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (filled) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        border = if (filled) null else BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .size(TouchTarget)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Box(contentAlignment = Alignment.Center) {
            StrokeIcon(
                glyph = glyph,
                color = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                size = 18.dp,
                strokeWidth = 2.2f,
            )
        }
    }
}

// The card shell every commitment row and the rest card share: 16 dp corners
// (shapes.medium), surface fill, 1 dp hairline.
@Composable
internal fun StreakCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    border: BorderStroke? = BorderStroke(1.dp, StreakTheme.colors.line),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = color,
        border = border,
    ) {
        Column(modifier = Modifier.padding(Space.s4), content = content)
    }
}

// "2,100", "72.5" — grouped, at most two decimals, no trailing ".0". Display
// only; never parsed back.
fun formatAmount(value: Double): String =
    NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }.format(value)

// What a number field shows for a stored value: "72.5", "70" — no grouping, so
// it parses straight back with toDoubleOrNull().
fun formatInputAmount(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

// Wraps a preview in the theme on the page background, so light and dark
// previews show the real ground colour instead of the preview's default white.
@Composable
internal fun PreviewFrame(content: @Composable ColumnScope.() -> Unit) {
    StreakTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(Space.s6), content = content)
        }
    }
}
