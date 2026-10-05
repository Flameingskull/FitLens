package com.fitlens.companion.ui

import android.content.res.Resources
import com.fitlens.companion.R
import com.fitlens.companion.data.Analysis
import com.fitlens.companion.data.GoalKinds
import com.fitlens.companion.data.Records
import kotlin.math.roundToInt

/*
 * What the Analysis enums and goal kinds read as on screen (#94). The enums keep their names, which are stored in
 * pins and settings; their words come from strings.xml here.
 */

/** "Workouts", "Volume", "Duration". */
internal fun Analysis.Metric.text(res: Resources): String = res.getString(
    when (this) {
        Analysis.Metric.Workouts -> R.string.an_metric_workouts
        Analysis.Metric.Volume -> R.string.an_metric_volume
        Analysis.Metric.Sets -> R.string.an_metric_sets
        Analysis.Metric.Reps -> R.string.an_metric_reps
        Analysis.Metric.Duration -> R.string.an_metric_duration
    }
)

/** The metric as a word after a number or in a sentence: "workouts", "volume". */
internal fun Analysis.Metric.word(res: Resources): String = res.getString(
    when (this) {
        Analysis.Metric.Workouts -> R.string.an_word_workouts
        Analysis.Metric.Volume -> R.string.an_word_volume
        Analysis.Metric.Sets -> R.string.an_word_sets
        Analysis.Metric.Reps -> R.string.an_word_reps
        Analysis.Metric.Duration -> R.string.an_word_duration
    }
)

/** "week", "month", "year", for "this week" and "per month". */
internal fun Analysis.Period.word(res: Resources): String = res.getString(
    when (this) {
        Analysis.Period.Week -> R.string.an_period_week
        Analysis.Period.Month -> R.string.an_period_month
        Analysis.Period.Year -> R.string.an_period_year
    }
)

/** "Sets", "Reps", "Workouts", "Volume". */
internal fun Analysis.Measure.text(res: Resources): String = res.getString(
    when (this) {
        Analysis.Measure.Sets -> R.string.an_metric_sets
        Analysis.Measure.Reps -> R.string.an_metric_reps
        Analysis.Measure.Workouts -> R.string.an_metric_workouts
        Analysis.Measure.Volume -> R.string.an_metric_volume
    }
)

/** A count of [this] measure with its plural ("1 set", "12 reps"); volume has no count word, so only the number. */
internal fun Analysis.Measure.count(res: Resources, number: String, v: Double): String {
    val id = when (this) {
        Analysis.Measure.Sets -> R.plurals.an_count_sets
        Analysis.Measure.Reps -> R.plurals.an_count_reps
        Analysis.Measure.Workouts -> R.plurals.an_count_workouts
        Analysis.Measure.Volume -> return number
    }
    return res.getQuantityString(id, v.roundToInt(), number)
}

internal fun Analysis.GroupBy.text(res: Resources): String = res.getString(
    when (this) {
        Analysis.GroupBy.Category -> R.string.an_group_category
        Analysis.GroupBy.Exercise -> R.string.an_group_exercise
    }
)

internal fun Analysis.Span.text(res: Resources): String = res.getString(
    when (this) {
        Analysis.Span.Workout -> R.string.an_span_workout
        Analysis.Span.Week -> R.string.an_span_week
        Analysis.Span.Month -> R.string.an_span_month
        Analysis.Span.Year -> R.string.an_span_year
        Analysis.Span.All -> R.string.an_span_all
        Analysis.Span.Custom -> R.string.an_span_custom
    }
)

/** "the workout before", "the month before", for the Breakdown comparison. */
internal fun Analysis.Span.before(res: Resources): String = res.getString(
    when (this) {
        Analysis.Span.Workout -> R.string.an_before_workout
        Analysis.Span.Month -> R.string.an_before_month
        Analysis.Span.Year -> R.string.an_before_year
        else -> R.string.an_before_week
    }
)

/** A Breakdown window's name: the data layer's date label, except All, which reads "All time". */
internal fun Analysis.DateWindow.text(res: Resources, span: Analysis.Span): String =
    if (span == Analysis.Span.All) res.getString(R.string.an_all_time) else label

