package dev.priyanshu.arc.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

// A by-eye check of the design system in Android Studio, without running the
// app. Every colour token with its name, and every text style with a sample.
// This file draws nothing the real screens use — it is only a reference card.

private data class Swatch(val name: String, val color: Color)

@Composable
private fun swatches(c: StreakColors): List<Swatch> = listOf(
    Swatch("frost", c.frost),
    Swatch("surface", c.surface),
    Swatch("sunken", c.sunken),
    Swatch("line", c.line),
    Swatch("line-strong", c.lineStrong),
    Swatch("ink", c.ink),
    Swatch("ink-muted", c.inkMuted),
    Swatch("evergreen", c.evergreen),
    Swatch("evergreen-soft", c.evergreenSoft),
    Swatch("on-evergreen", c.onEvergreen),
    Swatch("larch", c.larch),
    Swatch("larch-soft", c.larchSoft),
    Swatch("missed", c.missed),
    Swatch("rest", c.rest),
    Swatch("focus-ring", c.focusRing),
)

private data class Sample(val name: String, val sample: String, val style: TextStyle)

@Composable
private fun samples(): List<Sample> {
    val t = MaterialTheme.typography
    return listOf(
        Sample("ss-display · displayMedium", "Day 12", t.displayMedium),
        Sample("ss-title · headlineMedium", "Winter Arc", t.headlineMedium),
        Sample("ss-heading · titleLarge", "Your toughest week", t.titleLarge),
        Sample("ss-body · bodyLarge", "Three of four done. Reading is left.", t.bodyLarge),
        Sample("ss-label · labelLarge", "Required", t.labelLarge),
        Sample("ss-caption · bodySmall", "13–19 Jan · 3 of 6 days", t.bodySmall),
        Sample("ss-voice · StreakTheme.type.voice", "I want to finish this winter stronger than I started it.", StreakTheme.type.voice),
    )
}

@Composable
private fun DesignSystemSheet() {
    val colors = StreakTheme.colors
    Column(
        modifier = Modifier
            .background(colors.frost)
            .verticalScroll(rememberScrollState())
            .padding(Space.s6),
        verticalArrangement = Arrangement.spacedBy(Space.s6),
    ) {
        Text("Colour", style = MaterialTheme.typography.titleLarge, color = colors.ink)
        Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            swatches(colors).forEach { swatch ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.s4),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(swatch.color, RoundedCornerShape(6.dp))
                            .border(1.dp, colors.lineStrong, RoundedCornerShape(6.dp)),
                    )
                    Text(swatch.name, style = MaterialTheme.typography.bodyLarge, color = colors.ink)
                }
            }
        }

        Text("Type", style = MaterialTheme.typography.titleLarge, color = colors.ink)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Space.s4),
        ) {
            samples().forEach { s ->
                Column(verticalArrangement = Arrangement.spacedBy(Space.s1)) {
                    Text(s.name, style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
                    Text(s.sample, style = s.style, color = colors.ink)
                }
            }
        }
    }
}

@Preview(name = "Design system — light", showBackground = true)
@Composable
private fun DesignSystemLightPreview() {
    StreakTheme(darkTheme = false) { DesignSystemSheet() }
}

@Preview(name = "Design system — dark", showBackground = true)
@Composable
private fun DesignSystemDarkPreview() {
    StreakTheme(darkTheme = true) { DesignSystemSheet() }
}
