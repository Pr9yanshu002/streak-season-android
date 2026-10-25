package dev.priyanshu.arc.ui.create

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Date and day-count words shared by the create screen and its ViewModel.
// Each formatter is built on use, so a language change while the app runs
// shows up the next time the text is drawn.

// "Thu 22 Oct"
internal fun LocalDate.shortText(): String = format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))

// "Thu 22 Oct 2026"
internal fun LocalDate.fullText(): String = format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault()))

// "1 day", "3 days"
internal fun daysText(n: Int): String = if (n == 1) "1 day" else "$n days"
