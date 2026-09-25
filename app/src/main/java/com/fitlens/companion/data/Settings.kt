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
    val chartExpandSeen: Boolean = false
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
    /** The weight stepper's step in kg, or null for the default. */
    val weightIncrementKg: Double? = null,
    val keepScreenOn: Boolean = true,
    /** Celebrate a new personal record when a set is saved (#23). */
    val celebratePrs: Boolean = true,
    /** Where a new set's fields come from (#97): [AUTOFILL_LAST] or [AUTOFILL_EMPTY]. Routines (#21) add a third. */
    val autofillSource: String = AUTOFILL_LAST,
    /** After updating a set, select the next one of the day so it can be adjusted and saved in turn (#97). */
    val autoSelectNext: Boolean = false
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
        _portable.updateAndGet(change)
        scope.launch {
            writeLock.withLock { savePortable(_portable.value) }
            Store.reload()
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
        db.setMeta(P_KEEP_SCREEN_ON, if (s.keepScreenOn) null else "0")
        db.setMeta(P_CELEBRATE_PRS, if (s.celebratePrs) null else "0")
        db.setMeta(P_AUTOFILL, s.autofillSource.takeIf { it != PortableSettings.AUTOFILL_LAST })
        db.setMeta(P_AUTO_SELECT_NEXT, if (s.autoSelectNext) "1" else null)
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

    /** Every phone-only key, as it was named in `meta` before 1.0.21. */
    internal val DEVICE_KEYS = listOf(
        D_BACKUP_FOLDER, D_AUTO_SYNC, D_AUTO_FOLDER, D_AUTO_DAYS, D_AUTO_KEEP, D_AUTO_LAST, D_AUTO_ERROR,
        D_AFTER_CHANGES, D_DIRTY, D_IMPORT_NAME, D_IMPORT_AT, D_IMPORT_MODIFIED, D_SAFETY_AT, D_SAFETY_REASON,
        D_LAST_RESULT
    )

    private const val P_WEIGHT_UNIT = "weight_unit"
    private const val P_WEIGHT_UNIT_MANUAL = "weight_unit_manual"
    private const val P_WEIGHT_INCREMENT = "weight_increment"
    private const val P_KEEP_SCREEN_ON = "keep_screen_on"
    private const val P_CELEBRATE_PRS = "celebrate_prs"
    private const val P_AUTOFILL = "autofill_source"
    private const val P_AUTO_SELECT_NEXT = "auto_select_next"

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
        chartExpandSeen = bool(get(D_CHART_EXPAND))
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
        D_CHART_EXPAND to if (chartExpandSeen) "1" else null
    )

    private fun portableFrom(get: (String) -> String?) = PortableSettings(
        weightUnit = get(P_WEIGHT_UNIT) ?: "kg",
        weightUnitManual = get(P_WEIGHT_UNIT_MANUAL) != null,
        weightIncrementKg = get(P_WEIGHT_INCREMENT)?.toDoubleOrNull()?.takeIf { it > 0 },
        keepScreenOn = get(P_KEEP_SCREEN_ON) != "0",
        celebratePrs = get(P_CELEBRATE_PRS) != "0",
        // An unknown value (say, "routine" from a later build's backup) falls back to the default.
        autofillSource = get(P_AUTOFILL)?.takeIf { it == PortableSettings.AUTOFILL_EMPTY } ?: PortableSettings.AUTOFILL_LAST,
        autoSelectNext = bool(get(P_AUTO_SELECT_NEXT))
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
