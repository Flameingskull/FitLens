package com.fitlens.companion.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/** Trend lines (#152): the fit, its quality and its words must match the data exactly. Robolectric gives [trendText] its strings (#94). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TrendTest {

    private fun pts(vararg xy: Pair<Long, Double>) = xy.map { (x, y) -> ChartPoint(x, y, "") }

    @Test
    fun exactLineIsRecoveredExactly() {
        // y = 100 + 0.5 per day, from epoch day 20000, unevenly spaced.
        val days = listOf(20000L, 20003, 20010, 20011, 20030, 20064)
        val t = trendOf(days.map { ChartPoint(it, 100 + 0.5 * (it - 20000), "") })!!
        assertEquals(0.5, t.slope, 1e-9)
        assertEquals(1.0, t.r2, 1e-9)
        assertEquals(100.0, t.at(20000), 1e-9)
        assertEquals(132.0, t.at(20064), 1e-9)
        assertEquals(0.5 * 30.44, t.perMonth, 1e-9)
    }

    @Test
    fun matchesTextbookLeastSquares() {
        // x 0..4, y 2,4,5,4,5: slope 0.6, intercept 2.8 (4 − 0.6 × 2), R² 0.6.
        val t = trendOf(pts(0L to 2.0, 1L to 4.0, 2L to 5.0, 3L to 4.0, 4L to 5.0))!!
        assertEquals(0.6, t.slope, 1e-9)
        assertEquals(2.8, t.at(0), 1e-9)
        assertEquals(0.6, t.r2, 1e-9)
    }

    @Test
    fun orderDoesNotMatter() {
        val a = trendOf(pts(0L to 1.0, 5L to 3.0, 9L to 2.0, 12L to 6.0))!!
        val b = trendOf(pts(12L to 6.0, 0L to 1.0, 9L to 2.0, 5L to 3.0))!!
        assertEquals(a.slope, b.slope, 1e-12)
        assertEquals(0L, b.fromX)
        assertEquals(12L, b.toX)
    }

    @Test
    fun tooFewPointsOrOneDayGiveNoTrend() {
        assertNull(trendOf(pts(0L to 1.0, 7L to 2.0)))
        assertNull(trendOf(pts(5L to 1.0, 5L to 2.0, 5L to 3.0)))
    }

    @Test
    fun flatValuesAreAPerfectFlatFit() {
        val t = trendOf(pts(0L to 80.0, 3L to 80.0, 9L to 80.0))!!
        assertEquals(0.0, t.slope, 1e-12)
        assertEquals(1.0, t.r2, 1e-12)
    }

    @Test
    fun curveFollowsABendTheStraightLineMisses() {
        // Rises 1 a day for 20 days, then flat for 20: a plateau.
        val data = (0L..40L).map { ChartPoint(it, if (it <= 20) it.toDouble() else 20.0, "") }
        val t = trendOf(data)!!
        assertEquals(41, t.curve.size)
        val curveErr = data.zip(t.curve).maxOf { (p, c) -> abs(p.y - c.second) }
        val lineErr = data.maxOf { abs(it.y - t.at(it.x)) }
        assertTrue("curve $curveErr vs line $lineErr", curveErr < lineErr / 2)
        // On a straight stretch the local fit is exact.
        assertEquals(5.0, t.curve[5].second, 1e-9)
        assertEquals(20.0, t.curve[35].second, 1e-9)
    }

    @Test
    fun curveOnAStraightLineIsThatLine() {
        val data = (0L..30L step 3).map { ChartPoint(it, 50 - 0.25 * it, "") }
        val t = trendOf(data)!!
        t.curve.forEach { (x, y) -> assertEquals(t.at(x), y, 1e-9) }
    }

    @Test
    fun fewPointsDrawTheStraightLine() {
        val t = trendOf(pts(0L to 1.0, 4L to 2.0, 8L to 4.0, 12L to 3.0))!!
        assertTrue(t.curve.isEmpty())
        assertEquals(listOf(0L, 12L), t.drawn().map { it.first })
        assertTrue(t.fewPoints)
    }

    @Test
    fun textGivesRateEndsAndFit() {
        val t = trendOf((0L..60L step 6).map { ChartPoint(20000 + it, 80 + it / 7.0, "") })!!
        val res = ApplicationProvider.getApplicationContext<Application>().resources
        val s = trendText(res, t, { com.fitlens.companion.data.fmtNum(it, 1) }, "kg")
        assertTrue(s, s.startsWith("+1 kg per week: 80 → 88.6 kg from "))
        assertTrue(s, s.contains("straight-line fit to 11 points, R² 1"))
    }
}
