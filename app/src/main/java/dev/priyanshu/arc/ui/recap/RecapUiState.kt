package dev.priyanshu.arc.ui.recap

sealed interface RecapUiState {
    data object Loading : RecapUiState

    // recap() returned null (the arc isn't ended) or the arc wasn't found —
    // not normally reachable, since the entry point is Today's Ended state, but
    // handled rather than crashed.
    data object Unavailable : RecapUiState

    // The cards to show, already in order with the skip rules applied
    // (docs/tasks/15) — a skipped card is absent from this list, never a blank
    // page. Deciding which cards appear is presentation, done in the view
    // model; no recap number is computed here or in the composables.
    data class Loaded(val cards: List<RecapCard>) : RecapUiState
}

// One screen in the vertical pager. Each holds display-ready values so the
// composables only render — every number originated in Recap (R19).
sealed interface RecapCard {
    data class Title(val arcName: String, val dateRange: String, val headline: String) : RecapCard
    data class Intention(val text: String) : RecapCard
    data class BestStreak(val days: Int) : RecapCard
    data class HardestWeek(val dateRange: String, val completeDays: Int, val countedDays: Int) : RecapCard
    data class WhatYouDid(val rows: List<String>) : RecapCard
    data class Milestones(val rows: List<MilestoneRow>) : RecapCard
    data class Completion(val percent: Int) : RecapCard
    data object WhatNext : RecapCard

    data class MilestoneRow(val label: String, val date: String, val reflection: String?)
}
