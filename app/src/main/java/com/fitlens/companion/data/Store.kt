package com.fitlens.companion.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

/**
 * The areas the snapshot is built from (#60). A write re-reads only the areas it changed; every other area, with the
 * lists and lookups built from it, is carried over to the next snapshot as it is.
 */
enum class Area {
    /** Categories, exercises, goals, workouts (routines) and which workout each date came from. */
    LIBRARY,
    /** Every logged set. */
    SETS,
    /** Workout comments, workout times and exercise comments. */
    NOTES,
    /** Measurement definitions and body values. */
    BODY,
    PHOTOS;

    companion object {
        val ALL: Set<Area> = values().toSet()
        /** What a workout write can touch, when it doesn't say more precisely. */
        val WORKOUT: Set<Area> = setOf(LIBRARY, SETS, NOTES)
    }
}

/** The library area: categories, exercises, goals and workouts, with their lookups. */
class LibraryPart internal constructor(
    val categories: Map<Long, Category>,
    val exercises: Map<Long, Exercise>,
    val goals: List<ExerciseGoal>,
    val routines: List<Routine>,
    val workoutOrigins: Map<String, WorkoutOrigin>
) {
    val routinesById: Map<Long, Routine> = routines.associateBy { it.id }
    val goalsByExercise: Map<Long, List<ExerciseGoal>> = goals.groupBy { it.exerciseId }
    val categoriesSorted: List<Category> = categories.values.sortedWith(compareBy({ it.sortOrder }, { it.name.lowercase() }))
    val exercisesSorted: List<Exercise> = exercises.values.sortedBy { it.name.lowercase() }
    val favouriteExercises: List<Exercise> = exercisesSorted.filter { it.favourite }
}

/** The sets area, in date then log order, with the lookups built from it. */
class SetPart internal constructor(val sets: List<SetRow>, val countWarmups: Boolean) {
    val setsByDate: Map<String, List<SetRow>> = sets.groupBy { it.date }
    val setsByExercise: Map<Long, List<SetRow>> = sets.groupBy { it.exerciseId }
    val statSets: List<SetRow> = if (countWarmups) sets else sets.filter { !it.isWarmup }
    val statSetsByExercise: Map<Long, List<SetRow>> = statSets.groupBy { it.exerciseId }
    val lastUsedByExercise: Map<Long, String> = setsByExercise.mapValues { e -> e.value.last().date }
    val workoutsByExercise: Map<Long, Int> = setsByExercise.mapValues { e -> e.value.distinctBy { it.date }.size }

    /** The same sets counted under another warm-up setting (#43): rebuilt from memory, never re-read. */
    internal fun withWarmups(count: Boolean): SetPart = if (count == countWarmups) this else SetPart(sets, count)
}

/** The notes area: workout comments and times, and exercise comments. */
class NotesPart internal constructor(
    val workoutComments: Map<String, List<String>>,
    val workoutTimes: Map<String, List<WorkoutTime>>,
    val exerciseComments: Map<String, Map<Long, String>>
)

/**
 * The body area: measurement definitions and values, with the lists the body screens use. Values in a weight unit
 * are shown in [weightUnit] (#117) and lengths in [lengthUnit] (#7): [rawDefs] and [rawRecords] are as stored, the
 * rest is converted.
 */
