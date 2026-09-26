package dev.priyanshu.arc.domain.rules

import dev.priyanshu.arc.domain.fixtures.FixtureDto
import dev.priyanshu.arc.domain.fixtures.FixtureLoader
import dev.priyanshu.arc.domain.fixtures.parseArcStatus
import dev.priyanshu.arc.domain.fixtures.parseCommitmentStatus
import dev.priyanshu.arc.domain.fixtures.parseDayState
import dev.priyanshu.arc.domain.fixtures.parseMilestoneKind
import dev.priyanshu.arc.domain.fixtures.toDomain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime

// One dynamic test per fixture in contract/fixtures, named after the
// fixture's id. Right now RulesEngine.resolve() is a TODO(), so every one of
// these is expected to fail with NotImplementedError — that's the harness
// being verified, not engine coverage.
class RulesEngineFixtureTest {

    @TestFactory
    fun fixtures(): List<DynamicTest> =
        FixtureLoader.loadAll().map { fixture ->
            DynamicTest.dynamicTest(fixture.id) { runFixture(fixture) }
        }

    private fun runFixture(fixture: FixtureDto) {
        val resolution = RulesEngine.resolve(
            arc = fixture.arc.toDomain(),
            commitments = fixture.commitments.map { it.toDomain() },
            days = fixture.days.map { it.toDomain() },
            now = LocalDateTime.parse(fixture.now),
        )
        val expect = fixture.expect

        expect.effectiveToday?.let {
            assertEquals(LocalDate.parse(it), resolution.effectiveToday, "[${fixture.id}] effective_today")
        }

        expect.dayStates?.forEach { (dateStr, stateStr) ->
            assertEquals(
                parseDayState(stateStr),
                resolution.dayStates[LocalDate.parse(dateStr)],
                "[${fixture.id}] day_states[$dateStr]",
            )
        }

        expect.currentStreak?.let {
            assertEquals(it, resolution.currentStreak, "[${fixture.id}] current_streak")
        }

        expect.bestStreak?.let {
            assertEquals(it, resolution.bestStreak, "[${fixture.id}] best_streak")
        }

        expect.restTokensRemaining?.forEach { (week, remaining) ->
            assertEquals(
                remaining,
                resolution.restTokensRemaining[week],
                "[${fixture.id}] rest_tokens_remaining[$week]",
            )
        }

        expect.completionPct?.let {
            assertEquals(round4(it), round4(resolution.completionPct), "[${fixture.id}] completion_pct")
        }

        // failed_on_date is only meaningful alongside arc_status
        // (fixtures/README.md) — asserted together so there's never a need to
        // tell "key omitted" apart from "key present but null".
        expect.arcStatus?.let { rawStatus ->
            assertEquals(parseArcStatus(rawStatus), resolution.arcStatus, "[${fixture.id}] arc_status")
            assertEquals(
                expect.failedOnDate?.let(LocalDate::parse),
                resolution.failedOnDate,
                "[${fixture.id}] failed_on_date",
            )
        }

        expect.sealedDates?.let {
            assertEquals(it.map(LocalDate::parse).toSet(), resolution.sealedDates, "[${fixture.id}] sealed_dates")
        }

        expect.dayNumber?.let {
            assertEquals(it, resolution.dayNumber, "[${fixture.id}] day_number")
        }

        expect.totalDays?.let {
            assertEquals(it, resolution.totalDays, "[${fixture.id}] total_days")
        }

        expect.commitmentStatuses?.forEach { (dateStr, statusesByCommitmentId) ->
            val date = LocalDate.parse(dateStr)
            statusesByCommitmentId.forEach { (commitmentId, statusStr) ->
                assertEquals(
                    parseCommitmentStatus(statusStr),
                    resolution.commitmentStatuses[date]?.get(commitmentId),
                    "[${fixture.id}] commitment_statuses[$dateStr][$commitmentId]",
                )
            }
        }

        // Whole-map comparison, not key-by-key (fixtures/README.md) — a kind
        // the engine returns that the fixture doesn't list must fail, the
        // same reasoning as commit_status_no_missing_keys. A partial
        // assertion would let a threshold firing too early pass silently.
        expect.requiredMilestones?.let { expectedRaw ->
            val expected = expectedRaw.entries.associate { (kind, date) ->
                parseMilestoneKind(kind) to LocalDate.parse(date)
            }
            assertEquals(expected, resolution.requiredMilestones, "[${fixture.id}] required_milestones")
        }
    }

    // completion_pct compares to 4 decimal places, round half up (README.md).
    private fun round4(value: Double): BigDecimal =
        BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP)
}
