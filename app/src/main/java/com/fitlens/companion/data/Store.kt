package com.fitlens.companion.data

import android.content.ContentValues
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

/** Everything the UI needs, loaded into memory (FitNotes data sets are small). */
class Snapshot(
    val categories: Map<Long, Category>,
    val exercises: Map<Long, Exercise>,
    val sets: List<SetRow>,
    val measurementDefs: List<MeasurementDef>,
    val records: List<MRecord>,
    val photos: List<Photo>,
    val workoutComments: Map<String, List<String>>,
    val workoutTimes: Map<String, List<WorkoutTime>>,
    val weightUnit: String,
    val photoDir: File,
    /** Count warm-up sets in records and statistics (#43, a setting; off by default). */
    val countWarmups: Boolean = false,
    /** The first day of the week, 1 = Monday … 7 = Sunday (#7). */
    val weekStart: Int = 1,
    /** Exercise goals (#25), in each exercise's order. */
    val goals: List<ExerciseGoal> = emptyList(),
    /** Saved workouts (#100), in the user's order. */
    val savedWorkouts: List<SavedWorkout> = emptyList(),
    /** Routines (#21), in the user's order. */
    val routines: List<Routine> = emptyList(),
    /** Which saved workout each logged day was started from, by date (#21). */
    val workoutOrigins: Map<String, WorkoutOrigin> = emptyMap()
) {
    val routinesById: Map<Long, Routine> = routines.associateBy { it.id }
    val savedWorkoutsById: Map<Long, SavedWorkout> = savedWorkouts.associateBy { it.id }
    val goalsByExercise: Map<Long, List<ExerciseGoal>> = goals.groupBy { it.exerciseId }
    val setsByDate: Map<String, List<SetRow>> = sets.groupBy { it.date }
    val setsByExercise: Map<Long, List<SetRow>> = sets.groupBy { it.exerciseId }

    /**
     * The sets that count for records, estimated maxes, graphs and analysis: every set, or every set except warm-ups
     * unless the setting counts them (#43). Lists and history still show every set.
     */
    val statSets: List<SetRow> = if (countWarmups) sets else sets.filter { !it.isWarmup }
    val statSetsByExercise: Map<Long, List<SetRow>> = statSets.groupBy { it.exerciseId }

    // ---- Exercise library (#13) ----
    /** Every category, in the order the library shows them. */
    val categoriesSorted: List<Category> = categories.values.sortedWith(compareBy({ it.sortOrder }, { it.name.lowercase() }))
    /** Every exercise in the library, whether or not anything has been logged for it, by name. */
    val exercisesSorted: List<Exercise> = exercises.values.sortedBy { it.name.lowercase() }
    val favouriteExercises: List<Exercise> = exercisesSorted.filter { it.favourite }
    /** Last date each exercise was logged (sets are loaded in date order). */
    val lastUsedByExercise: Map<Long, String> = setsByExercise.mapValues { e -> e.value.last().date }
    /** Number of separate days each exercise was logged. */
    val workoutsByExercise: Map<Long, Int> = setsByExercise.mapValues { e -> e.value.distinctBy { it.date }.size }


    val recordsByDate: Map<String, List<MRecord>> = records.groupBy { it.date }
    /** Records per measurement, sorted by date then time. */
    val recordsByName: Map<String, List<MRecord>> =
        records.groupBy { it.name }.mapValues { e -> e.value.sortedWith(compareBy({ it.date }, { it.time })) }
    val datedPhotos: List<Photo> = photos.filter { it.date != null }
        .sortedWith(compareBy({ it.date }, { it.takenAt ?: "" }, { it.id }))
    val photosByDate: Map<String, List<Photo>> = datedPhotos.groupBy { it.date!! }
    val photosById: Map<Long, Photo> = photos.associateBy { it.id }
    val undatedPhotos: List<Photo> = photos.filter { it.date == null }
    val reviewPhotos: List<Photo> = photos.filter { DateSources.needsReview(it.dateSource) }

    /** Every date that has anything on it, newest first. */
    val allDates: List<String> =
        (setsByDate.keys + recordsByDate.keys + photosByDate.keys + workoutComments.keys)
            .toSortedSet().toList().reversed()

    /** Measurements that have at least one record, plus all custom metrics, in FitNotes order. */
    val usedMeasurements: List<MeasurementDef> = run {
        val defs = measurementDefs.associateBy { it.name }
        (recordsByName.keys + measurementDefs.filter { it.custom }.map { it.name }).distinct().map { name ->
            defs[name] ?: MeasurementDef(name, recordsByName[name]?.firstOrNull()?.unit ?: "", 999, 0, 0.0, true)
        }.sortedWith(compareBy({ it.sortOrder }, { it.name }))
    }

    val customMetrics: List<MeasurementDef> = measurementDefs.filter { it.custom }.sortedBy { it.name.lowercase() }

    /** FitNotes measurement names a custom metric can be linked to. */
    val fitNotesMeasurementNames: List<String> = measurementDefs.filter { !it.custom }.map { it.name }.sortedBy { it.lowercase() }

    val bodyweightName: String? = usedMeasurements.firstOrNull { it.name.equals("Bodyweight", true) || it.name.equals("Body Weight", true) }?.name
        ?: usedMeasurements.firstOrNull()?.name

    fun photoFile(p: Photo): File = File(photoDir, p.file)

    fun weight(kg: Double): Double = if (weightUnit == "lbs") kg * 2.2046226 else kg

    /** The inverse of [weight]: turns a number the user typed in their unit back into the kilograms we store. */
    fun toKg(shown: Double): Double = if (weightUnit == "lbs") shown / 2.2046226 else shown

    fun fmtWeight(kg: Double): String = fmtNum(weight(kg), 2)

    /** Last recorded value of a measurement on a given date. */
    fun valueOn(name: String, date: String): MRecord? =
        recordsByName[name]?.lastOrNull { it.date == date }

    /**
     * Value on the date if present, otherwise the nearest record within [windowDays].
     * Returns the record and whether it was an exact-date match.
     */
    fun valueNear(name: String, date: String, windowDays: Int): Pair<MRecord, Boolean>? {
        valueOn(name, date)?.let { return it to true }
        val list = recordsByName[name] ?: return null
        val d = Dates.epochDay(date)
        var best: MRecord? = null
        var bestDist = Long.MAX_VALUE
        for (r in list) {
            val dist = abs(Dates.epochDay(r.date) - d)
            if (dist < bestDist) { bestDist = dist; best = r }
        }
        return if (best != null && bestDist <= windowDays) best to false else null
    }

    /** One value per day (last of the day), for graphs. */
    fun dailySeries(name: String): List<MRecord> =
        (recordsByName[name] ?: emptyList()).groupBy { it.date }.map { it.value.last() }.sortedBy { it.date }

    fun categoryOf(exerciseId: Long): Category? = exercises[exerciseId]?.let { categories[it.categoryId] }
}