class BodyPart internal constructor(
    internal val rawDefs: List<MeasurementDef>,
    internal val rawRecords: List<MRecord>,
    val weightUnit: String,
    val lengthUnit: String = LengthUnits.CM
) {
    val measurementDefs: List<MeasurementDef> = rawDefs.map { MeasureUnits.shown(it, weightUnit, lengthUnit) }
    // A measurement's own display unit (#7) applies to its values too.
    private val overrides: Map<String, String> = rawDefs.mapNotNull { d -> d.displayUnit?.let { d.name to it } }.toMap()
    val records: List<MRecord> = rawRecords.map { MeasureUnits.shown(it, weightUnit, lengthUnit, overrides[it.name]) }

    /** The same values shown in other units: converted from memory, never re-read. */
    internal fun withUnits(weight: String, length: String): BodyPart =
        if (weight == weightUnit && length == lengthUnit) this else BodyPart(rawDefs, rawRecords, weight, length)

    val recordsByDate: Map<String, List<MRecord>> = records.groupBy { it.date }
    val recordsByName: Map<String, List<MRecord>> =
        records.groupBy { it.name }.mapValues { e -> e.value.sortedWith(compareBy({ it.date }, { it.time })) }
    val allMeasurements: List<MeasurementDef> = run {
        val defs = measurementDefs.associateBy { it.name }
        (measurementDefs.map { it.name } + recordsByName.keys).distinct().map { name ->
            defs[name] ?: MeasurementDef(name, recordsByName[name]?.firstOrNull()?.unit ?: "", 999, 0, 0.0, true)
        }.sortedWith(compareBy({ it.sortOrder }, { it.name }))
    }
    val usedMeasurements: List<MeasurementDef> = allMeasurements.filter { m ->
        m.enabled && (m.custom || recordsByName.containsKey(m.name))
    }
    val customMetrics: List<MeasurementDef> = measurementDefs.filter { it.custom }.sortedBy { it.name.lowercase() }
    val fitNotesMeasurementNames: List<String> = measurementDefs.filter { !it.custom }.map { it.name }.sortedBy { it.lowercase() }
    val bodyweightName: String? = usedMeasurements.firstOrNull { it.name.equals("Bodyweight", true) || it.name.equals("Body Weight", true) }?.name
        ?: usedMeasurements.firstOrNull()?.name
}

/** The photos area, with the dated, undated and needs-review lists. */
class PhotoPart internal constructor(val photos: List<Photo>) {
    val datedPhotos: List<Photo> = photos.filter { it.date != null }
        .sortedWith(compareBy({ it.date }, { it.takenAt ?: "" }, { it.id }))
    val photosByDate: Map<String, List<Photo>> = datedPhotos.groupBy { it.date!! }
    val photosById: Map<Long, Photo> = photos.associateBy { it.id }
    val undatedPhotos: List<Photo> = photos.filter { it.date == null }
    val reviewPhotos: List<Photo> = photos.filter { DateSources.needsReview(it.dateSource) }
}

/**
 * Everything the UI needs, held in memory. It's made of one part per [Area] (#60): a write rebuilds only the parts it
 * changed and shares the rest with the previous snapshot, so saving a set never re-reads photos or body values.
 */
