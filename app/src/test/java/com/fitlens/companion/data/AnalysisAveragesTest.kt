package com.fitlens.companion.data

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** A measurement's average per period, the overlay on Analysis → Workouts totals (#56). Plain Kotlin, no settings read. */
class AnalysisAveragesTest {

    private fun r(date: String, value: Double) = MRecord(0, "Bodyweight", "kg", date, "", value, null, Sources.FLEXNOTES)

    @Test
    fun averagesEachWeekAndLeavesEmptyWeeksOut() {
        Analysis.weekStart = DayOfWeek.MONDAY
        val values = listOf(
            r("2026-09-28", 80.0), r("2026-09-30", 81.0), // week of Monday 28 September
            r("2026-10-14", 79.0) // week of Monday 12 October; the week of 5 October has none
        )
        val avgs = Analysis.periodAverages(values, Analysis.Period.Week, null)
        assertEquals(listOf(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 12)), avgs.map { it.first })
        assertEquals(80.5, avgs[0].second, 1e-9)
        assertEquals(79.0, avgs[1].second, 1e-9)
    }

    @Test
    fun leavesOutValuesBeforeTheRange() {
        val values = listOf(r("2026-08-31", 90.0), r("2026-09-02", 80.0))
        val avgs = Analysis.periodAverages(values, Analysis.Period.Month, "2026-09-01")
        assertEquals(1, avgs.size)
        assertEquals(LocalDate.of(2026, 9, 1), avgs[0].first)
        assertEquals(80.0, avgs[0].second, 1e-9)
    }
}
