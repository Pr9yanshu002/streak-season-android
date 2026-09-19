package dev.priyanshu.arc.domain.fixtures

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire-shaped, like FixtureDto — dates and enum-like fields stay raw strings,
// parsed in the test assertions. Every field is optional — a fixture only
// asserts what it's about (fixtures/README.md). failed_on_date is the one
// field that's conditionally required: whenever arc_status is present,
// failed_on_date must be too (null meaning "did not fail"). That convention
// is what lets this stay a plain nullable-fields data class — there's no
// field where "key omitted" and "key present but null" need to mean
// different things.
@Serializable
data class ExpectDto(
    @SerialName("effective_today") val effectiveToday: String? = null,
    @SerialName("day_states") val dayStates: Map<String, String>? = null,
    @SerialName("current_streak") val currentStreak: Int? = null,
    @SerialName("best_streak") val bestStreak: Int? = null,
    @SerialName("rest_tokens_remaining") val restTokensRemaining: Map<String, Int>? = null,
    @SerialName("completion_pct") val completionPct: Double? = null,
    @SerialName("arc_status") val arcStatus: String? = null,
    @SerialName("failed_on_date") val failedOnDate: String? = null,
    @SerialName("sealed_dates") val sealedDates: List<String>? = null,
    @SerialName("day_number") val dayNumber: Int? = null,
    @SerialName("total_days") val totalDays: Int? = null,
    // date -> commitment id -> status string ("not_scheduled" / "pending" / "done").
    @SerialName("commitment_statuses") val commitmentStatuses: Map<String, Map<String, String>>? = null,
)
