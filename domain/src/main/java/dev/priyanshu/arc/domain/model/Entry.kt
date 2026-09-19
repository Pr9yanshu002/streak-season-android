package dev.priyanshu.arc.domain.model

// Mirrors one row of the entries table, trimmed to what the rules engine
// reads: which commitment, and what value was logged. Row identity and
// timestamps are persistence concerns and live in :app.
data class Entry(
    val commitmentId: String,
    val value: Double,
)
