package com.fitlens.companion.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * The numbers behind the Analysis hub (#58, #90): period totals (#51) and breakdowns (#52). Plain Kotlin with no
 * Android imports, working only from a [Snapshot], so the screens stay thin and the maths can be unit-tested (#40).
 *
 * Volume is weight × reps in kilograms; screens convert to the display unit. Only sets with both a weight and reps
 * count towards volume, and only sets with reps count towards reps, so time and distance exercises never inflate them.
 */
object Analysis {

    enum class Period(val label: String) { Week("Weekly"), Month("Monthly"), Year("Yearly") }

    enum class Metric(val label: String) { Workouts("Workouts"), Volume("Volume"), Sets("Sets"), Reps("Reps"), Duration("Duration") }

    /** Which sets count: everything, one category or one exercise. */
    data class Filter(val categoryId: Long? = null, val exerciseId: Long? = null) {
        fun matches(snap: Snapshot, s: SetRow): Boolean = when {
            exerciseId != null -> s.exerciseId == exerciseId
            categoryId != null -> snap.exercises[s.exerciseId]?.categoryId == categoryId
            else -> true
        }
    }

    /** One period's total. [days] are the workout dates inside it, oldest first; [current] marks today's period. */
    data class PeriodTotal(
        val start: LocalDate,
        val end: LocalDate,
        val value: Double,
        val days: List<String>,
        val current: Boolean,
        /** For duration: how many of [days] had a recorded start and finish. */
        val timed: Int = 0
    )

    /** The first day of the week (#7), set from the week-start setting each time the data snapshot loads. */
    @Volatile var weekStart: DayOfWeek = DayOfWeek.MONDAY

    /** "Monday", "Saturday" or "Sunday", in the phone's language. */
    fun weekStartName(): String = weekStart.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())

    fun periodStart(d: LocalDate, p: Period): LocalDate = when (p) {
        Period.Week -> d.with(TemporalAdjusters.previousOrSame(weekStart))
        Period.Month -> d.withDayOfMonth(1)
        Period.Year -> d.withDayOfYear(1)
    }

    fun periodEnd(start: LocalDate, p: Period): LocalDate = when (p) {
        Period.Week -> start.plusDays(6)
        Period.Month -> start.plusMonths(1).minusDays(1)
        Period.Year -> start.plusYears(1).minusDays(1)
    }

    private fun next(start: LocalDate, p: Period): LocalDate = when (p) {
        Period.Week -> start.plusWeeks(1)
        Period.Month -> start.plusMonths(1)
        Period.Year -> start.plusYears(1)
    }

    private val weekLabel = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    private val monthLabel = DateTimeFormatter.ofPattern("MMM yy", Locale.getDefault())
    private val rangeDay = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

    /** A bar's short axis label. */
    fun shortLabel(t: PeriodTotal, p: Period): String = when (p) {
        Period.Week -> t.start.format(weekLabel)
        Period.Month -> t.start.format(monthLabel)
        Period.Year -> t.start.year.toString()
    }

    /** A period in words, for the selection line: "Week of 3 Mar 2026", "March 2026", "2026". */
    fun longLabel(t: PeriodTotal, p: Period): String = when (p) {
        Period.Week -> "${t.start.format(rangeDay)} – ${t.end.format(rangeDay)}"
        Period.Month -> Dates.monthYear(t.start)
        Period.Year -> t.start.year.toString()
    }

    fun volumeKg(s: SetRow): Double = if (s.weightKg > 0 && s.reps > 0) s.weightKg * s.reps else 0.0

    /** Seconds of recorded workout time on [date], or 0 when it has no start and finish. */
    fun workoutSeconds(snap: Snapshot, date: String): Long =
        snap.workoutTimes[date].orEmpty().sumOf { Dates.secondsBetween(it.start, it.end) }

    /**
     * One total per period from the period holding [from] (or the first matching set when null) to today's period,
     * with empty periods included as zero so gaps in training show as gaps. Duration is in seconds, volume in kg.
     */
    fun totals(snap: Snapshot, metric: Metric, period: Period, filter: Filter, from: String?): List<PeriodTotal> {
        // Warm-ups count only when the setting says so (#43).
        val sets = snap.statSets.filter { filter.matches(snap, it) }
        if (sets.isEmpty()) return emptyList()
        val byDay = sets.groupBy { it.date.take(10) }
        val today = LocalDate.now()
        val firstDay = Dates.parse(from) ?: Dates.parse(byDay.keys.min()) ?: return emptyList()
        val currentStart = periodStart(today, period)
        var start = periodStart(firstDay, period)
        val out = ArrayList<PeriodTotal>()
        var guard = 0
        while (!start.isAfter(currentStart) && guard++ < 2000) {
            val end = periodEnd(start, period)
            val lo = start.format(Dates.ISO)
            val hi = end.format(Dates.ISO)
            val days = byDay.keys.filter { it in lo..hi }.sorted()
            val daySets = days.flatMap { byDay.getValue(it) }
            var timed = 0
            val value = when (metric) {
                Metric.Workouts -> days.size.toDouble()
                Metric.Sets -> daySets.size.toDouble()
                Metric.Reps -> daySets.sumOf { it.reps }.toDouble()
                Metric.Volume -> daySets.sumOf { volumeKg(it) }
                Metric.Duration -> days.sumOf { d ->
                    workoutSeconds(snap, d).also { if (it > 0) timed++ }
                }.toDouble()
            }
            out += PeriodTotal(start, end, value, days, start == currentStart, timed)
            start = next(start, period)
        }
        return out
    }

    // ---------- Breakdown (#52) ----------

    enum class Measure(val label: String, val unitOne: String, val unitMany: String) {
        Sets("Sets", "set", "sets"), Reps("Reps", "rep", "reps"), Workouts("Workouts", "workout", "workouts"), Volume("Volume", "", "")
    }

    enum class GroupBy(val label: String) { Category("Category"), Exercise("Exercise") }

    enum class Span(val label: String) { Workout("Workout"), Week("Week"), Month("Month"), Year("Year"), All("All"), Custom("Custom") }

    /** A breakdown slice. [id] is the category or exercise id, or null for "Other". */
    data class Slice(val id: Long?, val label: String, val value: Double)

    /** An inclusive range of ISO dates with a human name. */
    data class DateWindow(val from: String, val to: String, val label: String)

    /**
     * The windows of [span] that hold at least one set, newest first. Workout gives each workout day; All gives a
     * single window over everything. Custom has no list (the screen picks the dates).
     */
    fun windows(snap: Snapshot, span: Span): List<DateWindow> {
        val days = snap.setsByDate.keys.map { it.take(10) }.distinct().sortedDescending()
        if (days.isEmpty()) return emptyList()
        return when (span) {
            Span.Workout -> days.map { DateWindow(it, it, Dates.long(it)) }
            Span.All -> listOf(DateWindow(days.last(), days.first(), "All time"))
            Span.Custom -> emptyList()
            Span.Week, Span.Month, Span.Year -> {
                val p = when (span) { Span.Week -> Period.Week; Span.Month -> Period.Month; else -> Period.Year }
                days.mapNotNull { Dates.parse(it) }.map { periodStart(it, p) }.distinct().map { s ->
                    val e = periodEnd(s, p)
                    val t = PeriodTotal(s, e, 0.0, emptyList(), false)
                    DateWindow(s.format(Dates.ISO), e.format(Dates.ISO), longLabel(t, p))
                }
            }
        }
    }

    /** What [m] adds up to for [sets]. Workouts counts distinct days. */
    fun measure(sets: List<SetRow>, m: Measure): Double = when (m) {
        Measure.Sets -> sets.size.toDouble()
        Measure.Reps -> sets.sumOf { it.reps }.toDouble()
        Measure.Workouts -> sets.map { it.date.take(10) }.distinct().size.toDouble()
        Measure.Volume -> sets.sumOf { volumeKg(it) }
    }

    fun setsIn(snap: Snapshot, from: String, to: String): List<SetRow> =
        snap.statSets.filter { val d = it.date.take(10); d >= from && d <= to }

    /**
     * Slices of [m] by [by] for the sets from [from] to [to], largest first. Anything beyond [maxSlices] is grouped as
     * "Other", so the donut stays readable.
     */
    fun breakdown(snap: Snapshot, m: Measure, by: GroupBy, from: String, to: String, maxSlices: Int = 8): List<Slice> {
        val sets = setsIn(snap, from, to)
        val groups = sets.groupBy { s ->
            if (by == GroupBy.Exercise) s.exerciseId else snap.exercises[s.exerciseId]?.categoryId ?: 0L
        }
        val slices = groups.map { (id, list) ->
            val label = if (by == GroupBy.Exercise) snap.exercises[id]?.name ?: "Unknown exercise"
            else snap.categories[id]?.name ?: "No category"
            Slice(id, label, measure(list, m))
        }.filter { it.value > 0 }.sortedByDescending { it.value }
        if (slices.size <= maxSlices) return slices
        val kept = slices.take(maxSlices - 1)
        val rest = slices.drop(maxSlices - 1)
        return kept + Slice(null, "Other (${rest.size})", rest.sumOf { it.value })
    }

    /** The window just before [w] of the same length, for "vs last week" comparisons. */
    fun previousWindow(snap: Snapshot, span: Span, w: DateWindow): DateWindow? {
        val list = windows(snap, span)
        val i = list.indexOfFirst { it.from == w.from && it.to == w.to }
        if (span == Span.Workout) return list.getOrNull(i + 1)
        val p = when (span) { Span.Week -> Period.Week; Span.Month -> Period.Month; Span.Year -> Period.Year; else -> return null }
        val start = Dates.parse(w.from) ?: return null
        val prevStart = when (p) {
            Period.Week -> start.minusWeeks(1)
            Period.Month -> start.minusMonths(1)
            Period.Year -> start.minusYears(1)
        }
        val prevEnd = periodEnd(prevStart, p)
        return DateWindow(prevStart.format(Dates.ISO), prevEnd.format(Dates.ISO), "")
    }

    /**
     * Whole-number percentages that always add up to 100: each share is rounded down, then the leftover points go to
     * the largest remainders (#52).
     */
    fun percents(values: List<Double>): List<Int> {
        val total = values.sumOf { it.coerceAtLeast(0.0) }
        if (total <= 0) return values.map { 0 }
        val exact = values.map { it.coerceAtLeast(0.0) / total * 100.0 }
        val floors = exact.map { it.toInt() }.toMutableList()
        var left = 100 - floors.sum()
        exact.indices.sortedByDescending { exact[it] - floors[it] }.forEach { i ->
            if (left > 0) { floors[i]++; left-- }
        }
        return floors
    }
}