object Store {
    lateinit var db: Db
        private set
    lateinit var photoDir: File
        private set
    private val _snapshot = MutableStateFlow<Snapshot?>(null)
    val snapshot: StateFlow<Snapshot?> = _snapshot

    fun init(context: Context) {
        db = Db(context.applicationContext)
        photoDir = File(context.filesDir, "photos").apply { mkdirs() }
    }

    suspend fun reload() = withContext(Dispatchers.IO) {
        Settings.reloadPortable()
        _snapshot.value = load()
    }

    private fun load(): Snapshot {
        val r = db.readableDatabase
        val categories = HashMap<Long, Category>()
        r.rawQuery("SELECT id, name, colour, sort_order, source FROM category", null).use { c ->
            while (c.moveToNext()) categories[c.lng(0)] = Category(c.lng(0), c.strOr(1), c.int(2), c.int(3), c.strOr(4, Sources.FITLENS))
        }
        val exercises = HashMap<Long, Exercise>()
        r.rawQuery("SELECT id, name, category_id, type, notes, source, favourite, weight_step, default_graph FROM exercise", null).use { c ->
            while (c.moveToNext()) exercises[c.lng(0)] =
                Exercise(
                    c.lng(0), c.strOr(1), c.lng(2), c.int(3), c.str(4), c.strOr(5, Sources.FITLENS), c.int(6) != 0,
                    if (c.isNull(7)) null else c.getDouble(7), if (c.isNull(8)) -1 else c.getInt(8)
                )
        }
        val sets = ArrayList<SetRow>()
        r.rawQuery("SELECT id, exercise_id, date, weight, reps, distance, duration, is_pr, comment, source, set_type, rpe, position FROM workout_set ORDER BY date, position, id", null).use { c ->
            while (c.moveToNext()) sets.add(
                SetRow(
                    c.lng(0), c.lng(1), c.strOr(2), c.dbl(3), c.int(4), c.dbl(5), c.int(6), c.int(7) != 0, c.str(8),
                    c.strOr(9, Sources.FITLENS), c.int(10), if (c.isNull(11)) null else c.getDouble(11), c.lng(12)
                )
            )
        }
        val defs = ArrayList<MeasurementDef>()
        r.rawQuery("SELECT name, unit, sort_order, goal_type, goal_value, enabled, custom, link FROM measurement ORDER BY sort_order, name", null).use { c ->
            while (c.moveToNext()) defs.add(
                MeasurementDef(c.strOr(0), c.strOr(1), c.int(2), c.int(3), c.dbl(4), c.int(5) != 0, c.int(6) != 0, c.str(7))
            )
        }
        val records = ArrayList<MRecord>()
        r.rawQuery("SELECT id, name, unit, date, time, value, comment, source FROM mrecord ORDER BY date, time", null).use { c ->
            while (c.moveToNext()) records.add(
                MRecord(c.lng(0), c.strOr(1), c.strOr(2), c.strOr(3), c.strOr(4), c.dbl(5), c.str(6), c.strOr(7))
            )
        }
        val photos = ArrayList<Photo>()
        r.rawQuery("SELECT id, file, date, taken_at, date_source, pose, note, original_name FROM photo ORDER BY date, taken_at, id", null).use { c ->
            while (c.moveToNext()) photos.add(
                Photo(c.lng(0), c.strOr(1), c.str(2), c.str(3), c.strOr(4, DateSources.NONE), c.strOr(5), c.str(6), c.str(7))
            )
        }
        val comments = HashMap<String, MutableList<String>>()
        r.rawQuery("SELECT date, comment FROM workout_comment ORDER BY id", null).use { c ->
            while (c.moveToNext()) {
                val d = c.strOr(0).take(10)
                val t = c.strOr(1)
                if (t.isNotBlank()) comments.getOrPut(d) { ArrayList() }.add(t)
            }
        }
        val times = HashMap<String, MutableList<WorkoutTime>>()
        r.rawQuery("SELECT date, start, finish FROM workout_time ORDER BY id", null).use { c ->
            while (c.moveToNext()) {
                val d = c.strOr(0).take(10)
                times.getOrPut(d) { ArrayList() }.add(WorkoutTime(d, c.strOr(1), c.strOr(2)))
            }
        }
        val goals = ArrayList<ExerciseGoal>()
        r.rawQuery("SELECT id, exercise_id, kind, target, sort_order FROM exercise_goal ORDER BY exercise_id, sort_order, id", null).use { c ->
            while (c.moveToNext()) goals.add(ExerciseGoal(c.lng(0), c.lng(1), c.int(2), c.dbl(3), c.int(4)))
        }
        val prefs = Settings.currentPortable()
        // Weekly analysis follows the week-start setting (#7).
        Analysis.weekStart = java.time.DayOfWeek.of(prefs.weekStart)
        return Snapshot(
            categories, exercises, sets, defs, records, photos, comments, times, prefs.weightUnit, photoDir,
            prefs.warmupsCount, prefs.weekStart, goals, SavedWorkouts.load(r), Routines.load(r), Routines.loadOrigins(r)
        )
    }

