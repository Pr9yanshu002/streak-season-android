package dev.priyanshu.arc.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.priyanshu.arc.R

// Bricolage Grotesque — the app's voice: every label, number, heading, button.
// Static weights (400 / 600 / 700) rather than the variable font, per
// docs/tasks/16, so there is no optical-size or weight axis at runtime.
val Bricolage = FontFamily(
    Font(R.font.bricolage_grotesque_regular, FontWeight.Normal),
    Font(R.font.bricolage_grotesque_semibold, FontWeight.SemiBold),
    Font(R.font.bricolage_grotesque_bold, FontWeight.Bold),
)

// Literata — only ever the italic 400, only ever for words the user wrote.
// Exposed only as StreakTheme.type.voice so no screen mistakes it for body text.
val Literata = FontFamily(
    Font(R.font.literata_italic, FontWeight.Normal, FontStyle.Italic),
)

// The six ss-* styles from tokens.json. All Bricolage.
// tokens.json asks weight 650 on ss-title; 600 (SemiBold) is the nearest static
// weight shipped — see the task report.
private val SsDisplay = TextStyle(
    fontFamily = Bricolage,
    fontWeight = FontWeight.Bold,        // 700
    fontSize = 56.sp,
    lineHeight = 56.sp,
    letterSpacing = (-0.02).em,
)
private val SsTitle = TextStyle(
    fontFamily = Bricolage,
    fontWeight = FontWeight.SemiBold,    // 650 → 600
    fontSize = 28.sp,
    lineHeight = 34.sp,
    letterSpacing = (-0.01).em,
)
private val SsHeading = TextStyle(
    fontFamily = Bricolage,
    fontWeight = FontWeight.SemiBold,    // 600
    fontSize = 20.sp,
    lineHeight = 26.sp,
    letterSpacing = 0.em,
)
private val SsBody = TextStyle(
    fontFamily = Bricolage,
    fontWeight = FontWeight.Normal,      // 400
    fontSize = 16.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.em,
)
private val SsLabel = TextStyle(
    fontFamily = Bricolage,
    fontWeight = FontWeight.SemiBold,    // 600
    fontSize = 13.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.02.em,
)
private val SsCaption = TextStyle(
    fontFamily = Bricolage,
    fontWeight = FontWeight.Normal,      // 400
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.em,
)

// The ss-* styles mapped onto Material slots. The six the brief lists are exact;
// every other slot falls back to the assigned member of its own Material role
// family (display* → ss-display, headline* → ss-title, title* → ss-heading,
// body* → ss-body, label* → ss-label) so no slot is ever left on Roboto. See
// the task report for the full table.
val Typography = Typography(
    // display family → ss-display
    displayLarge = SsDisplay,
    displayMedium = SsDisplay,   // ss-display (listed)
    displaySmall = SsDisplay,
    // headline family → ss-title
    headlineLarge = SsTitle,
    headlineMedium = SsTitle,    // ss-title (listed)
    headlineSmall = SsTitle,
    // title family → ss-heading
    titleLarge = SsHeading,      // ss-heading (listed)
    titleMedium = SsHeading,
    titleSmall = SsHeading,
    // body family → ss-body, except bodySmall which the brief pins to ss-caption
    bodyLarge = SsBody,          // ss-body (listed)
    bodyMedium = SsBody,
    bodySmall = SsCaption,       // ss-caption (listed)
    // label family → ss-label
    labelLarge = SsLabel,        // ss-label (listed)
    labelMedium = SsLabel,
    labelSmall = SsLabel,
)

/**
 * Streak Season type that Material's [Typography] has no slot for.
 *
 * [voice] is `ss-voice`: Literata italic, for words the user wrote — the day-1
 * intention, reflections, notes. Reached through [StreakTheme.type] so it can
 * never be mistaken for an ordinary text style.
 */
@Immutable
data class StreakType(
    val voice: TextStyle,
)

val StreakTypography = StreakType(
    voice = TextStyle(
        fontFamily = Literata,
        fontWeight = FontWeight.Normal,
        fontStyle = FontStyle.Italic,
        fontSize = 18.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.em,
    ),
)
