package com.fitlens.companion.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The chart kind remembered per graph (#137): stored as "graph=kind;…" in the portable settings. */
class ChartKindTest {

    @Test
    fun choicesRoundTrip() {
        val m = mapOf("exercise:Estimated 1RM" to ChartKind.BAR, "body:Bodyweight" to ChartKind.STEP, "analysis:Volume" to ChartKind.AREA)
        assertEquals(m, ChartKind.decode(ChartKind.encode(m)))
    }

    @Test
    fun nothingChosenIsEmpty() {
        assertNull(ChartKind.encode(emptyMap()))
        assertEquals(emptyMap<String, ChartKind>(), ChartKind.decode(null))
        assertEquals(emptyMap<String, ChartKind>(), ChartKind.decode(""))
    }

    @Test
    fun unknownKindsFromALaterBuildAreSkipped() {
        assertEquals(mapOf("a" to ChartKind.LINE), ChartKind.decode("a=line;b=radar;broken"))
    }

    @Test
    fun aGraphNameWithAnEqualsSignStillReadsBack() {
        val m = mapOf("exercise:Max weight = reps" to ChartKind.BAR)
        assertEquals(m, ChartKind.decode(ChartKind.encode(m)))
    }
}
