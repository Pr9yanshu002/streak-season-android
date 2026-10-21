package dev.priyanshu.arc.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import dev.priyanshu.arc.ui.theme.Space
import dev.priyanshu.arc.ui.theme.StreakTheme

// Commitment rows for the Today screen (docs/tasks/18, design 01). All
// stateless: the caller passes the values Resolution already produced and gets
// plain callbacks back. None of them decide anything about the day — "done"
// is whatever the caller says it is.

// The commitment name: ss-body at weight 600, as every card in the designs uses.
@Composable
private fun CommitmentName(
    name: String,
    isBonus: Boolean,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = color,
        )
        if (isBonus) BonusChip()
    }
}

/**
 * Binary commitment (design 01, Gym): a round tick button, the name, and a
 * status line. Done → evergreen-soft card, filled tick, "Done". Not done →
 * plain card with an empty outlined circle to tap.
 */
@Composable
fun BinaryCommitmentCard(
    name: String,
    isDone: Boolean,
    isBonus: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    StreakCard(
        modifier = modifier.fillMaxWidth(),
        color = if (isDone) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = if (isDone) null else BorderStroke(1.dp, StreakTheme.colors.line),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s4),
        ) {
            // Checkbox semantics come from Surface(checked = …): TalkBack
            // reads the name plus "checked"/"not checked".
            Surface(
                checked = isDone,
                onCheckedChange = onToggle,
                shape = CircleShape,
                color = if (isDone) MaterialTheme.colorScheme.primary else Color.Transparent,
                border = if (isDone) null else BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .size(TouchTarget)
                    .semantics { contentDescription = name },
            ) {
                if (isDone) {
                    Box(contentAlignment = Alignment.Center) {
                        StrokeIcon(StrokeGlyph.TICK, MaterialTheme.colorScheme.onPrimary, size = 20.dp, strokeWidth = 2.5f)
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                CommitmentName(name, isBonus)
                Text(
                    text = if (isDone) "Done" else "Not done yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Counter or duration commitment (design 01, Water and Reading): name,
 * "X of Y unit", − and + buttons, and a progress bar toward the target.
 *
 * [isDone] switches to the design-02 look: evergreen-soft card, a tick disc
 * in front, the caption in evergreen ending "· done", no progress bar.
 * [captionSuffix] appends " · <text>" to the caption (design 02's bonus row
 * uses "doesn't affect your day"). [step] is what one tap adds or removes —
 * the data model has no per-commitment step, so callers pass 1.
 */
@Composable
fun CounterCommitmentCard(
    name: String,
    current: Double,
    target: Double?,
    unit: String,
    isBonus: Boolean,
    isDone: Boolean,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    step: Double = 1.0,
    captionSuffix: String? = null,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val evergreen = MaterialTheme.colorScheme.primary
    val unitText = if (unit.isBlank()) "" else " $unit"
    val caption = buildAnnotatedString {
        if (isDone) {
            append(formatAmount(current))
        } else {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = ink)) { append(formatAmount(current)) }
        }
        if (target != null) append(" of ${formatAmount(target)}")
        append(unitText)
        if (isDone) append(" · done")
        if (captionSuffix != null) append(" · $captionSuffix")
    }

    StreakCard(
        modifier = modifier.fillMaxWidth(),
        color = if (isDone) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = if (isDone) null else BorderStroke(1.dp, StreakTheme.colors.line),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s2 + Space.s1),
        ) {
            if (isDone) {
                Box(
                    modifier = Modifier.size(TouchTarget).background(evergreen, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    StrokeIcon(StrokeGlyph.TICK, MaterialTheme.colorScheme.onPrimary, size = 20.dp, strokeWidth = 2.5f)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                CommitmentName(name, isBonus)
                Text(caption, style = MaterialTheme.typography.bodySmall, color = if (isDone) evergreen else muted)
            }
            val stepText = formatAmount(step) + unitText
            RoundIconButton(
                glyph = StrokeGlyph.MINUS,
                filled = false,
                contentDescription = "Remove $stepText from $name",
                onClick = onDecrement,
            )
            RoundIconButton(
                glyph = StrokeGlyph.PLUS,
                filled = true,
                contentDescription = "Add $stepText to $name",
                onClick = onIncrement,
            )
        }
        if (!isDone && target != null && target > 0.0) {
            LinearProgressIndicator(
                progress = { (current / target).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier
                    .padding(top = Space.s2 + Space.s1)
                    .fillMaxWidth()
                    .height(6.dp),
                color = evergreen,
                trackColor = StreakTheme.colors.sunken,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}

/**
 * Number ("log") commitment (design 01, Weight): name, a number field, the
 * unit, and a helper line.
 *
 * Keeps task 10's save behaviour: the typed text lives only in this field
 * until the user taps done or focus leaves — never one write per keystroke.
 * A blank field clears the entry ([onClear]); text that isn't a number snaps
 * back to the stored [value].
 */
@Composable
fun NumberCommitmentCard(
    name: String,
    value: Double?,
    unit: String?,
    isBonus: Boolean,
    onCommit: (Double) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf(value?.let(::formatInputAmount).orEmpty()) }
    var isFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // Follows the stored value when it changes for another reason (an undo
    // elsewhere) — but never mid-typing, or the user's input would be lost.
    LaunchedEffect(value) {
        if (!isFocused) text = value?.let(::formatInputAmount).orEmpty()
    }

    fun commit() {
        val trimmed = text.trim()
        when {
            trimmed.isEmpty() -> onClear()
            else -> trimmed.toDoubleOrNull()?.let(onCommit) ?: run {
                text = value?.let(::formatInputAmount).orEmpty()
            }
        }
    }

    StreakCard(modifier = modifier.fillMaxWidth()) {
        CommitmentName(name, isBonus)
        Row(
            modifier = Modifier.padding(top = Space.s2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s2),
        ) {
            val outline = MaterialTheme.colorScheme.outline
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    commit()
                    focusManager.clearFocus()
                }),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .semantics { contentDescription = if (unit.isNullOrBlank()) name else "$name, $unit" }
                    .onFocusChanged { focusState ->
                        val wasFocused = isFocused
                        isFocused = focusState.isFocused
                        if (wasFocused && !isFocused) commit()
                    },
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .background(StreakTheme.colors.sunken, MaterialTheme.shapes.small)
                            .border(1.5.dp, outline, MaterialTheme.shapes.small)
                            .padding(horizontal = Space.s4),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (text.isEmpty()) {
                            Text(
                                "Today's reading",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            if (!unit.isNullOrBlank()) {
                Text(unit, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            "Saved when you tap done or move away.",
            modifier = Modifier.padding(top = Space.s2),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A commitment that isn't scheduled today (design 01, Long walk): a dashed
 * outline, a small sunken dot, the name muted, and "Not today", followed by
 * " · <schedule>" when the caller has schedule text to show. Not interactive.
 */
@Composable
fun UnscheduledCommitmentRow(
    name: String,
    scheduleText: String?,
    modifier: Modifier = Modifier,
) {
    val line = StreakTheme.colors.line
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                // Compose has no dashed BorderStroke, so the outline is drawn
                // here: 1 dp, 16 dp corners to match shapes.medium.
                val stroke = 1.dp.toPx()
                val corner = 16.dp.toPx()
                drawRoundRect(
                    color = line,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(corner, corner),
                    style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                )
            }
            .padding(horizontal = Space.s4, vertical = Space.s2 + Space.s1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s4),
    ) {
        Box(modifier = Modifier.size(TouchTarget), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(22.dp).background(StreakTheme.colors.sunken, CircleShape))
        }
        Column(modifier = Modifier.weight(1f)) {
            CommitmentName(name, isBonus = false, color = muted)
            Text(
                text = if (scheduleText.isNullOrBlank()) "Not today" else "Not today · $scheduleText",
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun BinaryCardPreview() {
    PreviewFrame {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s2 + Space.s1)) {
            BinaryCommitmentCard(name = "Gym", isDone = true, isBonus = false, onToggle = {})
            BinaryCommitmentCard(name = "Stretch", isDone = false, isBonus = true, onToggle = {})
        }
    }
}

@PreviewLightDark
@Composable
private fun CounterCardPreview() {
    PreviewFrame {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s2 + Space.s1)) {
            CounterCommitmentCard(
                name = "Water", current = 2100.0, target = 3000.0, unit = "ml",
                isBonus = false, isDone = false, onDecrement = {}, onIncrement = {},
            )
            CounterCommitmentCard(
                name = "Reading", current = 10.0, target = 30.0, unit = "min",
                isBonus = true, isDone = false, onDecrement = {}, onIncrement = {},
            )
            CounterCommitmentCard(
                name = "Water", current = 3000.0, target = 3000.0, unit = "ml",
                isBonus = false, isDone = true, onDecrement = {}, onIncrement = {},
            )
            CounterCommitmentCard(
                name = "Reading", current = 20.0, target = 30.0, unit = "min",
                isBonus = true, isDone = false, onDecrement = {}, onIncrement = {},
                captionSuffix = "doesn't affect your day",
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun NumberCardPreview() {
    PreviewFrame {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s2 + Space.s1)) {
            NumberCommitmentCard(name = "Weight", value = null, unit = "kg", isBonus = true, onCommit = {}, onClear = {})
            NumberCommitmentCard(name = "Weight", value = 72.5, unit = "kg", isBonus = true, onCommit = {}, onClear = {})
        }
    }
}

@PreviewLightDark
@Composable
private fun UnscheduledRowPreview() {
    PreviewFrame {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s2 + Space.s1)) {
            UnscheduledCommitmentRow(name = "Long walk", scheduleText = "Saturdays and Sundays")
            UnscheduledCommitmentRow(name = "Long walk", scheduleText = null)
        }
    }
}
