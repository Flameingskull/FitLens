package com.fitlens.companion.ui

import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.data.fmtSigned
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import android.content.res.Resources
import com.fitlens.companion.R

// Trend lines (#152). Every trend in FitLens comes from here, so the line drawn, the figures written under it and a
// shared image always agree. Plain Kotlin with no Android parts, so it's unit tested on the JVM (TrendTest).

/**
 * The trend through a series of points, with x in epoch days.
 *
 * - The straight line is the least-squares fit, y = [at] (x). [slope] is per day, [r2] how much of the variation it
 *   explains (0..1), and [n] the number of points it was fitted to, from day [fromX] to [toX].
 * - [curve] follows the data where a straight line can't (a plateau, a cut, a bulk): a locally weighted regression
 *   (LOESS) at each fitted day. It's empty when there are too few points to smooth, and the straight line is drawn.
 */
data class TrendLine(
    val slope: Double,
    val meanX: Double,
    val meanY: Double,
    val r2: Double,
    val n: Int,
    val fromX: Long,
    val toX: Long,
    val curve: List<Pair<Long, Double>> = emptyList()
) {
    /** The straight line's value on day [x]. */
    fun at(x: Long): Double = meanY + slope * (x - meanX)

    /** The change over an average month (30.44 days). */
    val perMonth: Double get() = slope * DAYS_PER_MONTH

    val spanDays: Long get() = toX - fromX

    /** Too few points to rely on: the figures say so. */
    val fewPoints: Boolean get() = n < RELIABLE_POINTS

    /** The straight line explains less than a third of the variation: the values vary more than they trend. */
    val looseFit: Boolean get() = r2 < LOOSE_R2

    /** What to draw, in day order: the smoothed curve, or the straight line's two ends. */
    fun drawn(): List<Pair<Long, Double>> = curve.ifEmpty { listOf(fromX to at(fromX), toX to at(toX)) }

    companion object {
        const val DAYS_PER_MONTH = 30.44
        const val DAYS_PER_YEAR = 365.25
        /** No trend at all below this: two points always make a "perfect" line, which says nothing. */
        const val MIN_POINTS = 3
        const val RELIABLE_POINTS = 5
        /** The curve needs this many points, or it's just the straight line with extra wiggle. */
        const val SMOOTH_POINTS = 8
        const val LOOSE_R2 = 0.3
        /** Each point of the curve is fitted to the nearest 40% of the points (at least 6). */
        const val SMOOTH_SPAN = 0.4
    }
}

/**
 * The trend through [points] (in any order), or null when there are fewer than [TrendLine.MIN_POINTS] points or they
 * all fall on one day.
 */
fun trendOf(points: List<ChartPoint>): TrendLine? {
    if (points.size < TrendLine.MIN_POINTS) return null
    val pts = points.sortedBy { it.x }
    val n = pts.size
    val mx = pts.sumOf { it.x.toDouble() } / n
    val my = pts.sumOf { it.y } / n
    var sxx = 0.0
    var sxy = 0.0
    var syy = 0.0
    pts.forEach { p ->
        val dx = p.x - mx
        val dy = p.y - my
        sxx += dx * dx
        sxy += dx * dy
        syy += dy * dy
    }
    if (sxx == 0.0) return null
    val slope = sxy / sxx
    // R² = 1 − residual / total. Values that never change are fitted exactly by a flat line.
    val r2 = if (syy == 0.0) 1.0 else ((sxy * sxy) / (sxx * syy)).coerceIn(0.0, 1.0)
    val curve = if (n >= TrendLine.SMOOTH_POINTS) loess(pts) else emptyList()
    return TrendLine(slope, mx, my, r2, n, pts.first().x, pts.last().x, curve)
}

/**
 * A locally weighted linear regression (LOESS, tricube weights) evaluated on every distinct day of [pts], which are
 * sorted by day. Each day's value is a straight-line fit to its nearest points, weighted by how close they are, so
 * the curve stays close to the data without following every bump.
 */
internal fun loess(pts: List<ChartPoint>): List<Pair<Long, Double>> {
    val n = pts.size
    val q = max(6, ceil(n * TrendLine.SMOOTH_SPAN).toInt()).coerceAtMost(n)
    val xs = DoubleArray(n) { pts[it].x.toDouble() }
    val out = ArrayList<Pair<Long, Double>>()
    var lo = 0
    var i = 0
    while (i < n) {
        val day = pts[i].x
        val x0 = day.toDouble()
        // The q nearest points in time are a run of q neighbours: slide the run while that brings it closer.
        while (lo + q < n && x0 - xs[lo] > xs[lo + q] - x0) lo++
        val hi = lo + q - 1
        // The window's radius, a little wider so its furthest point still counts for something.
        val h = max(x0 - xs[lo], xs[hi] - x0).coerceAtLeast(0.5) * 1.001
        var sw = 0.0
        var swx = 0.0
        var swy = 0.0
        for (k in lo..hi) {
            val w = (1 - (abs(xs[k] - x0) / h).pow(3)).coerceAtLeast(0.0).pow(3)
            sw += w; swx += w * xs[k]; swy += w * pts[k].y
        }
        val wx = swx / sw
        val wy = swy / sw
        var wxx = 0.0
        var wxy = 0.0
        for (k in lo..hi) {
            val w = (1 - (abs(xs[k] - x0) / h).pow(3)).coerceAtLeast(0.0).pow(3)
            wxx += w * (xs[k] - wx) * (xs[k] - wx)
            wxy += w * (xs[k] - wx) * (pts[k].y - wy)
        }
        val y = if (wxx < 1e-12) wy else wy + wxy / wxx * (x0 - wx)
        out += day to y
        while (i < n && pts[i].x == day) i++
    }
    return out
}

/**
 * The trend in words, with full figures (owner, 2026-10-02): the rate in the graph's own unit per week, month or year
 * (whichever suits the span), the fitted values at either end and their dates, and how much to trust it.
 * [format] writes a value; [unit] follows it (blank for plain counts).
 */
fun trendText(res: Resources, t: TrendLine, format: (Double) -> String, unit: String): String {
    val (per, days) = when {
        t.spanDays <= 120 -> res.getString(R.string.tr_week) to 7.0
        t.spanDays <= 3 * 365 -> res.getString(R.string.tr_month) to TrendLine.DAYS_PER_MONTH
        else -> res.getString(R.string.tr_year) to TrendLine.DAYS_PER_YEAR
    }
    val rate = t.slope * days
    val decimals = when {
        abs(rate) >= 100 -> 0
        abs(rate) >= 10 -> 1
        else -> 2
    }
    val u = if (unit.isBlank()) "" else " $unit"
    fun day(x: Long) = Dates.medium(LocalDate.ofEpochDay(x).format(Dates.ISO))
    val head = res.getString(
        R.string.tr_head, fmtSigned(rate, decimals) + u, per, format(t.at(t.fromX)), format(t.at(t.toX)) + u, day(t.fromX), day(t.toX)
    )
    val fit = res.getString(R.string.tr_fit, t.n, fmtNum((t.r2 * 100).roundToInt() / 100.0, 2))
    val caution = when {
        t.fewPoints -> res.getString(R.string.tr_few, t.n)
        t.looseFit -> res.getString(R.string.tr_loose)
        else -> ""
    }
    return res.getString(R.string.tr_whole, head, fit, caution)
}
