package dev.priyanshu.arc.ui.today

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Design 08's "Ninety days, done." spells the arc's length out with the
// platform's ICU (android.icu), so this runs under Robolectric to use the real
// thing rather than a JVM stand-in.
@RunWith(RobolectricTestRunner::class)
class SpellOutTest {

    @Test
    fun `arc lengths are spelled out with a capital first letter`() {
        assertEquals("Ninety", spellOut(90))
        assertEquals("Thirty", spellOut(30))
        assertEquals("Seventy-five", spellOut(75))
        assertEquals("One hundred", spellOut(100))
    }
}
