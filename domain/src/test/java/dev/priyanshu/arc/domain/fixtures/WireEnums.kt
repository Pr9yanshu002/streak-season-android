package dev.priyanshu.arc.domain.fixtures

import dev.priyanshu.arc.domain.model.ArcStatus
import dev.priyanshu.arc.domain.model.CommitmentStatus
import dev.priyanshu.arc.domain.model.CommitmentType
import dev.priyanshu.arc.domain.model.DayState
import dev.priyanshu.arc.domain.model.MilestoneKind
import dev.priyanshu.arc.domain.model.ScheduleType
import dev.priyanshu.arc.domain.model.Strictness

// Fixture JSON's wire strings for the domain enums (schema.sql's TEXT
// columns). Kept here, not on the enums themselves — the enums are plain
// domain types with no notion of a serialized form.

fun parseArcStatus(raw: String): ArcStatus = when (raw) {
    "draft" -> ArcStatus.DRAFT
    "active" -> ArcStatus.ACTIVE
    "completed" -> ArcStatus.COMPLETED
    "failed" -> ArcStatus.FAILED
    "archived" -> ArcStatus.ARCHIVED
    else -> error("Unknown arc status '$raw'")
}

fun parseStrictness(raw: String): Strictness = when (raw) {
    "strict" -> Strictness.STRICT
    "standard" -> Strictness.STANDARD
    "chill" -> Strictness.CHILL
    else -> error("Unknown strictness '$raw'")
}

fun parseCommitmentType(raw: String): CommitmentType = when (raw) {
    "binary" -> CommitmentType.BINARY
    "counter" -> CommitmentType.COUNTER
    "duration" -> CommitmentType.DURATION
    "log" -> CommitmentType.LOG
    else -> error("Unknown commitment type '$raw'")
}

fun parseScheduleType(raw: String): ScheduleType = when (raw) {
    "daily" -> ScheduleType.DAILY
    "weekdays" -> ScheduleType.WEEKDAYS
    else -> error("Unknown schedule type '$raw'")
}

fun parseCommitmentStatus(raw: String): CommitmentStatus = when (raw) {
    "not_scheduled" -> CommitmentStatus.NOT_SCHEDULED
    "pending" -> CommitmentStatus.PENDING
    "done" -> CommitmentStatus.DONE
    else -> error("Unknown commitment status '$raw'")
}

fun parseMilestoneKind(raw: String): MilestoneKind = when (raw) {
    "start" -> MilestoneKind.START
    "p25" -> MilestoneKind.P25
    "p50" -> MilestoneKind.P50
    "p75" -> MilestoneKind.P75
    "final" -> MilestoneKind.FINAL
    else -> error("Unknown milestone kind '$raw'")
}

fun parseDayState(raw: String): DayState = when (raw) {
    "future" -> DayState.FUTURE
    "out_of_range" -> DayState.OUT_OF_RANGE
    "complete" -> DayState.COMPLETE
    "pending" -> DayState.PENDING
    "rest" -> DayState.REST
    "missed" -> DayState.MISSED
    "partial" -> DayState.PARTIAL
    else -> error("Unknown day state '$raw'")
}
