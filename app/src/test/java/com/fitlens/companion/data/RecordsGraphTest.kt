package com.fitlens.companion.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** The graphs FitNotes offers that FitLens adds in #22: Max weight for reps and Personal records. */
class RecordsGraphTest {

    private fun set(date: String, kg: Double, reps: Int, pr: Boolean = false) =
        SetRow(0, 1, date, kg, reps, 0.0, 0, pr, null)

    @Test
    fun maxWeightForRepsCountsOnlyThatRepCount() {
        val day = listOf(set("2026-09-01", 100.0, 5), set("2026-09-01", 110.0, 3), set("2026-09-01", 95.0, 5))
        assertEquals(100.0, Records.maxWeightForReps(day, 5), 1e-9)
        assertEquals(110.0, Records.maxWeightForReps(day, 3), 1e-9)
        assertEquals(0.0, Records.maxWeightForReps(day, 8), 1e-9)
    }

    @Test
    fun recordProgressFollowsThePrMarksAndNeverFalls() {
        val f = Records.Formula.EPLEY
        val byDate = sortedMapOf(
            "2026-09-01" to listOf(set("2026-09-01", 100.0, 1, pr = true)),
            "2026-09-03" to listOf(set("2026-09-03", 120.0, 1)), // heavier but not marked: no point
            "2026-09-05" to listOf(set("2026-09-05", 80.0, 10, pr = true)), // a 10-rep record, lower estimate
            "2026-09-08" to listOf(set("2026-09-08", 105.0, 1, pr = true))
        )
        val points = Records.recordProgress(byDate, f)
        assertEquals(listOf("2026-09-01", "2026-09-05", "2026-09-08"), points.map { it.first })
        val e1rm10 = Records.oneRepMax(80.0, 10, f)
        assertEquals(100.0, points[0].second, 1e-9)
        assertEquals(maxOf(100.0, e1rm10), points[1].second, 1e-9)
        assertEquals(maxOf(105.0, e1rm10), points[2].second, 1e-9)
        assertEquals(points.map { it.second }, points.map { it.second }.sorted())
    }
}
