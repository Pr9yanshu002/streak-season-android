package dev.priyanshu.arc.ui.create

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import dev.priyanshu.arc.R
import dev.priyanshu.arc.ui.components.LargeButton
import dev.priyanshu.arc.ui.components.PreviewFrame
import dev.priyanshu.arc.ui.components.RoundIconButton
import dev.priyanshu.arc.ui.components.StrokeGlyph
import dev.priyanshu.arc.ui.components.TouchTarget
import dev.priyanshu.arc.ui.theme.Space
import dev.priyanshu.arc.ui.theme.StreakTheme
import java.time.DayOfWeek
import java.util.Locale
import java.time.format.TextStyle as DayNameStyle

@Composable
fun StepHeader(
    currentStep: Int,
    totalSteps: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Space.s6, end = Space.s2, top = Space.s4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                // TalkBack reads one sentence instead of four silent bars.
                .clearAndSetSemantics { contentDescription = "Step $currentStep of $totalSteps" },
            horizontalArrangement = Arrangement.spacedBy(Space.s1),
        ) {
            repeat(totalSteps) { index ->
                Box(
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
) {
    IconButton(onClick = onClick, modifier = Modifier.size(TouchTarget)) {
        Icon(
            painter = painterResource(R.drawable.ic_close),
            contentDescription = "Close",
            tint = StreakTheme.colors.ink,
        )
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
    backText: String = "Back",
) {
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Space.s6, end = Space.s6, top = Space.s4, bottom = Space.s6),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (onBack != null) {
                LargeButton(text = backText, onClick = onBack, filled = false, modifier = Modifier.width(112.dp))
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

// Top of a full-screen editor inside the flow: × then the title.
@Composable
fun EditorTopBar(title: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Space.s2, end = Space.s6, top = Space.s4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s1),
    ) {
        CloseButton(onClick = onClose)
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

// Small bold label above a field: "Name", "Starts", "How long".
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelLarge, modifier = modifier)
}

// The message under a field that isn't ready yet. The design system has no
// error colour; larch means "notice this", so the icon and border use it and
// the words stay in plain ink.
@Composable
fun FieldError(message: String, modifier: Modifier = Modifier) {
    val larch = StreakTheme.colors.larch
    Row(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_info),
            contentDescription = null,
            tint = StreakTheme.colors.larch,
            modifier = Modifier.size(16.dp),
        )
        Text(message, style = MaterialTheme.typography.bodySmall)
    }
}

// A one-line text input in the Streak Season style: label above, sunken
// field, larch border and message when there's an error.
@Composable
fun StreakTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        FieldLabel(label)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            isError = error != null,
            placeholder = if (placeholder != null) { { Text(placeholder) } } else null,
            shape = MaterialTheme.shapes.small,
            colors = streakFieldColors(),
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = label },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        )
        if (error != null) FieldError(error)
    }
}

// Material's outlined field, recoloured with our tokens. Shared by every text
// input in the create flow so they all look the same.
@Composable
internal fun streakFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = StreakTheme.colors.sunken,
    unfocusedContainerColor = StreakTheme.colors.sunken,
    errorContainerColor = StreakTheme.colors.sunken,
    focusedBorderColor = StreakTheme.colors.evergreen,
    unfocusedBorderColor = StreakTheme.colors.lineStrong,
    errorBorderColor = StreakTheme.colors.larch,
    cursorColor = StreakTheme.colors.evergreen,
    errorCursorColor = StreakTheme.colors.larch,
)

// Looks like a field, behaves like a button: tapping it opens the date picker.
@Composable
fun DateField(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    val colors = StreakTheme.colors
    val shape = MaterialTheme.shapes.small
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(colors.sunken)
            .border(if (isError) 2.dp else 1.5.dp, if (isError) colors.larch else colors.lineStrong, shape)
            .clickable(onClickLabel = "Change date", role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_calendar),
            contentDescription = null,
            tint = colors.ink,
            modifier = Modifier.size(20.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

// One pill in a pick-one row ("30 days", "60 days"…). The parent Row must use
// Modifier.selectableGroup() so TalkBack reads them as one group.
@Composable
fun ChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StreakTheme.colors
    Box(
        modifier = modifier
            .height(TouchTarget)
            .clip(CircleShape)
            .background(if (selected) colors.evergreenSoft else Color.Transparent)
            .border(1.5.dp, if (selected) colors.evergreen else colors.lineStrong, CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Space.s4),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.evergreen else colors.ink,
        )
    }
}

// Two or three choices side by side in one sunken pill; exactly one is chosen.
// Generic over T so it works for Boolean (required/bonus), ScheduleType, etc.
@Composable
fun <T> SegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StreakTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(colors.sunken)
            .padding(Space.s1)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Space.s1),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(TouchTarget)
                    .clip(CircleShape)
                    .background(if (isSelected) colors.surface else Color.Transparent)
                    .border(1.5.dp, if (isSelected) colors.evergreen else Color.Transparent, CircleShape)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(value) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) colors.ink else colors.inkMuted,
                )
            }
        }
    }
}

