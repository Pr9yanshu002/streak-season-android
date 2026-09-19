package dev.priyanshu.arc.domain.model

import java.time.LocalDate

// A day row exists only once the user has touched that date (schema.sql).
// Absence of a Day for a date is meaningful: nothing was logged. Day state
// itself is never stored here — it's derived by the rules engine.
data class Day(
    val date: LocalDate,
    val entries: List<Entry> = emptyList(),
    val restTokenUsed: Boolean = false,
)