    // ---------- Photo edits ----------

    suspend fun setPhotoDate(ids: Collection<Long>, date: String?) = withContext(Dispatchers.IO) {
        val w = db.writableDatabase
        w.beginTransaction()
        try {
            ids.forEach { id ->
                val cv = ContentValues().apply {
                    if (date == null) putNull("date") else put("date", date)
                    put("date_source", if (date == null) DateSources.NONE else DateSources.MANUAL)
                }
                w.update("photo", cv, "id=?", arrayOf(id.toString()))
            }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
        _snapshot.value = load()
    }

    /** Accept the automatically detected date (removes the "needs review" flag). */
    suspend fun confirmPhotoDates(ids: Collection<Long>) = withContext(Dispatchers.IO) {
        val w = db.writableDatabase
        ids.forEach { id ->
            w.execSQL("UPDATE photo SET date_source='${DateSources.MANUAL}' WHERE id=? AND date IS NOT NULL", arrayOf<Any>(id))
        }
        _snapshot.value = load()
    }

    suspend fun setPhotoPose(ids: Collection<Long>, pose: String) = withContext(Dispatchers.IO) {
        val w = db.writableDatabase
        ids.forEach { id ->
            val cv = ContentValues().apply { put("pose", pose) }
            w.update("photo", cv, "id=?", arrayOf(id.toString()))
        }
        _snapshot.value = load()
    }

    suspend fun setPhotoNote(id: Long, note: String) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply { put("note", note) }
        db.writableDatabase.update("photo", cv, "id=?", arrayOf(id.toString()))
        _snapshot.value = load()
    }

