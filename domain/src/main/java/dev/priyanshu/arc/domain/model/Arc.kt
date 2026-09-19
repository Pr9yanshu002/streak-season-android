package dev.priyanshu.arc.domain.model

import java.time.LocalDate

// Mirrors the arcs table (schema.sql), trimmed to the columns the rules
// engine actually reads. Sync/UI-only columns (metaphor, accent_color,
// created_at, synced_at, ...) belong to the persistence model in :app, not
// here.
data class Arc(
    val id: String,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val status: ArcStatus,
    val strictness: Strictness,
    val restTokensPerWeek: Int,
    val backfillDays: Int,
    val dayBoundaryHour: Int,
)
