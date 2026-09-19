package dev.priyanshu.arc.data.mapper

import dev.priyanshu.arc.data.entity.CommitmentEntity
import dev.priyanshu.arc.domain.model.Commitment
import dev.priyanshu.arc.domain.model.CommitmentType
import dev.priyanshu.arc.domain.model.ScheduleType
import java.time.LocalDate

fun CommitmentEntity.toDomain(): Commitment = Commitment(
    id = id,
    name = name,
    type = CommitmentType.valueOf(type.uppercase()),
    targetValue = targetValue,
    unit = unit,
    isRequired = isRequired == 1,
    scheduleType = ScheduleType.valueOf(scheduleType.uppercase()),
    scheduleDays = scheduleDays,
    activeFrom = LocalDate.parse(activeFrom),
    activeUntil = activeUntil?.let(LocalDate::parse),
)