    suspend fun deletePhotos(ids: Collection<Long>) = withContext(Dispatchers.IO) {
        val w = db.writableDatabase
        ids.forEach { id ->
            w.rawQuery("SELECT file FROM photo WHERE id=?", arrayOf(id.toString())).use { c ->
                if (c.moveToFirst()) File(photoDir, c.getString(0)).delete()
            }
            w.delete("photo", "id=?", arrayOf(id.toString()))
        }
        _snapshot.value = load()
    }

    // ---------- Manual measurements ----------

    suspend fun addManualRecord(name: String, unit: String, date: String, time: String, value: Double, comment: String?) =
        withContext(Dispatchers.IO) {
            val w = db.writableDatabase
            val def = ContentValues().apply { put("name", name); put("unit", unit); put("sort_order", 999) }
            w.insertWithOnConflict("measurement", null, def, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE)
            val cv = ContentValues().apply {
                put("name", name); put("unit", unit); put("date", date); put("time", time)
                put("value", value); put("comment", comment); put("source", "manual")
            }
            w.insert("mrecord", null, cv)
            _snapshot.value = load()
        }

    /** Sets a measurement's goal (#27), and marks it so a FitNotes import keeps the user's choice. */
    suspend fun setMeasurementGoal(name: String, unit: String, type: Int, value: Double) = withContext(Dispatchers.IO) {
        val w = db.writableDatabase
        ensureMeasurement(w, name, unit, 999)
        w.update("measurement", ContentValues().apply {
            put("goal_type", type); put("goal_value", value); put("edited", 1)
        }, "name=?", arrayOf(name))
        _snapshot.value = load()
    }

