package dev.priyanshu.arc.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import dev.priyanshu.arc.ui.theme.Space
import dev.priyanshu.arc.ui.theme.StreakTheme

/**
 * The milestone reflection box (designs 04/05): "<label> · optional", a
 * three-line field in the user's-own-words voice (Literata italic) on sunken,
 * and a helper line. The text is held by the caller.
 */
@Composable
fun ReflectionField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    helper: String,
    modifier: Modifier = Modifier,
    placeholder: String = "How is it going?",
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val voice = StreakTheme.type.voice
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        Text(
            buildAnnotatedString {
                append(label)
                withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = muted)) { append(" · optional") }
            },
            style = MaterialTheme.typography.labelLarge,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            minLines = 3,
            textStyle = voice.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = label },
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .background(StreakTheme.colors.sunken, MaterialTheme.shapes.small)
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                        .padding(Space.s4),
                ) {
                    if (value.isEmpty()) Text(placeholder, style = voice, color = muted)
                    innerTextField()
                }
            },
        )
        Text(helper, style = MaterialTheme.typography.bodySmall, color = muted)
    }
}

/**
 * The full-width 56 dp pill button the designs use for a screen's main
 * actions (Continue, Start an arc, See your recap). [filled] = evergreen;
 * otherwise outlined with a line-strong edge.
 */
@Composable
fun LargeButton(text: String, onClick: () -> Unit, filled: Boolean, modifier: Modifier = Modifier) {
    val buttonModifier = modifier.fillMaxWidth().height(56.dp)
    val textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
    if (filled) {
        Button(onClick = onClick, shape = CircleShape, modifier = buttonModifier) {
            Text(text, style = textStyle)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            shape = CircleShape,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            modifier = buttonModifier,
        ) {
            Text(text, style = textStyle)
        }
    }
}

@PreviewLightDark
@Composable
private fun FormsPreview() {
    var text by remember { mutableStateOf("") }
    PreviewFrame {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s4)) {
            ReflectionField(
                value = text,
                onValueChange = { text = it },
                label = "A line for later",
                helper = "You'll see it again in your recap.",
            )
            ReflectionField(
                value = "Slow week, but I kept going.",
                onValueChange = {},
                label = "A line for later",
                helper = "You'll see it again in your recap.",
            )
            LargeButton(text = "Continue", onClick = {}, filled = true)
            LargeButton(text = "Start something new", onClick = {}, filled = false)
        }
    }
}
