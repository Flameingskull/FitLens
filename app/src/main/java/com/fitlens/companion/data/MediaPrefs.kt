package com.fitlens.companion.data

import java.net.URLDecoder
import java.net.URLEncoder
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Progress photo and media preferences (#46). They're kept as text in [PortableSettings], so they travel in backups,
 * and everything read back is validated: an unknown or out-of-range value (say, from a later build's backup) falls
 * back to its default instead of breaking the screen. Plain Kotlin, tested in `MediaPrefsTest`.
 */
object MediaPrefs {
    /** How the Photos grid is grouped: the stored keys, in the order the choices are shown. */
    const val GROUP_DAY = "day"
    const val GROUP_WEEK = "week"
    const val GROUP_MONTH = "month"
    const val GROUP_YEAR = "year"
    const val GROUP_POSE = "pose"
    val GROUPS = listOf(GROUP_DAY, GROUP_WEEK, GROUP_MONTH, GROUP_YEAR, GROUP_POSE)

    /** A stored grouping, or Month when it's missing or unknown. */
    fun groupOf(stored: String?): String = stored?.takeIf { it in GROUPS } ?: GROUP_MONTH

    /** Stored for "None" as the pose for new photos; no row at all means "Ask each time". */
    const val POSE_NONE = "none"

    /**
     * The pose new photos get without asking (#46): null to ask each time, [Poses.NONE] for no pose, or one of
     * [Poses.all]. [stored] is the `meta` value.
     */
    fun defaultPoseOf(stored: String?): String? =
        if (stored == POSE_NONE) Poses.NONE else stored?.takeIf { it in Poses.all }

    /** The `meta` value for [defaultPoseOf]'s result. */
    fun storeDefaultPose(pose: String?): String? =
        if (pose == Poses.NONE) POSE_NONE else pose?.takeIf { it in Poses.all }

    /** PDF photos per day (#46): 0 to 4, 2 when missing or out of range. */
    fun photosPerDayOf(stored: String?): Int = stored?.toIntOrNull()?.takeIf { it in 0..4 } ?: 2

    /** The first day of the section a photo taken on [date] falls in, by [group]. Pose grouping isn't by date. */
    fun sectionStart(date: LocalDate, group: String, weekStart: Int): LocalDate = when (group) {
        GROUP_DAY -> date
        GROUP_WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.of(weekStart.coerceIn(1, 7))))
        GROUP_YEAR -> date.withDayOfYear(1)
        else -> date.withDayOfMonth(1)
    }
}

/**
 * The slideshow and video screen's options (#46), remembered between visits when Settings → Progress photos and
 * media says so. The date range is never remembered: it always starts at all dates.
 *
 * [overlays] is null until the user has chosen; then the screen's defaults (bodyweight as a chart, body fat and
 * waist as values) give way to the saved list, even an empty one.
 */
data class SlideshowPrefs(
    /** "All", [UNSET] or one of [Poses.all]. */
    val pose: String = ALL,
    val onePerDay: Boolean = true,
    val seconds: Float = 1.0f,
    val fade: Boolean = true,
    val showDate: Boolean = true,
    val showDays: Boolean = true,
    val showPose: Boolean = true,
    val title: String = "",
    /** The video size as "WIDTHxHEIGHT"; the screen falls back to its first format for one it doesn't offer. */
    val format: String = "720x1280",
    /** Measurement name to "shown as a chart". */
    val overlays: List<Pair<String, Boolean>>? = null
) {
    fun encode(): String = buildList {
        add("pose=${enc(pose)}")
        add("onePerDay=${flag(onePerDay)}")
        add("seconds=$seconds")
        add("fade=${flag(fade)}")
        add("showDate=${flag(showDate)}")
        add("showDays=${flag(showDays)}")
        add("showPose=${flag(showPose)}")
        if (title.isNotEmpty()) add("title=${enc(title)}")
        add("format=$format")
        overlays?.let { list ->
            add("overlays=${list.size}")
            list.forEach { (name, chart) -> add("overlay=${flag(chart)}:${enc(name)}") }
        }
    }.joinToString("\n")

    companion object {
        const val ALL = "All"
        const val UNSET = "Unset"
        const val MAX_TITLE = 80
        const val MAX_OVERLAYS = 5
        val SECONDS = 0.3f..3f

        private fun flag(b: Boolean) = if (b) "1" else "0"
        private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
        private fun dec(s: String): String? = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrNull()

        /** Reads [encode]'s text. Unknown keys are ignored and bad values fall back to their defaults. */
        fun decode(text: String?): SlideshowPrefs {
            if (text.isNullOrBlank()) return SlideshowPrefs()
            val d = SlideshowPrefs()
            val values = HashMap<String, String>()
            val overlays = ArrayList<Pair<String, Boolean>>()
            var overlaysChosen = false
            text.lineSequence().forEach { line ->
                val eq = line.indexOf('=')
                if (eq <= 0) return@forEach
                val key = line.substring(0, eq)
                val value = line.substring(eq + 1)
                when (key) {
                    "overlays" -> overlaysChosen = true
                    "overlay" -> {
                        val colon = value.indexOf(':')
                        val name = if (colon == 1) dec(value.substring(2))?.trim() else null
                        if (name != null && name.isNotEmpty() && overlays.size < MAX_OVERLAYS &&
                            overlays.none { it.first == name }
                        ) overlays.add(name to (value[0] == '1'))
                    }
                    else -> values[key] = value
                }
            }
            fun bool(key: String, default: Boolean) = when (values[key]) { "1" -> true; "0" -> false; else -> default }
            val pose = values["pose"]?.let { dec(it) }?.takeIf { it == ALL || it == UNSET || it in Poses.all } ?: d.pose
            val format = values["format"]?.takeIf { Regex("""\d{2,5}x\d{2,5}""").matches(it) } ?: d.format
            return SlideshowPrefs(
                pose = pose,
                onePerDay = bool("onePerDay", d.onePerDay),
                seconds = values["seconds"]?.toFloatOrNull()?.takeIf { !it.isNaN() && it in SECONDS } ?: d.seconds,
                fade = bool("fade", d.fade),
                showDate = bool("showDate", d.showDate),
                showDays = bool("showDays", d.showDays),
                showPose = bool("showPose", d.showPose),
                title = values["title"]?.let { dec(it) }?.take(MAX_TITLE) ?: d.title,
                format = format,
                overlays = if (overlaysChosen || overlays.isNotEmpty()) overlays else null
            )
        }
    }
}