    /** Stores [names] as the measurement order, top first (#27), and marks each as the user's choice. */
    suspend fun reorderMeasurements(names: List<String>, units: Map<String, String>) = withContext(Dispatchers.IO) {
        val w = db.writableDatabase
        w.beginTransaction()
        try {
            names.forEachIndexed { i, n ->
                ensureMeasurement(w, n, units[n] ?: "", i)
                w.update("measurement", ContentValues().apply { put("sort_order", i); put("edited", 1) }, "name=?", arrayOf(n))
            }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
        _snapshot.value = load()
    }

    /** A measurement seen only in records has no definition row yet; this adds one so it can hold a goal or order. */
    private fun ensureMeasurement(w: android.database.sqlite.SQLiteDatabase, name: String, unit: String, order: Int) {
        val def = ContentValues().apply { put("name", name); put("unit", unit); put("sort_order", order) }
        w.insertWithOnConflict("measurement", null, def, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE)
    }

    // ---------- Custom metrics ----------

    /**
     * Creates or edits a custom metric. FitNotes values for the linked measurement (or the same name, ignoring case)
     * are moved under it now, and every later backup import fills it in the same way.
     */
    suspend fun saveCustomMetric(original: String?, name: String, unit: String, link: String?) = withContext(Dispatchers.IO) {
        val w = db.writableDatabase
        w.beginTransaction()
        try {
            val old = original?.let { o -> loadDef(w, o) }
            if (old != null) {
                releaseImported(w, old)
                if (old.name != name) {
                    w.execSQL("UPDATE mrecord SET name=? WHERE name=? AND source='manual'", arrayOf(name, old.name))
                    w.delete("measurement", "name=?", arrayOf(old.name))
                }
            }
            val existing = loadDef(w, name)
            w.insertWithOnConflict("measurement", null, ContentValues().apply {
                put("name", name); put("unit", unit); put("sort_order", existing?.sortOrder ?: old?.sortOrder ?: 900)
                put("goal_type", existing?.goalType ?: 0); put("goal_value", existing?.goalValue ?: 0.0)
                put("enabled", 1); put("custom", 1); put("link", link?.takeIf { it.isNotBlank() })
            }, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
            val key = (link?.takeIf { it.isNotBlank() } ?: name).trim().lowercase()
            w.execSQL(
                "UPDATE mrecord SET name=? WHERE source IN ('fitnotes','csv') AND lower(trim(name))=?",
                arrayOf(name, key)
            )
            if (unit.isBlank()) {
                w.execSQL(
                    "UPDATE measurement SET unit=IFNULL((SELECT unit FROM mrecord WHERE name=? AND unit<>'' LIMIT 1), '') WHERE name=?",
                    arrayOf(name, name)
                )
            }
            w.execSQL("UPDATE mrecord SET unit=(SELECT unit FROM measurement WHERE name=?) WHERE name=? AND source='manual'", arrayOf(name, name))
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
        _snapshot.value = load()
    }

    /** Deletes a custom metric and the values entered by hand. Values from FitNotes go back to their own measurement. */
    suspend fun deleteCustomMetric(name: String) = withContext(Dispatchers.IO) {
        val w = db.writableDatabase
        w.beginTransaction()
        try {
            loadDef(w, name)?.let { releaseImported(w, it) }
            w.delete("mrecord", "name=? AND source='manual'", arrayOf(name))
            w.delete("measurement", "name=? AND custom=1", arrayOf(name))
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
        _snapshot.value = load()
    }

    /** Number of values entered by hand for a measurement. */
    fun manualCount(snap: Snapshot, name: String): Int = snap.recordsByName[name]?.count { it.source == "manual" } ?: 0

    private fun loadDef(w: android.database.sqlite.SQLiteDatabase, name: String): MeasurementDef? =
        w.rawQuery("SELECT name, unit, sort_order, goal_type, goal_value, enabled, custom, link FROM measurement WHERE name=?", arrayOf(name)).use { c ->
            if (c.moveToFirst()) MeasurementDef(c.strOr(0), c.strOr(1), c.int(2), c.int(3), c.dbl(4), c.int(5) != 0, c.int(6) != 0, c.str(7)) else null
        }

    /** Moves imported values held by a custom metric back under the FitNotes measurement they came from. */
    private fun releaseImported(w: android.database.sqlite.SQLiteDatabase, def: MeasurementDef) {
        if (!def.custom) return
        val original = w.rawQuery(
            "SELECT name FROM measurement WHERE custom=0 AND lower(trim(name))=? LIMIT 1", arrayOf(def.matchKey)
        ).use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: def.link?.takeIf { it.isNotBlank() } ?: return
        if (original != def.name) {
            w.execSQL("UPDATE mrecord SET name=? WHERE name=? AND source IN ('fitnotes','csv')", arrayOf(original, def.name))
        }
    }

    suspend fun deleteRecord(id: Long) = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("mrecord", "id=? AND source='manual'", arrayOf(id.toString()))
        _snapshot.value = load()
    }
}
