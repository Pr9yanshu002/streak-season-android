package dev.priyanshu.arc.data

import java.time.LocalDateTime

// The one seam between :app and the wall clock. RulesEngine.resolve() takes
// `now` as a parameter and never reads the clock itself (CLAUDE.md) — this
// is what supplies it. Everything that needs the current time takes a Clock;
// nothing outside SystemClock calls LocalDateTime.now() directly.
interface Clock {
    fun now(): LocalDateTime

    // Epoch millis, for entity created_at/updated_at/deleted_at columns.
    fun millis(): Long
}

class SystemClock : Clock {
    override fun now(): LocalDateTime = LocalDateTime.now()
    override fun millis(): Long = System.currentTimeMillis()
}
