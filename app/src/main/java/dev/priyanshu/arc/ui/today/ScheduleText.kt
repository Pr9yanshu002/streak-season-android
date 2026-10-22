package dev.priyanshu.arc.ui.today

import java.time.DayOfWeek

// docs/tasks/18 — a commitment's schedule in words, for the "Not today · …"
// row. Presentation only: whether a commitment is scheduled on a given date is
// still the engine's answer (R3, Resolution.commitmentStatuses). This only
// describes a set of weekdays.
//
//   all seven            -> "Every day"
//   Monday to Friday     -> "Weekdays"
//   Saturday and Sunday  -> "Saturdays and Sundays"
//   anything else        -> "Mon, Wed, Fri" (Monday first)
//
// An empty set returns "" — the caller shows plain "Not today" for that.
fun formatSchedule(days: Set<DayOfWeek>): String {
    val weekdays = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )
    val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    return when {
        days.size == 7 -> "Every day"
        days == weekdays -> "Weekdays"
        days == weekend -> "Saturdays and Sundays"
        else -> days.sorted().joinToString(", ") { shortDayName(it) }
    }
}

// Fixed English, like every other string on the screen.
private fun shortDayName(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "Mon"
    DayOfWeek.TUESDAY -> "Tue"
    DayOfWeek.WEDNESDAY -> "Wed"
    DayOfWeek.THURSDAY -> "Thu"
    DayOfWeek.FRIDAY -> "Fri"
    DayOfWeek.SATURDAY -> "Sat"
    DayOfWeek.SUNDAY -> "Sun"
}
