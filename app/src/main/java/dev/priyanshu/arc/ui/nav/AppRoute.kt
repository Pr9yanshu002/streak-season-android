package dev.priyanshu.arc.ui.nav

// Two destinations, switched on in-memory state — no androidx.navigation
// dependency pulled in for a two-route app (docs/tasks/11-arc-creation.md:
// "nothing speculative beyond that"). Add a real nav graph when a third
// route makes this stop being enough.
sealed interface AppRoute {
    data object Today : AppRoute

    // remixFromArcId is non-null only for the remix path (docs/tasks/12) —
    // the create flow pre-fills name/commitments from that arc when present.
    data class CreateArc(val remixFromArcId: String? = null) : AppRoute

    // Debug-only (gated on the manifest's debuggable flag, same as
    // DebugSeeder's auto-seed) — a scenario picker for jumping straight to
    // any flow instead of grinding through real days. Doesn't count toward
    // "a third route" above: it never ships, and never appears in a release
    // build's nav graph.
    data object DebugMenu : AppRoute
}
