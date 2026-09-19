package dev.priyanshu.arc.domain.fixtures

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire-shaped DTOs matching contract/fixtures/README.md exactly. No coupling
// to the domain models — FixtureMapper.kt converts these to the
// Arc/Commitment/Day the engine actually takes. dates and enum-like fields
// stay raw strings here; parsing them is the mapper's job.
@Serializable
data class FixtureDto(
    val id: String,
    val rules: List<String>,
    val description: String,
    val now: String,
    val arc: ArcDto,
    val commitments: List<CommitmentDto>,
    val days: List<DayDto>,
    val expect: ExpectDto,
)

@Serializable
data class ArcDto(
    val id: String,
    val name: String,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String,
    val status: String,
    val strictness: String,
    @SerialName("rest_tokens_per_week") val restTokensPerWeek: Int,
    @SerialName("backfill_days") val backfillDays: Int,
    @SerialName("day_boundary_hour") val dayBoundaryHour: Int,
)

@Serializable
data class CommitmentDto(
    val id: String,
    val name: String,
    val type: String,
    @SerialName("target_value") val targetValue: Double? = null,
    val unit: String? = null,
    @SerialName("is_required") val isRequired: Boolean,
    @SerialName("schedule_type") val scheduleType: String,
    @SerialName("schedule_days") val scheduleDays: String? = null,
    @SerialName("active_from") val activeFrom: String,
    @SerialName("active_until") val activeUntil: String? = null,
)

// entries is an object keyed by commitment id, e.g. {"c-gym": 1} — a plain
// Map<String, Double> decodes it natively. No custom serializer needed.
@Serializable
data class DayDto(
    val date: String,
    val entries: Map<String, Double> = emptyMap(),
    @SerialName("rest_token_used") val restTokenUsed: Boolean = false,
)