// A card you pick from a set: the four commitment kinds, the three strictness
// levels. showRadio adds the round dot the strictness cards use.
@Composable
fun OptionCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showRadio: Boolean = false,
) {
    val colors = StreakTheme.colors
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (selected) colors.evergreenSoft else colors.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.evergreen else colors.line, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(if (showRadio) Space.s4 else 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (showRadio) RadioDot(selected)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
        }
    }
}

@Composable
private fun RadioDot(selected: Boolean) {
    val colors = StreakTheme.colors
    Box(
        modifier = Modifier
            .padding(top = 2.dp)
            .size(20.dp)
            .border(2.dp, if (selected) colors.evergreen else colors.lineStrong, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(9.dp).clip(CircleShape).background(colors.evergreen))
    }
}

// Seven round toggles, Monday first. Any number can be on. The numbers match
// schedule_days in schema.sql: Monday = 1 … Sunday = 7 (ISO, same as DayOfWeek).
@Composable
fun DayPicker(
    selectedDays: Set<Int>,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StreakTheme.colors
    val locale = Locale.getDefault()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DayOfWeek.entries.forEach { day ->
            val iso = day.value
            val on = iso in selectedDays
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(TouchTarget)
                    .clip(CircleShape)
                    .background(if (on) colors.evergreen else Color.Transparent)
                    .border(1.5.dp, if (on) colors.evergreen else colors.lineStrong, CircleShape)
                    .toggleable(value = on, role = Role.Checkbox, onValueChange = { onToggle(iso) })
                    .semantics { contentDescription = day.getDisplayName(DayNameStyle.FULL, locale) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    day.getDisplayName(DayNameStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) colors.onEvergreen else colors.ink,
                )
            }
        }
    }
}

// "Required" in evergreen or "Bonus" in larch: the same pair the design uses
// everywhere a commitment is listed.
@Composable
fun RequirementChip(isRequired: Boolean, modifier: Modifier = Modifier) {
    val colors = StreakTheme.colors
    Text(
        text = if (isRequired) "Required" else "Bonus",
        style = MaterialTheme.typography.labelMedium,
        color = if (isRequired) colors.evergreen else colors.larch,
        modifier = modifier
            .clip(CircleShape)
            .background(if (isRequired) colors.evergreenSoft else colors.larchSoft)
            .padding(horizontal = Space.s2, vertical = 2.dp),
    )
}

// One commitment in the step-2 list. The whole card is the tap target for
// editing; the pencil is only a visual hint.
@Composable
fun CommitmentListCard(
    name: String,
    detail: String,
    isRequired: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StreakTheme.colors
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.line, shape)
            .clickable(onClickLabel = "Edit", role = Role.Button, onClick = onClick)
            .padding(start = Space.s4, end = Space.s2, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                RequirementChip(isRequired)
            }
            Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
        }
        Icon(
            painter = painterResource(R.drawable.ic_edit),
            contentDescription = null,
            tint = colors.inkMuted,
            modifier = Modifier.padding(Space.s2).size(20.dp),
        )
    }
}

// The dashed "+ Add …" row under a list. Compose has no dashed border, so the
// outline is drawn by hand behind the content.
@Composable
fun DashedAddButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = StreakTheme.colors
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    color = colors.lineStrong,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                    ),
                )
            }
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_add),
            contentDescription = null,
            tint = colors.evergreen,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(Space.s2))
        Text(text, style = MaterialTheme.typography.labelLarge, color = colors.evergreen)
    }
}

// A plain card on frost: surface, hairline border, rounded. No padding of its
// own, so rows inside can run edge to edge with dividers between them.
@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(StreakTheme.colors.surface)
            .border(1.dp, StreakTheme.colors.line, shape),
        content = content,
    )
}

// A setting you change one step at a time: title, a line explaining it, and
// − / + buttons. leading is an optional mark on the left (the rest-day disc).
@Composable
fun StepperRow(
    title: String,
    subtitle: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    decreaseLabel: String,
    increaseLabel: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(Space.s4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(
            Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = StreakTheme.colors.inkMuted)
        }
        RoundIconButton(glyph = StrokeGlyph.MINUS, filled = false, contentDescription = decreaseLabel, onClick = onDecrease)
        RoundIconButton(glyph = StrokeGlyph.PLUS, filled = true, contentDescription = increaseLabel, onClick = onIncrease)
    }
}