class Snapshot internal constructor(
    internal val library: LibraryPart,
    internal val setPart: SetPart,
    internal val notes: NotesPart,
    internal val body: BodyPart,
    internal val photoPart: PhotoPart,
    val weightUnit: String,
    val photoDir: File,
    /** The first day of the week, 1 = Monday … 7 = Sunday (#7). */
    val weekStart: Int = 1,
    /** The global distance unit (#7); an exercise may have its own, see [distanceUnit]. */
    val globalDistanceUnit: String = DistanceUnits.KM
) {
    /** A snapshot with its sets replaced, for a write that changed only those. */
    internal fun replacing(setPart: SetPart): Snapshot =
        Snapshot(library, setPart, notes, body, photoPart, weightUnit, photoDir, weekStart, globalDistanceUnit)

    /** Body lengths are shown in this unit (#7). */
    val lengthUnit: String get() = body.lengthUnit

    /** The unit [exerciseId]'s distances are logged and shown in: its own, or the global one (#7). */
    fun distanceUnit(exerciseId: Long): String = exercises[exerciseId]?.distanceUnit ?: globalDistanceUnit

    /**
     * The unit [exerciseId]'s weights are shown and typed in: its own, or the global one (#7). Screens about one
     * exercise use this and the `weight` / `toKg` / `fmtWeight` overloads that take its id; totals across exercises
     * use the global [weightUnit].
     */
    fun weightUnitOf(exerciseId: Long?): String = exerciseId?.let { exercises[it]?.weightUnit } ?: weightUnit

    fun weight(kg: Double, exerciseId: Long?): Double = WeightUnits.convert(kg, "kg", weightUnitOf(exerciseId))

    fun toKg(shown: Double, exerciseId: Long?): Double = WeightUnits.convert(shown, weightUnitOf(exerciseId), "kg")

    fun fmtWeight(kg: Double, exerciseId: Long?): String = fmtNum(weight(kg, exerciseId), 2)

    val categories: Map<Long, Category> get() = library.categories
    val exercises: Map<Long, Exercise> get() = library.exercises
    /** Exercise goals (#25), in each exercise's order. */
    val goals: List<ExerciseGoal> get() = library.goals
    /** The user's workouts (#106, FitNotes's routines): named days of exercises, in the user's order. */
    val routines: List<Routine> get() = library.routines
    /** Which workout and day each logged date was started from, by date (#21, #106). */
    val workoutOrigins: Map<String, WorkoutOrigin> get() = library.workoutOrigins
    val routinesById: Map<Long, Routine> get() = library.routinesById
    val goalsByExercise: Map<Long, List<ExerciseGoal>> get() = library.goalsByExercise

    // ---- Exercise library (#13) ----
    /** Every category, in the order the library shows them. */
    val categoriesSorted: List<Category> get() = library.categoriesSorted
    /** Every exercise in the library, whether or not anything has been logged for it, by name. */
    val exercisesSorted: List<Exercise> get() = library.exercisesSorted
    val favouriteExercises: List<Exercise> get() = library.favouriteExercises

    val sets: List<SetRow> get() = setPart.sets
    /** Count warm-up sets in records and statistics (#43, a setting; off by default). */
    val countWarmups: Boolean get() = setPart.countWarmups
    val setsByDate: Map<String, List<SetRow>> get() = setPart.setsByDate
    val setsByExercise: Map<Long, List<SetRow>> get() = setPart.setsByExercise

    /**
     * The sets that count for records, estimated maxes, graphs and analysis: every set, or every set except warm-ups
     * unless the setting counts them (#43). Lists and history still show every set.
     */
    val statSets: List<SetRow> get() = setPart.statSets
    val statSetsByExercise: Map<Long, List<SetRow>> get() = setPart.statSetsByExercise
    /** Last date each exercise was logged (sets are loaded in date order). */
    val lastUsedByExercise: Map<Long, String> get() = setPart.lastUsedByExercise
    /** Number of separate days each exercise was logged. */
    val workoutsByExercise: Map<Long, Int> get() = setPart.workoutsByExercise

    val workoutComments: Map<String, List<String>> get() = notes.workoutComments
    val workoutTimes: Map<String, List<WorkoutTime>> get() = notes.workoutTimes
    /** Exercise comments (#107): date to exercise id to its comment in that day's workout. */
    val exerciseComments: Map<String, Map<Long, String>> get() = notes.exerciseComments

    val measurementDefs: List<MeasurementDef> get() = body.measurementDefs
    val records: List<MRecord> get() = body.records
    val recordsByDate: Map<String, List<MRecord>> get() = body.recordsByDate
    /** Records per measurement, sorted by date then time. */
    val recordsByName: Map<String, List<MRecord>> get() = body.recordsByName
    /** Every measurement: each definition and each name seen only in records, in the user's order (#88). */
    val allMeasurements: List<MeasurementDef> get() = body.allMeasurements
    /**
     * Measurements that have at least one record, plus FitLens's own (custom and standard), in the user's order.
     * Those switched off on the Measurements screen are left out everywhere they'd be shown (#27).
     */
    val usedMeasurements: List<MeasurementDef> get() = body.usedMeasurements
    val customMetrics: List<MeasurementDef> get() = body.customMetrics
    /** FitNotes measurement names a custom metric can be linked to. */
    val fitNotesMeasurementNames: List<String> get() = body.fitNotesMeasurementNames
    val bodyweightName: String? get() = body.bodyweightName

    val photos: List<Photo> get() = photoPart.photos
    val datedPhotos: List<Photo> get() = photoPart.datedPhotos
    val photosByDate: Map<String, List<Photo>> get() = photoPart.photosByDate
    val photosById: Map<Long, Photo> get() = photoPart.photosById
    val undatedPhotos: List<Photo> get() = photoPart.undatedPhotos
    val reviewPhotos: List<Photo> get() = photoPart.reviewPhotos

    /** Every date that has anything on it, newest first. */
    val allDates: List<String> =
        (setsByDate.keys + recordsByDate.keys + photosByDate.keys + workoutComments.keys)
            .toSortedSet().toList().reversed()

    fun photoFile(p: Photo): File = File(photoDir, p.file)

    fun weight(kg: Double): Double = if (weightUnit == "lbs") kg / WeightUnits.KG_PER_LB else kg

    /** The inverse of [weight]: turns a number the user typed in their unit back into the kilograms we store. */
    fun toKg(shown: Double): Double = if (weightUnit == "lbs") shown * WeightUnits.KG_PER_LB else shown

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

/** The order sets are held in, the same as `ORDER BY date, position, id` (#70). */
internal val SET_ORDER: Comparator<SetRow> = compareBy<SetRow>({ it.date }, { it.position }, { it.id })

/**
 * Replaces the sets matching [replaced] in [old] with [fresh], the same sets as just re-read from the database, and
 * keeps every other set (#60). Pure, so it's unit tested.
 */
internal fun mergeSets(old: List<SetRow>, fresh: List<SetRow>, replaced: (SetRow) -> Boolean): List<SetRow> {
    val out = ArrayList<SetRow>(old.size + fresh.size)
    old.filterNotTo(out, replaced)
    out.addAll(fresh)
    out.sortWith(SET_ORDER)
    return out
}

object Store {
    lateinit var db: Db
        private set
    lateinit var photoDir: File
        private set
    private val _snapshot = MutableStateFlow<Snapshot?>(null)
    val snapshot: StateFlow<Snapshot?> = _snapshot
    /** One snapshot update at a time, so two writes finishing together can't drop each other's change (#60). */
    private val lock = Mutex()

    fun init(context: Context) {
        db = Db(context.applicationContext)
        photoDir = File(context.filesDir, "photos").apply { mkdirs() }
    }

    /** Re-reads everything, preferences included. For start-up, imports and restores. */
    suspend fun reload() = withContext(Dispatchers.IO) {
        Settings.reloadPortable()
        lock.withLock { _snapshot.value = build(null, Area.ALL) }
    }

    /**
     * Re-reads only [areas] and keeps the rest of the snapshot (#60). With no areas it only applies the current
     * preferences (weight unit, week start, counting warm-ups) without reading the database.
     */
    suspend fun refresh(vararg areas: Area) = withContext(Dispatchers.IO) {
        lock.withLock { _snapshot.value = build(_snapshot.value, areas.toSet()) }
    }

    /**
     * After a small set write (#60): re-reads only the sets of [exerciseIds] and the sets on [dates], and keeps every
     * other set as it was. A set that moved to another exercise needs both exercises named.
     */
    suspend fun refreshSets(exerciseIds: Collection<Long> = emptyList(), dates: Collection<String> = emptyList()) =
        withContext(Dispatchers.IO) {
            lock.withLock<Unit> {
                val old = _snapshot.value
                val ex = exerciseIds.toSet()
                val ds = dates.map { it.take(10) }.toSet()
                val clauses = ArrayList<String>()
                if (ex.isNotEmpty()) clauses += "exercise_id IN (${ex.joinToString(",")})"
                if (ds.isNotEmpty()) clauses += "substr(date, 1, 10) IN (${ds.joinToString(",") { "?" }})"
                if (old == null) {
                    _snapshot.value = build(null, Area.ALL)
                } else if (clauses.isNotEmpty()) {
                    val fresh = loadSets(db.readableDatabase, clauses.joinToString(" OR "), ds.toTypedArray())
                    val merged = mergeSets(old.sets, fresh) { it.exerciseId in ex || it.date.take(10) in ds }
                    _snapshot.value = old.replacing(SetPart(merged, Settings.currentPortable().warmupsCount))
                }
            }
        }

    /** Builds a snapshot, reading [areas] and reusing every other part of [old] (reading everything when it's null). */
    private fun build(old: Snapshot?, areas: Set<Area>): Snapshot {
        val r = db.readableDatabase
        val prefs = Settings.currentPortable()
        // Weekly analysis follows the week-start setting (#7).
        Analysis.weekStart = java.time.DayOfWeek.of(prefs.weekStart)
        return Snapshot(
            library = if (old == null || Area.LIBRARY in areas) loadLibrary(r) else old.library,
            setPart = if (old == null || Area.SETS in areas) SetPart(loadSets(r, null, emptyArray()), prefs.warmupsCount)
                else old.setPart.withWarmups(prefs.warmupsCount),
            notes = if (old == null || Area.NOTES in areas) loadNotes(r) else old.notes,
            body = (if (old == null || Area.BODY in areas) loadBody(r, prefs.weightUnit) else old.body)
                .withUnits(prefs.weightUnit, prefs.lengthUnit),
            photoPart = if (old == null || Area.PHOTOS in areas) loadPhotos(r) else old.photoPart,
            weightUnit = prefs.weightUnit,
            photoDir = photoDir,
            weekStart = prefs.weekStart,
            globalDistanceUnit = prefs.distanceUnit
        )
    }

    private fun loadLibrary(r: SQLiteDatabase): LibraryPart {
        val categories = HashMap<Long, Category>()
        r.rawQuery("SELECT id, name, colour, sort_order, source FROM category", null).use { c ->
            while (c.moveToNext()) categories[c.lng(0)] = Category(c.lng(0), c.strOr(1), c.int(2), c.int(3), c.strOr(4, Sources.FITLENS))
        }
        val exercises = HashMap<Long, Exercise>()
        r.rawQuery("SELECT id, name, category_id, type, notes, source, favourite, weight_step, default_graph, rest_seconds, distance_unit, weight_unit FROM exercise", null).use { c ->
            while (c.moveToNext()) exercises[c.lng(0)] =
                Exercise(
                    c.lng(0), c.strOr(1), c.lng(2), c.int(3), c.str(4), c.strOr(5, Sources.FITLENS), c.int(6) != 0,
                    if (c.isNull(7)) null else c.getDouble(7), if (c.isNull(8)) -1 else c.getInt(8),
                    if (c.isNull(9)) null else c.getInt(9), DistanceUnits.of(c.str(10)), WeightUnits.of(c.str(11))
                )
        }
        val goals = ArrayList<ExerciseGoal>()
        r.rawQuery("SELECT id, exercise_id, kind, target, sort_order FROM exercise_goal ORDER BY exercise_id, sort_order, id", null).use { c ->
            while (c.moveToNext()) goals.add(ExerciseGoal(c.lng(0), c.lng(1), c.int(2), c.dbl(3), c.int(4)))
        }
        return LibraryPart(categories, exercises, goals, Routines.load(r), Routines.loadOrigins(r))
    }

    /** The sets matching [where] (every set when it's null), in [SET_ORDER]. */
    private fun loadSets(r: SQLiteDatabase, where: String?, args: Array<String>): List<SetRow> {
        val sets = ArrayList<SetRow>()
        r.rawQuery(
            "SELECT id, exercise_id, date, weight, reps, distance, duration, is_pr, comment, source, set_type, rpe, position, superset, done " +
                "FROM workout_set " + (if (where != null) "WHERE $where " else "") + "ORDER BY date, position, id",
            args
        ).use { c ->
            while (c.moveToNext()) sets.add(
                SetRow(
                    c.lng(0), c.lng(1), c.strOr(2), c.dbl(3), c.int(4), c.dbl(5), c.int(6), c.int(7) != 0, c.str(8),
                    c.strOr(9, Sources.FITLENS), c.int(10), if (c.isNull(11)) null else c.getDouble(11), c.lng(12), c.int(13), c.int(14) != 0
                )
            )
        }
        return sets
    }

    private fun loadNotes(r: SQLiteDatabase): NotesPart {
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
        val exerciseComments = HashMap<String, HashMap<Long, String>>()
        r.rawQuery("SELECT date, exercise_id, comment FROM exercise_comment", null).use { c ->
            while (c.moveToNext()) exerciseComments.getOrPut(c.strOr(0)) { HashMap() }[c.lng(1)] = c.strOr(2)
        }
        return NotesPart(comments, times, exerciseComments)
    }

    private fun loadBody(r: SQLiteDatabase, weightUnit: String): BodyPart {
        val defs = ArrayList<MeasurementDef>()
        r.rawQuery("SELECT name, unit, sort_order, goal_type, goal_value, enabled, custom, link, display_unit FROM measurement ORDER BY sort_order, name", null).use { c ->
            while (c.moveToNext()) defs.add(
                MeasurementDef(c.strOr(0), c.strOr(1), c.int(2), c.int(3), c.dbl(4), c.int(5) != 0, c.int(6) != 0, c.str(7), c.str(8))
            )
        }
        val records = ArrayList<MRecord>()
        r.rawQuery("SELECT id, name, unit, date, time, value, comment, source FROM mrecord ORDER BY date, time", null).use { c ->
            while (c.moveToNext()) records.add(
                MRecord(c.lng(0), c.strOr(1), c.strOr(2), c.strOr(3), c.strOr(4), c.dbl(5), c.str(6), c.strOr(7))
            )
        }
        return BodyPart(defs, records, weightUnit)
    }

    private fun loadPhotos(r: SQLiteDatabase): PhotoPart {
        val photos = ArrayList<Photo>()
        r.rawQuery("SELECT id, file, date, taken_at, date_source, pose, note, original_name FROM photo ORDER BY date, taken_at, id", null).use { c ->
            while (c.moveToNext()) photos.add(
                Photo(c.lng(0), c.strOr(1), c.str(2), c.str(3), c.strOr(4, DateSources.NONE), c.strOr(5), c.str(6), c.str(7))
            )
        }
        return PhotoPart(photos)
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
        refresh(Area.PHOTOS)
    }

    /** Accept the automatically detected date (removes the "needs review" flag). */
    suspend fun confirmPhotoDates(ids: Collection<Long>) = withContext(Dispatchers.IO) {
        val w = db.writableDatabase
        ids.forEach { id ->
            w.execSQL("UPDATE photo SET date_source='${DateSources.MANUAL}' WHERE id=? AND date IS NOT NULL", arrayOf<Any>(id))
        }
        refresh(Area.PHOTOS)
    }

    suspend fun setPhotoPose(ids: Collection<Long>, pose: String) = withContext(Dispatchers.IO) {
        val w = db.writableDatabase
        ids.forEach { id ->
            val cv = ContentValues().apply { put("pose", pose) }
            w.update("photo", cv, "id=?", arrayOf(id.toString()))
        }
        refresh(Area.PHOTOS)
    }

    suspend fun setPhotoNote(id: Long, note: String) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply { put("note", note) }
        db.writableDatabase.update("photo", cv, "id=?", arrayOf(id.toString()))
        refresh(Area.PHOTOS)
    }

    suspend fun deletePhotos(ids: Collection<Long>) = withContext(Dispatchers.IO) {
        val w = db.writableDatabase
        ids.forEach { id ->
            w.rawQuery("SELECT file FROM photo WHERE id=?", arrayOf(id.toString())).use { c ->
                if (c.moveToFirst()) File(photoDir, c.getString(0)).delete()
            }
            w.delete("photo", "id=?", arrayOf(id.toString()))
        }
        refresh(Area.PHOTOS)
    }

    // ---------- Manual measurements ----------

    suspend fun addManualRecord(name: String, unit: String, date: String, time: String, value: Double, comment: String?) =
        withContext(Dispatchers.IO) {
            val w = db.writableDatabase
            // A value typed in the display unit is stored in the unit the measurement already uses (#117).
            val stored = loadDef(w, name)?.unit?.takeIf { MeasureUnits.sameKind(it, unit) }
            addRecordRow(w, name, stored ?: unit, date, time, MeasureUnits.convert(value, unit, stored ?: unit), comment)
            refresh(Area.BODY)
        }

    private fun addRecordRow(
        w: SQLiteDatabase, name: String, unit: String, date: String, time: String, value: Double, comment: String?
    ) {
        val def = ContentValues().apply { put("name", name); put("unit", unit); put("sort_order", 999) }
        w.insertWithOnConflict("measurement", null, def, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE)
        val cv = ContentValues().apply {
            put("name", name); put("unit", unit); put("date", date); put("time", time)
            put("value", value); put("comment", comment); put("source", "manual")
        }
        w.insert("mrecord", null, cv)
    }

    /** Sets a measurement's goal (#27), and marks it so a FitNotes import keeps the user's choice. */
    suspend fun setMeasurementGoal(name: String, unit: String, type: Int, value: Double) = withContext(Dispatchers.IO) {
        val w = db.writableDatabase
        ensureMeasurement(w, name, unit, 999)
        // The goal is typed in the display unit and kept in the measurement's own (#117).
        val stored = loadDef(w, name)?.unit
        w.update("measurement", ContentValues().apply {
            put("goal_type", type); put("goal_value", MeasureUnits.convert(value, unit, stored)); put("edited", 1)
        }, "name=?", arrayOf(name))
        refresh(Area.BODY)
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
        refresh(Area.BODY)
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
            // A weight metric keeps the unit its values are stored in: the editor shows it in the display unit, and
            // saving it there must not relabel kilograms as pounds (#117).
            val keptUnit = (old ?: existing)?.unit?.takeIf { MeasureUnits.sameKind(it, unit) } ?: unit
            w.insertWithOnConflict("measurement", null, ContentValues().apply {
                put("name", name); put("unit", keptUnit); put("sort_order", existing?.sortOrder ?: old?.sortOrder ?: 900)
                put("goal_type", existing?.goalType ?: 0); put("goal_value", existing?.goalValue ?: 0.0)
                put("enabled", 1); put("custom", 1); put("link", link?.takeIf { it.isNotBlank() })
                // Keeps the metric's own display unit (#7) while it's still the same kind of unit.
                put("display_unit", (existing ?: old)?.displayUnit?.takeIf { MeasureUnits.sameKind(it, keptUnit) })
            }, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
            val key = (link?.takeIf { it.isNotBlank() } ?: name).trim().lowercase()
            w.execSQL(
                "UPDATE mrecord SET name=? WHERE source IN ('fitnotes','csv') AND lower(trim(name))=?",
                arrayOf(name, key)
            )
            if (keptUnit.isBlank()) {
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
        refresh(Area.BODY)
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
        refresh(Area.BODY)
    }

    /**
     * Shows [name] in its own unit (#7), or in the global weight or length unit when [unit] is null. Only a unit of the
     * same kind as the stored one is kept; the stored values never change.
     */
    suspend fun setMeasurementDisplayUnit(name: String, unit: String?) = withContext(Dispatchers.IO) {
        val w = db.writableDatabase
        // A measurement known only from its values gets its row first, in the unit those values use.
        val stored = loadDef(w, name)?.unit
            ?: w.rawQuery("SELECT unit FROM mrecord WHERE name=? AND unit<>'' LIMIT 1", arrayOf(name)).use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }?.also { ensureMeasurement(w, name, it, 999) }
        val keep = unit?.takeIf { MeasureUnits.sameKind(it, stored) }
        w.update("measurement", ContentValues().apply {
            if (keep == null) putNull("display_unit") else put("display_unit", keep)
        }, "name=?", arrayOf(name))
        refresh(Area.BODY)
    }

    /** Shows or hides a measurement (#27), and marks it so a FitNotes import keeps the choice. */
    suspend fun setMeasurementEnabled(name: String, unit: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        val w = db.writableDatabase
        ensureMeasurement(w, name, unit, 999)
        w.update("measurement", ContentValues().apply { put("enabled", if (enabled) 1 else 0); put("edited", 1) }, "name=?", arrayOf(name))
        refresh(Area.BODY)
    }

    /**
     * Adds the [StandardMeasurements] not already present (ignoring capitals) as FitLens's own measurements, after the
     * existing ones. Existing measurements aren't touched. Returns how many were added.
     */
    suspend fun addStandardMeasurements(): Int = withContext(Dispatchers.IO) {
        val w = db.writableDatabase
        val names = w.rawQuery("SELECT name FROM measurement", null).use { c ->
            val out = ArrayList<String>(); while (c.moveToNext()) out += c.strOr(0); out
        } + w.rawQuery("SELECT DISTINCT name FROM mrecord", null).use { c ->
            val out = ArrayList<String>(); while (c.moveToNext()) out += c.strOr(0); out
        }
        val add = StandardMeasurements.missing(names)
        var order = w.rawQuery("SELECT IFNULL(MAX(sort_order), 0) FROM measurement WHERE sort_order < 900", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }
        w.beginTransaction()
        try {
            add.forEach { (name, unit) ->
                order++
                w.insertWithOnConflict("measurement", null, ContentValues().apply {
                    put("name", name); put("unit", unit); put("sort_order", order)
                    put("enabled", 1); put("custom", 1); put("edited", 1)
                }, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE)
            }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
        refresh(Area.BODY)
        add.size
    }

    /** Number of values entered by hand for a measurement. */
    fun manualCount(snap: Snapshot, name: String): Int = snap.recordsByName[name]?.count { it.source == "manual" } ?: 0

    private fun loadDef(w: android.database.sqlite.SQLiteDatabase, name: String): MeasurementDef? =
        w.rawQuery("SELECT name, unit, sort_order, goal_type, goal_value, enabled, custom, link, display_unit FROM measurement WHERE name=?", arrayOf(name)).use { c ->
            if (c.moveToFirst()) MeasurementDef(c.strOr(0), c.strOr(1), c.int(2), c.int(3), c.dbl(4), c.int(5) != 0, c.int(6) != 0, c.str(7), c.str(8)) else null
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

    /** Changes a value entered by hand (#27). Imported values aren't edited: the next import would restore them. */
    suspend fun updateRecord(id: Long, date: String, time: String, value: Double, comment: String?) = withContext(Dispatchers.IO) {
        val w = db.writableDatabase
        // The value is edited in the display unit and kept in the record's own (#117).
        val stored = w.rawQuery("SELECT unit FROM mrecord WHERE id=?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
        val prefs = Settings.currentPortable()
        val override = w.rawQuery(
            "SELECT m.display_unit FROM measurement m JOIN mrecord r ON r.name = m.name WHERE r.id=?", arrayOf(id.toString())
        ).use { c -> if (c.moveToFirst()) c.getString(0) else null }
        val stores = MeasureUnits.convert(value, MeasureUnits.display(stored, prefs.weightUnit, prefs.lengthUnit, override), stored)
        w.update("mrecord", ContentValues().apply {
            put("date", date); put("time", time); put("value", stores); put("comment", comment)
        }, "id=? AND source='manual'", arrayOf(id.toString()))
        refresh(Area.BODY)
    }

    suspend fun deleteRecord(id: Long) = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("mrecord", "id=? AND source='manual'", arrayOf(id.toString()))
        refresh(Area.BODY)
    }
}
