package com.fitlens.companion.data

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Who owns a workout row: imported from a FitNotes backup, or created (or edited) in FitLens. See [Workouts]. */
object Sources {
    const val FITNOTES = "fitnotes"
    const val FITLENS = "fitlens"
}

data class Category(val id: Long, val name: String, val colour: Int, val sortOrder: Int, val source: String = Sources.FITLENS) {
    val imported: Boolean get() = source == Sources.FITNOTES
}

/**
 * The four FitNotes exercise types, kept so imported exercises behave the same in FitLens. They decide which
 * fields the set entry screen shows. Per-exercise units, increments and the fuller type handling are #14 and #15.
 */
object ExerciseTypes {
    const val WEIGHT_REPS = 0
    const val DISTANCE_TIME = 1
    const val WEIGHT_DISTANCE = 2
    const val TIME = 3

    val all = listOf(WEIGHT_REPS, DISTANCE_TIME, WEIGHT_DISTANCE, TIME)

    fun label(type: Int): String = when (type) {
        DISTANCE_TIME -> "Distance & time"
        WEIGHT_DISTANCE -> "Weight & distance"
        TIME -> "Time"
        else -> "Weight & reps"
    }

    fun usesWeight(type: Int): Boolean = type == WEIGHT_REPS || type == WEIGHT_DISTANCE
    fun usesReps(type: Int): Boolean = type == WEIGHT_REPS
    fun usesDistance(type: Int): Boolean = type == DISTANCE_TIME || type == WEIGHT_DISTANCE
    fun usesDuration(type: Int): Boolean = type == DISTANCE_TIME || type == TIME
}

/** FitNotes exercise types: see [ExerciseTypes]. */
data class Exercise(
    val id: Long,
    val name: String,
    val categoryId: Long,
    val type: Int,
    val notes: String?,
    val source: String = Sources.FITLENS,
    /** Starred in the exercise library, so it comes first in the pickers. */
    val favourite: Boolean = false,
    /** This exercise's + and − step in kg, or null for the global step (#15). */
    val weightStepKg: Double? = null,
    /** The graph the exercise opens on, as an index into its graph list, or -1 for the first (#15). */
    val defaultGraph: Int = -1
) {
    val imported: Boolean get() = source == Sources.FITNOTES
}

data class SetRow(
    val id: Long,
    val exerciseId: Long,
    val date: String,
    val weightKg: Double,
    val reps: Int,
    val distance: Double,
    val durationSec: Int,
    val isPr: Boolean,
    val comment: String?,
    val source: String = Sources.FITLENS,
    /** Working, warm-up, drop or failure ([SetTypes], #43). */
    val setType: Int = SetTypes.WORKING,
    /** Effort as RPE (1–10, half steps), or null when not recorded (#44). RIR is shown as 10 − RPE. */
    val rpe: Double? = null,
    /** The set's place in its day (#70): sets and exercises are shown in this order. */
    val position: Long = 0L,
    /** The superset (#18) its exercise belongs to on that day; 0 when it isn't in one. */
    val superset: Int = 0
) {
    val imported: Boolean get() = source == Sources.FITNOTES
    val isWarmup: Boolean get() = setType == SetTypes.WARMUP
}

/**
 * What kind of set a set is (#43). Stored as `workout_set.set_type`; imports and older rows are [WORKING]. Each type
 * has a one-letter badge, so the meaning never rests on colour alone.
 */
object SetTypes {
    const val WORKING = 0
    const val WARMUP = 1
    const val DROP = 2
    const val FAILURE = 3

    val all = listOf(WORKING, WARMUP, DROP, FAILURE)

    fun label(t: Int): String = when (t) {
        WARMUP -> "Warm-up"
        DROP -> "Drop set"
        FAILURE -> "To failure"
        else -> "Working"
    }

    /** The badge letter, or null for a working set (which needs none). */
    fun badge(t: Int): String? = when (t) {
        WARMUP -> "W"
        DROP -> "D"
        FAILURE -> "F"
        else -> null
    }

    /** The CSV value (#31). */
    fun csv(t: Int): String = when (t) {
        WARMUP -> "warmup"
        DROP -> "drop"
        FAILURE -> "failure"
        else -> "working"
    }
}

/** Effort per set (#44): stored as RPE, entered and shown as RPE or RIR. */
object Effort {
    const val OFF = "off"
    const val RPE = "rpe"
    const val RIR = "rir"

    /** The RPE choices, in half steps. */
    val rpeSteps = listOf(6.0, 6.5, 7.0, 7.5, 8.0, 8.5, 9.0, 9.5, 10.0)

    /** The RIR choices; "5+" is stored as RPE 5. */
    val rirSteps = listOf(0, 1, 2, 3, 4, 5)

    fun rpeFromRir(rir: Int): Double = (10 - rir.coerceIn(0, 5)).toDouble()

    fun rirFromRpe(rpe: Double): Int = (10.0 - rpe).toInt().coerceIn(0, 5)

    /** Short text for a set list: "RPE 8.5" or "2 RIR". */
    fun short(rpe: Double, mode: String): String =
        if (mode == RIR) { val r = rirFromRpe(rpe); if (r >= 5) "5+ RIR" else "$r RIR" } else "RPE ${fmtNum(rpe, 1)}"

    /** What TalkBack reads: "RPE 8" or "2 reps in reserve". */
    fun spoken(rpe: Double, mode: String): String =
        if (mode == RIR) {
            val r = rirFromRpe(rpe)
            if (r >= 5) "5 or more reps in reserve" else if (r == 1) "1 rep in reserve" else "$r reps in reserve"
        } else "RPE ${fmtNum(rpe, 1)}"
}

