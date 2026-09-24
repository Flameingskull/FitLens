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
    val favourite: Boolean = false
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
    val source: String = Sources.FITLENS
) {
    val imported: Boolean get() = source == Sources.FITNOTES
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
