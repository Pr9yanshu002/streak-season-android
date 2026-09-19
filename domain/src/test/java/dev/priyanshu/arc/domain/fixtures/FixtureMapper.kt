package dev.priyanshu.arc.domain.fixtures

import dev.priyanshu.arc.domain.model.Arc
import dev.priyanshu.arc.domain.model.Commitment
import dev.priyanshu.arc.domain.model.Day
import dev.priyanshu.arc.domain.model.Entry
import java.time.LocalDate

fun ArcDto.toDomain(): Arc = Arc(
    id = id,
    name = name,
    startDate = LocalDate.parse(startDate),
    endDate = LocalDate.parse(endDate),
    status = parseArcStatus(status),
    strictness = parseStrictness(strictness),
    restTokensPerWeek = restTokensPerWeek,
    backfillDays = backfillDays,
    dayBoundaryHour = dayBoundaryHour,
)

fun CommitmentDto.toDomain(): Commitment = Commitment(
    id = id,
    name = name,
    type = parseCommitmentType(type),
    targetValue = targetValue,
    unit = unit,
    isRequired = isRequired,
    scheduleType = parseScheduleType(scheduleType),
    scheduleDays = scheduleDays,
    activeFrom = LocalDate.parse(activeFrom),
    activeUntil = activeUntil?.let(LocalDate::parse),
)

fun DayDto.toDomain(): Day = Day(
    date = LocalDate.parse(date),
    entries = entries.map { (commitmentId, value) -> Entry(commitmentId, value) },
    restTokenUsed = restTokenUsed,
)
