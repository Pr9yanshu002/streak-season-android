package dev.priyanshu.arc.ui.create

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Task 19 — the number shown in "Step N of 4" and the header bars. Review is
// not numbered, so the count never reads "Step 5 of 4".
class CreateArcStepTest {
    @Test
    fun `the four input steps are numbered 1 to 4`() {
        assertEquals(1, CreateArcStep.NAME_AND_DATES.number)
        assertEquals(2, CreateArcStep.COMMITMENTS.number)
        assertEquals(3, CreateArcStep.STRICTNESS.number)
        assertEquals(4, CreateArcStep.INTENTION.number)
    }

    @Test
    fun `review has no number`() {
        assertNull(CreateArcStep.REVIEW.number)
    }

    @Test
    fun `every numbered step fits within the step count`() {
        val numbers = CreateArcStep.entries.mapNotNull { it.number }
        assertEquals((1..NUMBERED_STEP_COUNT).toList(), numbers)
    }
}
