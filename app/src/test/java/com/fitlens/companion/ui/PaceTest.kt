package com.fitlens.companion.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pace on the Stats tab (#24): time per distance in the exercise's unit, metres per 100 m. */
class PaceTest {

    @Test
    fun pacePerKilometreAndMile() {
        assertEquals("5:00 /km", pace(1500.0, 5.0, "km"))
        assertEquals("8:30 /mi", pace(1530.0, 3.0, "mi"))
    }

    @Test
    fun metresArePacedPer100() {
        assertEquals("1:45 /100 m", pace(840.0, 800.0, "m"))
    }

    @Test
    fun noDistanceHasNoPace() {
        assertEquals("—", pace(600.0, 0.0, "km"))
    }
}
