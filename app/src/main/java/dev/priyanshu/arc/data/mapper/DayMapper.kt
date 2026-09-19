package dev.priyanshu.arc.data.mapper

import dev.priyanshu.arc.data.entity.DayEntity
import dev.priyanshu.arc.data.entity.EntryEntity
import dev.priyanshu.arc.domain.model.Day
import dev.priyanshu.arc.domain.model.Entry
import java.time.LocalDate

fun EntryEntity.toDomain(): Entry = Entry(
    commitmentId = commitmentId,
    value = value,
)

// The domain Day nests its entries directly (Day.kt), but schema.sql splits
// them into separate days/entries tables — so unlike the other mappers this
// one can't be a plain `EntryEntity.toDomain()` extension on its own; it
// needs the day's entries passed in from the repository, which already has
// both loaded and grouped by day_id.
fun DayEntity.toDomain(entries: List<EntryEntity>): Day = Day(
    date = LocalDate.parse(date),
    entries = entries.map { it.toDomain() },
    restTokenUsed = restTokenUsed == 1,
)
