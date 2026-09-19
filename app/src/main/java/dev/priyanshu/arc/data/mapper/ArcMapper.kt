package dev.priyanshu.arc.data.mapper

import dev.priyanshu.arc.data.entity.ArcEntity
import dev.priyanshu.arc.domain.model.Arc
import dev.priyanshu.arc.domain.model.ArcStatus
import dev.priyanshu.arc.domain.model.Strictness
import java.time.LocalDate

fun ArcEntity.toDomain(): Arc = Arc(
    id = id,
    name = name,
    startDate = LocalDate.parse(startDate),
    endDate = LocalDate.parse(endDate),
    status = ArcStatus.valueOf(status.uppercase()),
    strictness = Strictness.valueOf(strictness.uppercase()),
    restTokensPerWeek = restTokensPerWeek,
    backfillDays = backfillDays,
    dayBoundaryHour = dayBoundaryHour,
)