data class MeasurementDef(
    val name: String,
    val unit: String,
    val sortOrder: Int,
    val goalType: Int,
    val goalValue: Double,
    val enabled: Boolean,
    /** Created in FitLens rather than imported from FitNotes. */
    val custom: Boolean = false,
    /** For custom metrics: the FitNotes measurement whose values fill it in (null = match by name). */
    val link: String? = null
) {
    /** Lower-case FitNotes name this custom metric takes its values from. */
    val matchKey: String get() = (link?.takeIf { it.isNotBlank() } ?: name).trim().lowercase()
}

/**
 * A measurement's goal direction (#27), stored in `measurement.goal_type` as FitNotes does: none, increase, decrease
 * or a specific value (`goal_value`). Increase and decrease may also carry a target value.
 */
object MeasurementGoals {
    const val NONE = 0
    const val INCREASE = 1
    const val DECREASE = 2
    const val TARGET = 3

    val all = listOf(NONE, INCREASE, DECREASE, TARGET)

    fun label(t: Int): String = when (t) {
        INCREASE -> "Increase"
        DECREASE -> "Decrease"
        TARGET -> "Specific value"
        else -> "No goal"
    }

    /** Whether going from [from] to [to] moves towards the goal, or null when there's no goal or no change. */
    fun isImprovement(type: Int, target: Double, from: Double, to: Double): Boolean? {
        if (to == from) return null
        return when (type) {
            INCREASE -> to > from
            DECREASE -> to < from
            TARGET -> if (target <= 0) null else kotlin.math.abs(to - target) < kotlin.math.abs(from - target)
            else -> null
        }
    }
}

data class MRecord(
    val id: Long,
    val name: String,
    val unit: String,
    val date: String,
    val time: String,
    val value: Double,
    val comment: String?,
    val source: String
)

object Poses {
    const val NONE = ""
    val all = listOf("Front", "Side", "Back", "Other")
}

object DateSources {
    const val EXIF = "exif"          // camera metadata (most reliable)
    const val MEDIA = "media"        // Android media library "date taken"
    const val FILENAME = "filename"  // parsed from e.g. IMG_20230826_132000.jpg
    const val FILE = "file"          // file modified time (least reliable)
    const val MANUAL = "manual"      // set by the user
    const val NONE = "none"

    fun label(s: String): String = when (s) {
        EXIF -> "Photo metadata (EXIF)"
        MEDIA -> "Media library date taken"
        FILENAME -> "Date in file name"
        FILE -> "File modified date (check this)"
        MANUAL -> "Set manually"
        else -> "No date found"
    }

    /** Sources worth a second look by the user. */
    fun needsReview(s: String) = s == FILE || s == NONE
}

data class Photo(
    val id: Long,
    val file: String,
    val date: String?,
    val takenAt: String?,
    val dateSource: String,
    val pose: String,
    val note: String?,
    val originalName: String?
)

data class WorkoutTime(val date: String, val start: String, val end: String)

object Dates {
    val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val long = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())
    private val medium = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
    private val short = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    private val monthYearShort = DateTimeFormatter.ofPattern("MMM yy", Locale.getDefault())

    fun parse(s: String?): LocalDate? = try {
        if (s == null || s.length < 10) null else LocalDate.parse(s.substring(0, 10), ISO)
    } catch (e: Exception) {
        null
    }

    fun long(s: String): String = parse(s)?.format(long) ?: s
    fun medium(s: String): String = parse(s)?.format(medium) ?: s
    fun short(s: String): String = parse(s)?.format(short) ?: s
    fun monthYear(d: LocalDate): String = d.format(monthYear)
    fun monthYearShort(d: LocalDate): String = d.format(monthYearShort)
    fun epochDay(s: String): Long = parse(s)?.toEpochDay() ?: 0L
    fun today(): String = LocalDate.now().format(ISO)

    /**
     * Parses a workout start/finish stamp. FitNotes writes `yyyy-MM-dd HH:mm:ss` — a space separator and no zone —
     * which `OffsetDateTime.parse` rejects and `LocalDateTime.parse` only accepts with a `T`. Every caller wrapped
     * the failure in a catch that returned zero, so workout durations silently never appeared (#72).
     * Accepts either separator, and ignores a trailing offset if one is ever added.
     */
    fun dateTime(s: String?): LocalDateTime? {
        if (s.isNullOrBlank()) return null
        val t = s.trim().substringBefore('+').substringBefore('Z').trim().replace(' ', 'T')
        return try {
            LocalDateTime.parse(t)
        } catch (e: Exception) {
            null
        }
    }

    /** Seconds between two workout stamps, or 0 when either is missing or unparseable. */
    fun secondsBetween(start: String?, end: String?): Long {
        val s = dateTime(start) ?: return 0L
        val e = dateTime(end) ?: return 0L
        return maxOf(0L, Duration.between(s, e).seconds)
    }
}

fun fmtNum(v: Double, maxDecimals: Int = 2): String {
    val s = String.format(Locale.US, "%.${maxDecimals}f", v)
    return if (s.contains('.')) s.trimEnd('0').trimEnd('.') else s
}

fun fmtSigned(v: Double, maxDecimals: Int = 2): String =
    (if (v > 0) "+" else if (v < 0) "−" else "±") + fmtNum(kotlin.math.abs(v), maxDecimals)

fun fmtDuration(sec: Int): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return when {
        h > 0 -> String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else -> String.format(Locale.US, "%d:%02d", m, s)
    }
}
