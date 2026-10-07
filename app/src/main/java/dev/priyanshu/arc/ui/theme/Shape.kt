package dev.priyanshu.arc.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// radius-sm (6) and radius-md (16) from tokens.json. radius-full (999 ≈
// CircleShape) is not a Material Shapes slot — use CircleShape directly for
// pills, day discs and stepper buttons.
val StreakShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(16.dp),
)

// Spacing scale from tokens.json. A plain object, not a CompositionLocal:
// spacing never changes with the theme.
object Space {
    val s1 = 4.dp
    val s2 = 8.dp
    val s4 = 16.dp
    val s6 = 24.dp
    val s10 = 40.dp
}
