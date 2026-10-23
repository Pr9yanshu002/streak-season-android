package dev.priyanshu.arc.ui.create

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.priyanshu.arc.R
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

@Preview(name = "Fields · light")
@Preview(name = "Fields · dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
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