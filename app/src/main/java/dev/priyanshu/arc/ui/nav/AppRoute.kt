package dev.priyanshu.arc.ui.nav

// Destinations switched on in-memory state — still no androidx.navigation
// dependency (docs/tasks/11/15: "No new dependencies"). Recap (docs/tasks/15)
// is the first genuine third route, but it carries one id and has no deep back
// stack, so the when-switch in MainActivity still covers it; a real nav graph
// waits until this stops being enough.
sealed interface AppRoute {
    data object Today : AppRoute

    // remixFromArcId is non-null only for the remix path (docs/tasks/12) —
    // the create flow pre-fills name/commitments from that arc when present.
    data class CreateArc(val remixFromArcId: String? = null) : AppRoute

    // The recap for an ended arc (docs/tasks/15). Reached from Today's Ended
    // state via "View recap"; carries the ended arc's id.
    data class Recap(val arcId: String) : AppRoute

    // Debug-only (gated on the manifest's debuggable flag, same as
    // DebugSeeder's auto-seed) — a scenario picker for jumping straight to
    // any flow instead of grinding through real days. Doesn't count toward
    // "a third route" above: it never ships, and never appears in a release
    // build's nav graph.
    data object DebugMenu : AppRoute
}
