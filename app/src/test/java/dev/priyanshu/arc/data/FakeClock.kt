package dev.priyanshu.arc.data

import java.time.LocalDateTime
import java.time.ZoneId

// Test-only Clock: starts at a fixed instant and only moves when told to,
// so behaviour around the day-boundary hour can be tested without touching
// the device clock.
class FakeClock(private var current: LocalDateTime) : Clock {
    override fun now(): LocalDateTime = current
    override fun millis(): Long = current.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun advanceTo(dateTime: LocalDateTime) {
        current = dateTime
    }
}
