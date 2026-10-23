package dev.priyanshu.arc.ui.create

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.priyanshu.arc.ui.components.LargeButton
import dev.priyanshu.arc.ui.components.PreviewFrame
import dev.priyanshu.arc.ui.components.TouchTarget
import dev.priyanshu.arc.ui.theme.Space
import dev.priyanshu.arc.ui.theme.StreakTheme
@Composable
fun StepHeader(
    currentStep: Int,
    totalSteps: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
){
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Space.s6, end = Space.s2, top = Space.s4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ){
        Row(
            modifier = Modifier
                .weight(1f)
                // TalkBack reads one sentence instead of four silent bars.
                .clearAndSetSemantics { contentDescription = "Step $currentStep of $totalSteps" },
            horizontalArrangement = Arrangement.spacedBy(Space.s1),
        ){
            repeat(totalSteps) {
                index -> Box(
                    Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(if (index < currentStep) StreakTheme.colors.ink else StreakTheme.colors.line),
                )
            }
        }
        CloseButton(onClick = onClose)
    }
}

@Composable
private fun CloseButton(
    onClick: () -> Unit,
){
    val ink = StreakTheme.colors.ink
    Box(
        modifier = Modifier
            .size(TouchTarget)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Close" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(14.dp)) {
            val stroke = 2.dp.toPx()
            drawLine(ink, Offset.Zero, Offset(size.width, size.height), stroke, StrokeCap.Round)
            drawLine(ink, Offset(size.width, 0f), Offset(0f, size.height), stroke, StrokeCap.Round)
        }
    }
}

// "Step 2 of 4", the step's title, and an optional line under it.
@Composable
fun StepIntro(
    eyebrow: String?,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        eyebrow?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = StreakTheme.colors.inkMuted) }
        Text(title, style = MaterialTheme.typography.headlineMedium)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = StreakTheme.colors.inkMuted) }
    }
}

@Composable
fun StepBottomBar(
    primaryText: String,
    onPrimary: () -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
){
    Column(
        modifier.fillMaxWidth()
    ){
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = Space.s6, end = Space.s6, top = Space.s4, bottom = Space.s6),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (onBack!=null){
                LargeButton(text = "Back", onClick = onBack, filled = false, modifier = Modifier.width(112.dp),)
            }
            LargeButton(
                text = primaryText,
                onClick = onPrimary,
                filled = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}


@Preview(name = "Header and intro · light")
@Preview(name = "Header and intro · dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StepHeaderPreview() {
    PreviewFrame {
        StepHeader(currentStep = 2, totalSteps = 4, onClose = {})
        StepIntro(
            eyebrow = "Step 2 of 4",
            title = "What will you do?",
            subtitle = "Required ones decide whether a day counts. Bonus ones are tracked, but never break a day.",
            modifier = Modifier.padding(horizontal = Space.s6),
        )
    }
}
