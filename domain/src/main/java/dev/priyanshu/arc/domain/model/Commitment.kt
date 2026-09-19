package dev.priyanshu.arc.domain.model

import java.time.LocalDate

// Mirrors the commitments table (schema.sql), trimmed the same way Arc is.
// scheduleDays stays a raw '1,3,5' string, exactly as stored — parsing it
// into weekday numbers is R3, which belongs to the rules engine, not here.
data class Commitment(
    val id: String,
    val name: String,
    val type: CommitmentType,
    val targetValue: Double? = null,
    val unit: String? = null,
    val isRequired: Boolean,
    val scheduleType: ScheduleType,
    val scheduleDays: String? = null,
    val activeFrom: LocalDate,
    val activeUntil: LocalDate? = null,
)