/** A breakdown slice's name: the exercise or category, or "Unknown exercise", "No category" or "Other (3)" (#156). */
internal fun Analysis.Slice.text(res: Resources, by: Analysis.GroupBy): String = when {
    others > 0 -> res.getString(R.string.slice_other, others)
    label.isNotEmpty() -> label
    by == Analysis.GroupBy.Exercise -> res.getString(R.string.slice_unknown_exercise)
    else -> res.getString(R.string.slice_no_category)
}

/** A goal kind's name: "Max weight", "Volume in one workout" (#25). */

internal fun goalKindText(res: Resources, kind: Int): String = res.getString(
    when (kind) {
        GoalKinds.MAX_WEIGHT -> R.string.goal_max_weight
        GoalKinds.E1RM -> R.string.goal_e1rm
        GoalKinds.MAX_REPS -> R.string.goal_max_reps
        GoalKinds.SET_VOLUME -> R.string.goal_set_volume
        GoalKinds.WORKOUT_VOLUME -> R.string.goal_workout_volume
        GoalKinds.LONGEST_SET -> R.string.goal_longest_set
        GoalKinds.WORKOUT_DISTANCE -> R.string.goal_workout_distance
        else -> R.string.goal_generic
    }
)

/** "Line", "Bar", "Area", "Step" (#137). */
internal fun ChartKind.text(res: Resources): String = res.getString(
    when (this) {
        ChartKind.LINE -> R.string.ck_line
        ChartKind.BAR -> R.string.ck_bar
        ChartKind.AREA -> R.string.ck_area
        ChartKind.STEP -> R.string.ck_step
    }
)

/** A Records tab period: "Workout", "Week", "All". */
internal fun Records.Period.text(res: Resources): String = res.getString(
    when (this) {
        Records.Period.WORKOUT -> R.string.an_span_workout
        Records.Period.WEEK -> R.string.an_span_week
        Records.Period.MONTH -> R.string.an_span_month
        Records.Period.YEAR -> R.string.an_span_year
        Records.Period.ALL -> R.string.an_span_all
    }
)

/** A range preset's name in a menu (the keys in [RANGES]): "1M" reads as "1 month". */
internal fun rangeName(res: Resources, short: String): String = when (short) {
    "1M" -> res.getString(R.string.range_1m)
    "3M" -> res.getString(R.string.range_3m)
    "6M" -> res.getString(R.string.range_6m)
    "1Y" -> res.getString(R.string.range_1y)
    "All" -> res.getString(R.string.an_all_time)
    else -> short
}

/**
 * An exercise graph's name as shown (#94). The `GRAPH_*` names are stored keys (default graphs, pins, chart kinds),
 * so they stay as they are and only their display comes from strings.xml. A custom metric's graphs keep their own name.
 */
internal fun graphName(res: Resources, key: String): String = when (key) {
    GRAPH_E1RM -> res.getString(R.string.gr_e1rm)
    GRAPH_MAX_WEIGHT -> res.getString(R.string.gr_max_weight)
    GRAPH_WORKOUT_VOLUME -> res.getString(R.string.gr_workout_volume)
    GRAPH_WORKOUT_REPS -> res.getString(R.string.gr_workout_reps)
    GRAPH_MAX_REPS -> res.getString(R.string.gr_max_reps)
    GRAPH_MAX_VOLUME -> res.getString(R.string.gr_max_volume)
    GRAPH_WEIGHT_FOR_REPS -> res.getString(R.string.gr_weight_for_reps)
    GRAPH_RECORDS -> res.getString(R.string.gr_records)
    GRAPH_RELATIVE_STRENGTH -> res.getString(R.string.gr_relative_strength)
    GRAPH_LONGEST -> res.getString(R.string.gr_max_time)
    GRAPH_TOTAL_TIME -> res.getString(R.string.gr_total_time)
    GRAPH_DISTANCE -> res.getString(R.string.gr_distance)
    GRAPH_MAX_DISTANCE -> res.getString(R.string.gr_max_distance)
    GRAPH_MAX_SPEED -> res.getString(R.string.gr_max_speed)
    GRAPH_MAX_PACE -> res.getString(R.string.gr_max_pace)
    else -> key
}
