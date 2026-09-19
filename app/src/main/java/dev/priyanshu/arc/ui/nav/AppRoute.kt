package dev.priyanshu.arc.ui.nav

// Two destinations, switched on in-memory state — no androidx.navigation
// dependency pulled in for a two-route app (docs/tasks/11-arc-creation.md:
// "nothing speculative beyond that"). Add a real nav graph when a third
// route makes this stop being enough.
sealed interface AppRoute {
    data object Today : AppRoute
    data object CreateArc : AppRoute
}
