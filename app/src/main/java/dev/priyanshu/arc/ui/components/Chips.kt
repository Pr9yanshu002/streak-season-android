package dev.priyanshu.arc.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import dev.priyanshu.arc.ui.theme.Space
import dev.priyanshu.arc.ui.theme.StreakTheme

/**
 * "N-day streak" (design 01 header). Evergreen on evergreen-soft. The caller
 * decides whether to show it at all (task 18 hides it at 0); this only draws.
 */
@Composable
fun StreakPill(days: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Text(
            text = "$days-day streak",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = Space.s2 + Space.s1, vertical = Space.s1),
        )
    }
}

/**
 * "Bonus" — marks a commitment that isn't required (is_required = 0). Larch on
 * larch-soft (design 01, Reading and Weight).
 */
@Composable
fun BonusChip(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = StreakTheme.colors.larchSoft,
        contentColor = MaterialTheme.colorScheme.tertiary,
    ) {
        Text(
            text = "Bonus",
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = MaterialTheme.typography.labelLarge.letterSpacing,
            ),
            modifier = Modifier.padding(horizontal = Space.s2, vertical = 2.dp),
        )
    }
}

@PreviewLightDark
@Composable
private fun ChipsPreview() {
    PreviewFrame {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            StreakPill(days = 7)
            BonusChip()
        }
    }
}