// A card whose body shows only after its header is tapped ("Advanced").
@Composable
fun ExpandableSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    SectionCard(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clickable(
                    onClickLabel = if (expanded) "Collapse" else "Expand",
                    role = Role.Button,
                    onClick = onToggle,
                )
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                .padding(horizontal = Space.s4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(R.drawable.ic_expand_more),
                contentDescription = null,
                tint = StreakTheme.colors.ink,
                modifier = Modifier.rotate(if (expanded) 180f else 0f),
            )
        }
        if (expanded) {
            HorizontalDivider(color = StreakTheme.colors.line)
            content()
        }
    }
}

// One summary card on Review: a title, an Edit button that jumps to the
// step it came from, and the summary under them.
@Composable
fun ReviewCard(
    title: String,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = MaterialTheme.typography.titleLarge,
    content: @Composable ColumnScope.() -> Unit,
) {
    SectionCard(modifier) {
        Column(Modifier.padding(Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s1)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = titleStyle, modifier = Modifier.weight(1f))
                TextButton(
                    onClick = onEdit,
                    // Four "Edit" buttons on one screen: TalkBack needs to know which is which.
                    modifier = Modifier.semantics { contentDescription = "Edit $title" },
                ) {
                    Text("Edit", style = MaterialTheme.typography.labelLarge, color = StreakTheme.colors.evergreen)
                }
            }
            content()
        }
    }
}

@PreviewLightDark
@Composable
private fun CommitmentListPreview() {
    PreviewFrame {
        Column(Modifier.padding(Space.s6), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            CommitmentListCard("Water", "Reach 3,000 ml · Every day", isRequired = true, onClick = {})
            CommitmentListCard("Reading before bed, phone in another room", "Spend 30 min · Every day", isRequired = false, onClick = {})
            DashedAddButton("Add a commitment", onClick = {})
        }
    }
}

@PreviewLightDark
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

@PreviewLightDark
@Composable
private fun FieldsPreview() {
    PreviewFrame {
        Column(Modifier.padding(Space.s6), verticalArrangement = Arrangement.spacedBy(Space.s6)) {
            StreakTextField(value = "Winter Arc", onValueChange = {}, label = "Name")
            StreakTextField(value = "", onValueChange = {}, label = "Name", error = "Give it a name to continue.")
            Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                FieldLabel("Starts")
                DateField(text = "Today · Thu 22 Oct", onClick = {})
            }
            DateField(text = "Mon 19 Oct", onClick = {}, isError = true)
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                ChoiceChip("30 days", selected = false, onClick = {})
                ChoiceChip("90 days", selected = true, onClick = {})
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun EditorPiecesPreview() {
    PreviewFrame {
        Column(Modifier.padding(Space.s6), verticalArrangement = Arrangement.spacedBy(Space.s6)) {
            SegmentedControl(
                options = listOf(true to "Required", false to "Bonus"),
                selected = true,
                onSelect = {},
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                OptionCard("Tick off", "Done or not done", selected = false, onClick = {}, modifier = Modifier.weight(1f))
                OptionCard("Reach a count", "Like 3,000 ml of water", selected = true, onClick = {}, modifier = Modifier.weight(1f))
            }
            OptionCard(
                title = "Standard",
                description = "A missed day breaks your streak, but the arc carries on.",
                selected = true,
                onClick = {},
                showRadio = true,
            )
            DayPicker(selectedDays = setOf(6, 7), onToggle = {})
        }
    }
}

@PreviewLightDark
@Composable
private fun StepBottomBarPreview() {
    PreviewFrame {
        StepBottomBar(primaryText = "Next", onPrimary = {}, onBack = null)
        StepBottomBar(primaryText = "Begin Winter Arc", onPrimary = {}, onBack = {})
    }
}

@PreviewLightDark
@Composable
private fun SettingsPreview() {
    PreviewFrame {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s4)) {
            SectionCard {
                StepperRow(
                    title = "Rest days",
                    subtitle = "1 a week · your streak carries on",
                    onDecrease = {},
                    onIncrease = {},
                    decreaseLabel = "One fewer rest day a week",
                    increaseLabel = "One more rest day a week",
                )
            }
            ExpandableSection(title = "Advanced", expanded = true, onToggle = {}) {
                StepperRow(
                    title = "Day starts at 4:00 AM",
                    subtitle = "Late nights count as the day before.",
                    onDecrease = {},
                    onIncrease = {},
                    decreaseLabel = "Start the day an hour earlier",
                    increaseLabel = "Start the day an hour later",
                )
            }
            ExpandableSection(title = "Advanced", expanded = false, onToggle = {}) {}
        }
    }
}

@PreviewLightDark
@Composable
private fun ReviewCardPreview() {
    PreviewFrame {
        ReviewCard(title = "Winter Arc", onEdit = {}) {
            Text("60 days · starts today", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Thu 22 Oct – Sun 20 Dec 2026",
                style = MaterialTheme.typography.bodySmall,
                color = StreakTheme.colors.inkMuted,
            )
        }
    }
}
