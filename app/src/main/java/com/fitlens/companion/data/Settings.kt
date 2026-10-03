package com.fitlens.companion.data

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Settings that belong to this phone (#38): folders and their permissions, schedules, and state such as the last
 * import or the safety copy. They live in DataStore, outside `fitlens.db`, so they're never in a `.fitlens` backup
 * and a restore never replaces them.
 */
data class DeviceSettings(
    /** The FitNotes backup folder (a tree URI). */
    val backupFolder: String? = null,
    val autoSync: Boolean = false,
    val autoBackupFolder: String? = null,
    /** 0 = off, 1 = daily, 7 = weekly. */
    val autoBackupDays: Int = 0,
    val autoBackupKeep: Int = 5,
    val autoBackupLast: Long? = null,
    /** The last automatic backup that failed since the last one that worked, as "time|message". */
    val autoBackupError: String? = null,
    val backupAfterChanges: Boolean = false,
    val backupDirty: Boolean = false,
    val lastImportName: String? = null,
    val lastImportAt: Long? = null,
    val lastImportModified: Long? = null,
    val safetyAt: Long? = null,
    val safetyReason: String? = null,
    /** The last important result, as "time|level|text" (see `UiEvents`). */
    val lastResult: String? = null,
    /** The graph hint shows until the user has tapped a point and opened a graph full screen once each (#50). */
    val chartTapSeen: Boolean = false,
    val chartExpandSeen: Boolean = false,
    /** The rest-over sound (#20) as a ringtone URI, or null for the phone's default notification sound. Sounds are
     *  files on this phone, so the choice stays here and never travels in backups. */
    val restSoundUri: String? = null,
    /** The calendar's filter (#9), as `CalendarFilter.encode` wrote it, or null for none. */
    val calendarFilter: String? = null,
    /** The guided setup (#29) has been finished or skipped on this phone, or wasn't needed because data was here. */
    val setupDone: Boolean = false
)

/**
 * Preferences that belong to the user, not the phone. They stay in the database's `meta` table, so they go into
 * `.fitlens` backups and restore on another phone.
 */
data class PortableSettings(
    /** "kg" or "lbs". */
    val weightUnit: String = "kg",
    /** Set when the user chose the unit by hand, so a FitNotes import doesn't change it back. */
    val weightUnitManual: Boolean = false,
    /** Distances are logged and shown in this unit unless the exercise has its own (#7): "km", "mi" or "m".
     *  Distances are stored as typed, so changing it relabels them rather than converting them. */
    val distanceUnit: String = DistanceUnits.KM,
    /** Body lengths are shown in this unit (#7): "cm" or "in". They're stored as logged and converted for display. */
    val lengthUnit: String = LengthUnits.CM,
    /** The weight stepper's step in kg, or null for the default. */
    val weightIncrementKg: Double? = null,
    val keepScreenOn: Boolean = true,
    /** Celebrate a new personal record when a set is saved (#23). */
    val celebratePrs: Boolean = true,
    /** Where a new set's fields come from (#97): [AUTOFILL_LAST] or [AUTOFILL_EMPTY]. Routines (#21) add a third. */
    val autofillSource: String = AUTOFILL_LAST,
    /** After updating a set, select the next one of the day so it can be adjusted and saved in turn (#97). */
    val autoSelectNext: Boolean = false,
    /** Add the date and time to the names of backups saved or shared by hand (#30). Automatic backups always do. */
    val backupTimestamp: Boolean = true,
    /** Count warm-up sets in records and statistics (#43). */
    val warmupsCount: Boolean = false,
    /** The estimated-1RM formula's key (`Records.Formula`), used everywhere an estimate appears (#42). */
    val e1rmFormula: String = "auto",
    /** The most reps a set can have to be estimated from (FitNotes's Estimated 1RM settings, #148), or 0 for the
     *  formula's own limit. Never above the formula's limit. */
    val e1rmMaxReps: Int = 0,
    /** The plate calculator's bar in kg, or null for a standard bar in the display unit (#28). */
    val barKg: Double? = null,
    /** The plate calculator counts the bar in the target weight (#28). */
    val countBar: Boolean = true,
    /** The plates on hand in the display unit, comma-separated, or null for the standard set (#28). */
    val plates: String? = null,
    /** Show the W, D and F badges on sets (#43). */
    val showSetType: Boolean = true,
    /** Effort per set (#44): [Effort.OFF], [Effort.RPE] or [Effort.RIR]. */
    val effortMode: String = Effort.OFF,
    /** The first day of the week for the calendar and weekly analysis (#7): 1 = Monday … 7 = Sunday (ISO). */
    val weekStart: Int = 1,
    /** Show each exercise's category colour on the day log (#8). */
    val homeShowCategories: Boolean = true,
    /** How many sets each exercise card on the day log shows (#8): 0 for all, otherwise 1–10. */
    val homeSetsShown: Int = 0,
    /** The routine last chosen in the library's switcher (#21), or 0 for All exercises. */
    val lastRoutineId: Long = 0L,
    /** Start the workout timer when the first set of today is saved (#12). */
    val workoutTimerAuto: Boolean = false,
    /** The rest timer's length in seconds (#20). */
    val restSeconds: Int = 90,
    /** Start the rest timer when a set is saved (#20). */
    val restAutoStart: Boolean = false,
    /** Vibrate when the rest timer ends (#20). */
    val restVibrate: Boolean = true,
    /** Play a sound when the rest timer ends (#20); which sound is phone-only ([DeviceSettings.restSoundUri]). */
    val restSound: Boolean = true,
    /** The rest-over sound's volume, 10 to 100 percent of the notification volume (#20). */
    val restVolume: Int = 80,
    /** "Mark sets complete" mode (#19): tick boxes on sets and progress per exercise and workout. */
    val markComplete: Boolean = false,
    /** The chart kind chosen per graph (#137), "graph=kind;…" (`ChartKind.encode`), or null when none was chosen. */
    val graphKinds: String? = null,
    /** Graphs pinned to Analysis → Overview (#55), in order (`PinnedGraph.encode`), or null when none are pinned. */
    val pinnedGraphs: String? = null,
    /** The exercises each exercise's graph is compared with (#53), "id=id,id;…" (`GraphCompare.encode`). */
    val graphCompare: String? = null,
    /** Sex for the body fat formula (#153), `BodyFat.Sex.key`, or null until the user chooses. */
    val profileSex: String? = null,
    /** The pose new photos get (#46): null to ask each time, [Poses.NONE] for none, or one of [Poses.all]. */
    val photoDefaultPose: String? = null,
    /** How the Photos grid is grouped (#46), one of [MediaPrefs.GROUPS]. */
    val photoGroupBy: String = MediaPrefs.GROUP_MONTH,
    /** Open the slideshow and video screen with the options last used (#46). */
    val rememberVideoOpts: Boolean = true,
    /** Those options (`SlideshowPrefs.encode`), or null for the defaults. */
    val videoOpts: String? = null,
    /** The PDF report's pages: dark (as in the app) or light (for printing) (#46). */
    val pdfDark: Boolean = true,
    /** Photos per day in the PDF report's daily log, 0 to 4 (#46). */
    val pdfPhotosPerDay: Int = 2
) {
    companion object {
        const val AUTOFILL_LAST = "last"
        const val AUTOFILL_EMPTY = "empty"
    }
}

private const val STORE_NAME = "fitlens_device_settings"

private val Context.deviceStore: DataStore<Preferences> by preferencesDataStore(
    name = STORE_NAME,
    produceMigrations = { listOf(MetaToDeviceMigration) }
)

/**
 * The only way FitLens reads or writes settings. Screens observe [device] and [portable] and change them with
 * [updateDevice] and [updatePortable]. Nothing else touches DataStore or the settings rows in `meta`, so where a
 * setting is stored never shows.
 *
 * Both sets are loaded once, off the main thread, when the app starts. After that this in-memory copy is the truth
 * for the process: reads are instant, and every change is written through in order.
 */
object Settings {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()
    private lateinit var store: DataStore<Preferences>
    private lateinit var loaded: Deferred<Unit>
    /** True once DataStore has loaded, so its one-time copy from `meta` has run and the old rows can go (#98). */
    @Volatile private var deviceStoreReady = false

    private val _device = MutableStateFlow(DeviceSettings())
    val device: StateFlow<DeviceSettings> = _device

    private val _portable = MutableStateFlow(PortableSettings())
    val portable: StateFlow<PortableSettings> = _portable

    /** Called once per process from `App`, after `Store.init`. Starts loading in the background. */
    fun init(context: Context) {
        store = context.applicationContext.deviceStore
        loaded = scope.async {
            // A damaged settings file or database must not stop the app: it starts from the defaults instead.
            runCatching {
                val prefs = store.data.first()
                _device.value = deviceFrom { prefs[stringPreferencesKey(it)] }
                deviceStoreReady = true
            }
            runCatching { dropLegacyDeviceRows() }
            runCatching { _portable.value = portableFrom(Store.db::getMeta) }
            Unit
        }
    }

    /** Waits for the first load. Everything that reads settings goes through here. */
    suspend fun awaitLoaded() = loaded.await()

    /**
     * The current phone settings. Loading takes a few milliseconds at start-up; a caller that arrives before it's
     * done waits for it rather than seeing defaults (a background backup must never think it's switched off).
     */
    fun current(): DeviceSettings {
        if (!loaded.isCompleted) runBlocking { loaded.await() }
        return _device.value
    }

    fun currentPortable(): PortableSettings {
        if (!loaded.isCompleted) runBlocking { loaded.await() }
        return _portable.value
    }

    /** Changes phone settings. The change shows at once and is saved in the background, in order. */
    fun updateDevice(change: (DeviceSettings) -> DeviceSettings) {
        current()
        _device.updateAndGet(change)
        scope.launch { saveDevice() }
    }

    /** Like [updateDevice], but returns only once the change is on disk. For background work that may end soon. */
    suspend fun updateDeviceNow(change: (DeviceSettings) -> DeviceSettings) {
        awaitLoaded()
        _device.updateAndGet(change)
        saveDevice()
    }

    /** Changes the user's preferences, saves them to `meta` and refreshes the data snapshot that shows them. */
    fun updatePortable(change: (PortableSettings) -> PortableSettings) {
        currentPortable()
        _portable.updateAndGet { old -> withPlatesConverted(old, change(old)) }
        scope.launch {
            writeLock.withLock { savePortable(_portable.value) }
            // Preferences only change how the data is shown, so nothing is re-read (#60).
            Store.refresh()
        }
    }

    /**
     * Re-reads the preferences from the database. Called from `Store.reload()`, so a restore or an import that set
     * the weight unit is picked up with the rest of the data.
     */
    fun reloadPortable() {
        if (!::loaded.isInitialized || !loaded.isCompleted) return
        _portable.value = portableFrom(Store.db::getMeta)
    }

    /**
     * Removes the phone-only rows that `meta` kept after the move to DataStore in 1.0.21 (#98). It only runs once
     * DataStore has loaded, because loading is what copies them over: someone updating straight from 1.0.20 keeps
     * every value. It runs at start-up and after a restore, so an older backup's stale rows don't linger either.
     * No schema change is needed, and an older build simply finds no rows and falls back to its defaults.
     */
    fun dropLegacyDeviceRows() {
        if (deviceStoreReady) Store.db.deleteMeta(DEVICE_KEYS)
    }

    private suspend fun saveDevice() = writeLock.withLock {
        val values = _device.value.toMap()
        store.edit { p ->
            values.forEach { (k, v) ->
                val key = stringPreferencesKey(k)
                if (v == null) p.remove(key) else p[key] = v
            }
        }
    }

    private fun savePortable(s: PortableSettings) {
        val db = Store.db
        db.setMeta(P_WEIGHT_UNIT, s.weightUnit)
        db.setMeta(P_WEIGHT_UNIT_MANUAL, if (s.weightUnitManual) "1" else null)
        db.setMeta(P_WEIGHT_INCREMENT, s.weightIncrementKg?.toString())
        db.setMeta(P_DISTANCE_UNIT, s.distanceUnit.takeIf { it != DistanceUnits.KM })
        db.setMeta(P_LENGTH_UNIT, s.lengthUnit.takeIf { it != LengthUnits.CM })
        db.setMeta(P_KEEP_SCREEN_ON, if (s.keepScreenOn) null else "0")
        db.setMeta(P_CELEBRATE_PRS, if (s.celebratePrs) null else "0")
        db.setMeta(P_AUTOFILL, s.autofillSource.takeIf { it != PortableSettings.AUTOFILL_LAST })
        db.setMeta(P_AUTO_SELECT_NEXT, if (s.autoSelectNext) "1" else null)
        db.setMeta(P_BACKUP_TIMESTAMP, if (s.backupTimestamp) null else "0")
        db.setMeta(P_WARMUPS_COUNT, if (s.warmupsCount) "1" else null)
        db.setMeta(P_E1RM_FORMULA, s.e1rmFormula.takeIf { it != "auto" })
        db.setMeta(P_E1RM_MAX_REPS, s.e1rmMaxReps.takeIf { it > 0 }?.toString())
        db.setMeta(P_BAR_KG, s.barKg?.toString())
        db.setMeta(P_COUNT_BAR, if (s.countBar) null else "0")
        db.setMeta(P_PLATES, s.plates)
        db.setMeta(P_SHOW_SET_TYPE, if (s.showSetType) null else "0")
        db.setMeta(P_EFFORT_MODE, s.effortMode.takeIf { it != Effort.OFF })
        db.setMeta(P_WEEK_START, s.weekStart.takeIf { it != 1 }?.toString())
        db.setMeta(P_HOME_CATEGORIES, if (s.homeShowCategories) null else "0")
        db.setMeta(P_HOME_SETS, s.homeSetsShown.takeIf { it != 0 }?.toString())
        db.setMeta(P_LAST_ROUTINE, s.lastRoutineId.takeIf { it != 0L }?.toString())
        db.setMeta(P_TIMER_AUTO, if (s.workoutTimerAuto) "1" else null)
        db.setMeta(P_REST_SECONDS, s.restSeconds.takeIf { it != 90 }?.toString())
        db.setMeta(P_REST_AUTO, if (s.restAutoStart) "1" else null)
        db.setMeta(P_REST_VIBRATE, if (s.restVibrate) null else "0")
        db.setMeta(P_REST_SOUND, if (s.restSound) null else "0")
        db.setMeta(P_REST_VOLUME, s.restVolume.takeIf { it != 80 }?.toString())
        db.setMeta(P_MARK_COMPLETE, if (s.markComplete) "1" else null)
        db.setMeta(P_GRAPH_KINDS, s.graphKinds?.takeIf { it.isNotBlank() })
        db.setMeta(P_PINNED_GRAPHS, s.pinnedGraphs?.takeIf { it.isNotBlank() })
        db.setMeta(P_GRAPH_COMPARE, s.graphCompare?.takeIf { it.isNotBlank() })
        db.setMeta(P_PROFILE_SEX, s.profileSex?.takeIf { it.isNotBlank() })
        db.setMeta(P_PHOTO_POSE, MediaPrefs.storeDefaultPose(s.photoDefaultPose))
        db.setMeta(P_PHOTO_GROUP, s.photoGroupBy.takeIf { it != MediaPrefs.GROUP_MONTH })
        db.setMeta(P_REMEMBER_VIDEO, if (s.rememberVideoOpts) null else "0")
        db.setMeta(P_VIDEO_OPTS, s.videoOpts?.takeIf { it.isNotBlank() })
        db.setMeta(P_PDF_STYLE, if (s.pdfDark) null else "light")
        db.setMeta(P_PDF_PHOTOS, s.pdfPhotosPerDay.takeIf { it != 2 }?.toString())
    }

    // ---------- Storage keys. The names match the old `meta` keys, so the migration is a straight copy. ----------

    private const val D_BACKUP_FOLDER = "backup_folder"
    private const val D_AUTO_SYNC = "auto_sync"
    private const val D_AUTO_FOLDER = "auto_backup_folder"
    private const val D_AUTO_DAYS = "auto_backup_days"
    private const val D_AUTO_KEEP = "auto_backup_keep"
    private const val D_AUTO_LAST = "auto_backup_last"
    private const val D_AUTO_ERROR = "auto_backup_error"
    private const val D_AFTER_CHANGES = "auto_backup_on_change"
    private const val D_DIRTY = "auto_backup_dirty"
    private const val D_IMPORT_NAME = "last_import_name"
    private const val D_IMPORT_AT = "last_import_at"
    private const val D_IMPORT_MODIFIED = "last_import_modified"
    private const val D_SAFETY_AT = "safety_at"
    private const val D_SAFETY_REASON = "safety_reason"
    private const val D_LAST_RESULT = "last_result"
    private const val D_CHART_TAP = "chart_hint_tap"
    private const val D_CHART_EXPAND = "chart_hint_expand"
    private const val D_REST_SOUND_URI = "rest_sound_uri"
    private const val D_CALENDAR_FILTER = "calendar_filter"
    private const val D_SETUP_DONE = "setup_done"

    /** Every phone-only key, as it was named in `meta` before 1.0.21. */
    internal val DEVICE_KEYS = listOf(
        D_BACKUP_FOLDER, D_AUTO_SYNC, D_AUTO_FOLDER, D_AUTO_DAYS, D_AUTO_KEEP, D_AUTO_LAST, D_AUTO_ERROR,
        D_AFTER_CHANGES, D_DIRTY, D_IMPORT_NAME, D_IMPORT_AT, D_IMPORT_MODIFIED, D_SAFETY_AT, D_SAFETY_REASON,
        D_LAST_RESULT
    )

    private const val P_WEIGHT_UNIT = "weight_unit"
    private const val P_WEIGHT_UNIT_MANUAL = "weight_unit_manual"
    private const val P_WEIGHT_INCREMENT = "weight_increment"
    private const val P_DISTANCE_UNIT = "distance_unit"
    private const val P_LENGTH_UNIT = "length_unit"
    private const val P_KEEP_SCREEN_ON = "keep_screen_on"
    private const val P_CELEBRATE_PRS = "celebrate_prs"
    private const val P_AUTOFILL = "autofill_source"
    private const val P_AUTO_SELECT_NEXT = "auto_select_next"
    private const val P_BACKUP_TIMESTAMP = "backup_timestamp"
    private const val P_WARMUPS_COUNT = "warmups_count"
    private const val P_E1RM_FORMULA = "e1rm_formula"
    private const val P_E1RM_MAX_REPS = "e1rm_max_reps"
    private const val P_BAR_KG = "bar_kg"
    private const val P_COUNT_BAR = "count_bar"
    private const val P_PLATES = "plates"
    private const val P_SHOW_SET_TYPE = "show_set_type"
    private const val P_EFFORT_MODE = "effort_mode"
    private const val P_WEEK_START = "week_start"
    private const val P_HOME_CATEGORIES = "home_show_categories"
    private const val P_HOME_SETS = "home_sets_shown"
    private const val P_LAST_ROUTINE = "last_routine"
    private const val P_TIMER_AUTO = "workout_timer_auto"
    private const val P_REST_SECONDS = "rest_seconds"
    private const val P_REST_AUTO = "rest_auto_start"
    private const val P_REST_VIBRATE = "rest_vibrate"
    private const val P_REST_SOUND = "rest_sound"
    private const val P_REST_VOLUME = "rest_volume"
    private const val P_MARK_COMPLETE = "mark_complete"
    private const val P_GRAPH_KINDS = "graph_kinds"
    private const val P_PINNED_GRAPHS = "pinned_graphs"
    private const val P_GRAPH_COMPARE = "graph_compare"
    private const val P_PROFILE_SEX = "profile_sex"
    // Progress photos and media (#46), with the names the issue gave them.
    private const val P_PHOTO_POSE = "photo_default_pose"
    private const val P_PHOTO_GROUP = "photo_group_by"
    private const val P_REMEMBER_VIDEO = "remember_video_opts"
    private const val P_VIDEO_OPTS = "video_opts"
    private const val P_PDF_STYLE = "pdf_style"
    private const val P_PDF_PHOTOS = "pdf_photos_per_day"

    private fun bool(v: String?) = v == "1"

    private fun deviceFrom(get: (String) -> String?) = DeviceSettings(
        backupFolder = get(D_BACKUP_FOLDER),
        autoSync = bool(get(D_AUTO_SYNC)),
        autoBackupFolder = get(D_AUTO_FOLDER),
        autoBackupDays = get(D_AUTO_DAYS)?.toIntOrNull() ?: 0,
        autoBackupKeep = get(D_AUTO_KEEP)?.toIntOrNull() ?: 5,
        autoBackupLast = get(D_AUTO_LAST)?.toLongOrNull(),
        autoBackupError = get(D_AUTO_ERROR),
        backupAfterChanges = bool(get(D_AFTER_CHANGES)),
        backupDirty = bool(get(D_DIRTY)),
        lastImportName = get(D_IMPORT_NAME),
        lastImportAt = get(D_IMPORT_AT)?.toLongOrNull(),
        lastImportModified = get(D_IMPORT_MODIFIED)?.toLongOrNull(),
        safetyAt = get(D_SAFETY_AT)?.toLongOrNull(),
        safetyReason = get(D_SAFETY_REASON),
        lastResult = get(D_LAST_RESULT),
        chartTapSeen = bool(get(D_CHART_TAP)),
        chartExpandSeen = bool(get(D_CHART_EXPAND)),
        restSoundUri = get(D_REST_SOUND_URI),
        calendarFilter = get(D_CALENDAR_FILTER),
        setupDone = bool(get(D_SETUP_DONE))
    )

    private fun DeviceSettings.toMap(): Map<String, String?> = mapOf(
        D_BACKUP_FOLDER to backupFolder,
        D_AUTO_SYNC to if (autoSync) "1" else "0",
        D_AUTO_FOLDER to autoBackupFolder,
        D_AUTO_DAYS to autoBackupDays.toString(),
        D_AUTO_KEEP to autoBackupKeep.toString(),
        D_AUTO_LAST to autoBackupLast?.toString(),
        D_AUTO_ERROR to autoBackupError,
        D_AFTER_CHANGES to if (backupAfterChanges) "1" else "0",
        D_DIRTY to if (backupDirty) "1" else null,
        D_IMPORT_NAME to lastImportName,
        D_IMPORT_AT to lastImportAt?.toString(),
        D_IMPORT_MODIFIED to lastImportModified?.toString(),
        D_SAFETY_AT to safetyAt?.toString(),
        D_SAFETY_REASON to safetyReason,
        D_LAST_RESULT to lastResult,
        D_CHART_TAP to if (chartTapSeen) "1" else null,
        D_CHART_EXPAND to if (chartExpandSeen) "1" else null,
        D_REST_SOUND_URI to restSoundUri,
        D_CALENDAR_FILTER to calendarFilter,
        D_SETUP_DONE to if (setupDone) "1" else null
    )

    private fun portableFrom(get: (String) -> String?) = PortableSettings(
        weightUnit = get(P_WEIGHT_UNIT) ?: "kg",
        weightUnitManual = get(P_WEIGHT_UNIT_MANUAL) != null,
        weightIncrementKg = get(P_WEIGHT_INCREMENT)?.toDoubleOrNull()?.takeIf { it > 0 },
        // An unknown unit (from a later build's backup) falls back to the default.
        distanceUnit = DistanceUnits.of(get(P_DISTANCE_UNIT)) ?: DistanceUnits.KM,
        lengthUnit = LengthUnits.of(get(P_LENGTH_UNIT)) ?: LengthUnits.CM,
        keepScreenOn = get(P_KEEP_SCREEN_ON) != "0",
        celebratePrs = get(P_CELEBRATE_PRS) != "0",
        // An unknown value (say, "routine" from a later build's backup) falls back to the default.
        autofillSource = get(P_AUTOFILL)?.takeIf { it == PortableSettings.AUTOFILL_EMPTY } ?: PortableSettings.AUTOFILL_LAST,
        autoSelectNext = bool(get(P_AUTO_SELECT_NEXT)),
        backupTimestamp = get(P_BACKUP_TIMESTAMP) != "0",
        warmupsCount = bool(get(P_WARMUPS_COUNT)),
        // An unknown formula (from a later build's backup) falls back to Automatic.
        e1rmFormula = Records.Formula.of(get(P_E1RM_FORMULA)).key,
        e1rmMaxReps = get(P_E1RM_MAX_REPS)?.toIntOrNull()?.takeIf { it in 1..Records.MAX_ESTIMATE_REPS } ?: 0,
        barKg = get(P_BAR_KG)?.toDoubleOrNull()?.takeIf { it >= 0 },
        countBar = get(P_COUNT_BAR) != "0",
        plates = get(P_PLATES)?.takeIf { it.isNotBlank() },
        showSetType = get(P_SHOW_SET_TYPE) != "0",
        effortMode = get(P_EFFORT_MODE)?.takeIf { it == Effort.RPE || it == Effort.RIR } ?: Effort.OFF,
        weekStart = get(P_WEEK_START)?.toIntOrNull()?.takeIf { it in 1..7 } ?: 1,
        homeShowCategories = get(P_HOME_CATEGORIES) != "0",
        homeSetsShown = get(P_HOME_SETS)?.toIntOrNull()?.takeIf { it in 1..10 } ?: 0,
        lastRoutineId = get(P_LAST_ROUTINE)?.toLongOrNull() ?: 0L,
        workoutTimerAuto = bool(get(P_TIMER_AUTO)),
        // Any exact length from 1 s to 60 min (#105).
        restSeconds = get(P_REST_SECONDS)?.toIntOrNull()?.takeIf { it in 1..3600 } ?: 90,
        restAutoStart = bool(get(P_REST_AUTO)),
        restVibrate = get(P_REST_VIBRATE) != "0",
        restSound = get(P_REST_SOUND) != "0",
        restVolume = get(P_REST_VOLUME)?.toIntOrNull()?.coerceIn(10, 100) ?: 80,
        markComplete = bool(get(P_MARK_COMPLETE)),
        graphKinds = get(P_GRAPH_KINDS)?.takeIf { it.isNotBlank() },
        pinnedGraphs = get(P_PINNED_GRAPHS)?.takeIf { it.isNotBlank() },
        graphCompare = get(P_GRAPH_COMPARE)?.takeIf { it.isNotBlank() },
        profileSex = get(P_PROFILE_SEX)?.takeIf { it.isNotBlank() },
        // Unknown values (from a later build's backup) fall back to the defaults (#46).
        photoDefaultPose = MediaPrefs.defaultPoseOf(get(P_PHOTO_POSE)),
        photoGroupBy = MediaPrefs.groupOf(get(P_PHOTO_GROUP)),
        rememberVideoOpts = get(P_REMEMBER_VIDEO) != "0",
        videoOpts = get(P_VIDEO_OPTS)?.takeIf { it.isNotBlank() },
        pdfDark = get(P_PDF_STYLE) != "light",
        pdfPhotosPerDay = MediaPrefs.photosPerDayOf(get(P_PDF_PHOTOS))
    )
}

/**
 * Copies the phone-only settings from `meta` into DataStore the first time 1.0.21 or later runs, keeping every
 * value. [Settings.dropLegacyDeviceRows] then removes the rows (#98). Keep this while anyone may still update from
 * 1.0.20 or earlier.
 */
private object MetaToDeviceMigration : DataMigration<Preferences> {
    private val MIGRATED = stringPreferencesKey("migrated_from_meta")

    override suspend fun shouldMigrate(currentData: Preferences): Boolean = currentData[MIGRATED] == null

    override suspend fun migrate(currentData: Preferences): Preferences {
        val p = currentData.toMutablePreferences()
        Settings.DEVICE_KEYS.forEach { k -> Store.db.getMeta(k)?.let { p[stringPreferencesKey(k)] = it } }
        p[MIGRATED] = "1"
        return p
    }

    override suspend fun cleanUp() {}
}

/**
 * Switching between kg and lbs converts the plate calculator's plate list (#117), the only weight setting stored in
 * the display unit rather than in kg. A list the same change edited is left as it was given.
 */
internal fun withPlatesConverted(old: PortableSettings, new: PortableSettings): PortableSettings {
    if (old.weightUnit == new.weightUnit || new.plates == null || new.plates != old.plates) return new
    val converted = new.plates.split(',', ' ', ';').mapNotNull { it.trim().replace(',', '.').toDoubleOrNull() }
        .joinToString(", ") { fmtNum(WeightUnits.convert(it, old.weightUnit, new.weightUnit), 2) }
    return new.copy(plates = converted.ifBlank { null })
}
