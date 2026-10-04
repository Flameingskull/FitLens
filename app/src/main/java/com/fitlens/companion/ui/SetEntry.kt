@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.fitlens.companion.ui

import com.fitlens.companion.ui.design.Haptics
import com.fitlens.companion.ui.design.GlassOutlinedButton
import com.fitlens.companion.ui.design.GoldButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.fitlens.companion.R
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.Effort
import com.fitlens.companion.data.PortableSettings
import com.fitlens.companion.data.ExerciseTypes
import com.fitlens.companion.data.SetRow
import com.fitlens.companion.data.SetTypes
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Store
import com.fitlens.companion.data.Records
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.WorkoutDataException
import com.fitlens.companion.data.Workouts
import com.fitlens.companion.data.fmtDuration
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.DropdownPill
import com.fitlens.companion.ui.design.FitIcons
import com.fitlens.companion.ui.design.FitTabRow
import com.fitlens.companion.ui.design.FitTopBar
import com.fitlens.companion.ui.design.MenuAction
import com.fitlens.companion.ui.design.SetTypeBadge
import com.fitlens.companion.ui.design.TopBarAction
import com.fitlens.companion.ui.design.relativeDayLabel
import com.fitlens.companion.ui.design.StepperField
import com.fitlens.companion.ui.design.SearchablePicker
import com.fitlens.companion.ui.design.ExerciseCommentRow
import com.fitlens.companion.ui.design.SetCommentSheet
import com.fitlens.companion.ui.design.SetRow as SetRowView
import kotlin.math.max
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The exercise screen (#16, laid out after FitNotes in #82): TRACK, HISTORY and GRAPH tabs for one exercise on one
 * day. Track has fields that follow the exercise type, +/- steppers, auto-fill from last time, a comment button on
 * each set (#108), and Save / Clear, or Update / Delete for a selected set, with an undo. Exercises chosen together in the library (#83)
 * arrive as a [queue] and are opened one after another.
 *
 * Deferred on purpose: drag to reorder needs a stored position that `workout_set` doesn't have yet, and the gold
 * PR trophy waits for #23 — nothing here writes `is_pr`, so an imported FitNotes flag is still the only one shown.
 */

/** Global fallback increments. Per-exercise increments are #15, the global weight setting is #7. */
private const val DEFAULT_WEIGHT_STEP = 2.5
private const val DISTANCE_STEP = 0.5
private const val DURATION_STEP = 15

private fun num(s: String): Double = s.trim().replace(',', '.').toDoubleOrNull() ?: 0.0

/** Accepts `90`, `1:30` or `1:02:03`. */
private fun parseDuration(s: String): Int {
    val t = s.trim()
    if (t.isEmpty()) return 0
    val parts = t.split(":")
    return try {
        when (parts.size) {
            1 -> num(parts[0]).toInt()
            2 -> parts[0].trim().toInt() * 60 + parts[1].trim().toInt()
            else -> parts[0].trim().toInt() * 3600 + parts[1].trim().toInt() * 60 + parts[2].trim().toInt()
        }
    } catch (e: NumberFormatException) {
        0
    }
}

/**
 * The rest after a set (#138), first match wins: the set's own prescribed rest, the rest its logged workout prescribes
 * for the exercise, the exercise's own rest (#15), then the global one. After the exercise's [last] set, the workout's
 * rest before the next exercise comes first, when it has one.
 */
internal fun restFor(snap: Snapshot, date: String, exerciseId: Long, setId: Long? = null, last: Boolean = false): Int {
    val planned = snap.workoutRests[date.take(10)]?.get(exerciseId)
    if (last) planned?.restAfterSeconds?.let { return it }
    val own = setId?.let { id -> snap.setsByExercise[exerciseId]?.firstOrNull { it.id == id }?.restSeconds }
    return own ?: planned?.restSeconds ?: snap.exercises[exerciseId]?.restSeconds ?: Settings.currentPortable().restSeconds
}

/**
 * Starts the rest timer after a set, when Settings → Rest timer starts it automatically (#20, #129): on saving a set,
 * and on ticking one off as done. In a superset only the round's last exercise starts it (#18). A tick straight after
 * saving the same set doesn't restart a rest that began moments ago. The length comes from [restFor] (#138): ticking
 * the exercise's [last] set uses the workout's rest before the next exercise, which keeps counting as it opens.
 */
internal fun startRestAfterSet(
    context: android.content.Context,
    snap: Snapshot,
    date: String,
    exerciseId: Long,
    fromSave: Boolean = false,
    setId: Long? = null,
    last: Boolean = false
) {
    val p = Settings.currentPortable()
    if (!p.restAutoStart) return
    if (!fromSave) {
        val members = supersetMembers(snap, date, supersetOf(snap, date, exerciseId))
        if (members.size > 1 && members.indexOf(exerciseId) != members.lastIndex) return
        val st = RestTimer.state.value
        val startedAt = st.endAt - st.total * 1000L
        if (st.active && !st.paused && System.currentTimeMillis() - startedAt < 20_000L) return
    }
    RestTimer.start(context, restFor(snap, date, exerciseId, setId, last))
}

/**
 * The exercise's comments from workouts before [date], newest first, as (day, text), at most [limit] (owner,
 * 2026-10-03: exercise comments are detailed notes to use later).
 */
internal fun earlierExerciseComments(snap: Snapshot, exerciseId: Long, date: String, limit: Int): List<Pair<String, String>> =
    snap.exerciseComments.entries
        .filter { (d, m) -> d < date.take(10) && !m[exerciseId].isNullOrBlank() }
        .sortedByDescending { it.key }
        .take(limit)
        .map { (d, m) -> Dates.medium(d) to m.getValue(exerciseId) }

/** How long the screen waits after an exercise's last set is ticked before moving on (#136, owner: about 1.5 s). */
private const val ADVANCE_DELAY_MS = 1_500L

/**
 * Moves on once [exerciseId]'s last set ([setId]) is ticked (#136, owner decision 2026-10-02). A short message with
 * Undo shows at once; about 1.5 s later the next exercise of the day opens in place, in the workout's order with
 * supersets kept together ([displayOrder]). Undo unticks the set and cancels the switch, or comes back if it already
 * happened. After the last exercise the day log opens with "Workout complete", offering to stop a running workout
 * timer.
 */
private fun autoAdvance(
    res: android.content.res.Resources, snap: Snapshot, nav: Nav, date: String, exerciseId: Long, setId: Long, queue: List<Long>
) {
    val name = snap.exercises[exerciseId]?.name ?: res.getString(R.string.drawer_exercise_fallback)
    val order = displayOrder(snap, date)
    val next = order.getOrNull(order.indexOf(exerciseId) + 1)
    // Only move while this exercise is still on top: the user may have gone elsewhere in the meantime.
    fun stillHere() = (nav.top as? Screen.SetEntry)?.let { it.date == date && it.exerciseId == exerciseId } == true
    if (next != null) {
        var switched = false
        val pending = AppScope.scope.launch {
            delay(ADVANCE_DELAY_MS)
            if (stillHere()) {
                nav.replace(Screen.SetEntry(date, next, queue))
                switched = true
            }
        }
        UiEvents.show(
            res.getString(R.string.set_advance_next, name, snap.exercises[next]?.name ?: res.getString(R.string.set_advance_next_fallback)),
            res.getString(R.string.undo)
        ) {
            pending.cancel()
            AppScope.scope.launch { Workouts.setDone(setId, false) }
            val top = nav.top
            if (switched && top is Screen.SetEntry && top.date == date && top.exerciseId == next) {
                nav.replace(Screen.SetEntry(date, exerciseId, queue))
            }
        }
    } else {
        AppScope.scope.launch {
            delay(ADVANCE_DELAY_MS)
            if (!stillHere()) return@launch
            nav.home(date)
            val running = Store.snapshot.value?.let { WorkoutClock.running(it, date) }
            if (running != null) {
                UiEvents.show(res.getString(R.string.set_workout_complete), res.getString(R.string.day_stop_timer)) { WorkoutClock.stop(res, date, running) }
            } else {
                UiEvents.show(res.getString(R.string.set_workout_complete), res.getString(R.string.undo)) {
                    AppScope.scope.launch { Workouts.setDone(setId, false) }
                    nav.push(Screen.SetEntry(date, exerciseId, queue))
                }
            }
        }
    }
}

@Composable
fun SetEntryScreen(
    snap: Snapshot,
    nav: Nav,
    date: String,
    exerciseId: Long,
    queue: List<Long> = emptyList(),
    page: Int = 0,
    /** A set to open selected, from History (#22). */
    selectSet: Long? = null
) {
    val ex = snap.exercises[exerciseId]
    val allSets = snap.setsByExercise[exerciseId] ?: emptyList()
    val sets = remember(snap, date, exerciseId) { allSets.filter { it.date == date } }
    val type = ex?.type ?: ExerciseTypes.WEIGHT_REPS

    // Which fields to show. The exercise type decides, but anything already logged for this exercise is always
    // editable, so an imported exercise with an unexpected type can still be corrected.
    // A user-defined type's own metric (#14), such as jump height in cm.
    val metricDef = ExerciseTypes.metricOf(type)
    val showMetric = metricDef != null || allSets.any { it.metric != null }
    val showDistance = ExerciseTypes.usesDistance(type) || allSets.any { it.distance > 0 }
    val showDuration = ExerciseTypes.usesDuration(type) || allSets.any { it.durationSec > 0 }
    val showReps = ExerciseTypes.usesReps(type) || allSets.any { it.reps > 0 }
    val showWeight = ExerciseTypes.usesWeight(type) || allSets.any { it.weightKg != 0.0 } ||
        (!showDistance && !showDuration && !showReps && !showMetric)

    val prefs by Settings.portable.collectAsState()

    // Auto-fill: what was last logged today, otherwise the first set of the previous workout for this exercise.
    // "Leave empty" in Settings → Workout & logging turns it off (#97).
    val fillFromLast = prefs.autofillSource != PortableSettings.AUTOFILL_EMPTY
    val template = remember(snap, date, exerciseId, fillFromLast) {
        if (!fillFromLast) null else {
            val today = allSets.lastOrNull { it.date == date }
            val previousDay = allSets.filter { it.date < date }.maxByOrNull { it.date }?.date
            today ?: previousDay?.let { d -> allSets.firstOrNull { it.date == d } }
        }
    }

    var selected by remember(date, exerciseId) { mutableStateOf(selectSet) }
    var weight by remember(date, exerciseId) { mutableStateOf("") }
    var reps by remember(date, exerciseId) { mutableStateOf("") }
    var distance by remember(date, exerciseId) { mutableStateOf("") }
    var duration by remember(date, exerciseId) { mutableStateOf("") }
    var metric by remember(date, exerciseId) { mutableStateOf("") }
    // Set type (#43): new sets start as working sets; editing a set shows its own type.
    var setType by remember(date, exerciseId) { mutableIntStateOf(SetTypes.WORKING) }
    // Effort (#44), always held as RPE; null means not recorded.
    var rpe by remember(date, exerciseId) { mutableStateOf<Double?>(null) }
    // The exact kilograms the weight field was filled from, and the text it was filled with. Weights are stored in
    // kilograms but shown rounded in the user's unit, so converting the displayed text back on every save quietly
    // rewrote the stored value for anyone using pounds (#75). Only convert when the text has actually been edited.
    var loadedWeightText by remember(date, exerciseId) { mutableStateOf("") }
    var loadedWeightKg by remember(date, exerciseId) { mutableStateOf<Double?>(null) }
    var deleting by remember { mutableStateOf<SetRow?>(null) }
    var editExercise by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var calculator by remember { mutableStateOf<String?>(null) }
    // The set whose Comment box is open (#108).
    var commenting by remember { mutableStateOf<SetRow?>(null) }
    var editingExerciseComment by remember { mutableStateOf(false) }
    // Add to superset from the workout drawer (#124).
    var grouping by remember { mutableStateOf(false) }

    // The global step from Settings → Unit System (#7) is stored in kg; the field works in the display unit.
    // This exercise's own step comes first (#15), then the global one.
    // An exercise in its own weight unit (#7) skips the global step, which is sized for the global unit (2.5 kg would
    // step 5.51 lbs), and uses 2.5 in its unit unless it has its own step.
    val globalStepKg = prefs.weightIncrementKg?.takeIf { snap.weightUnitOf(exerciseId) == snap.weightUnit }
    val weightStep = (ex?.weightStepKg ?: globalStepKg)?.let { snap.weight(it, exerciseId) } ?: DEFAULT_WEIGHT_STEP

    // "Keep screen on" while logging, switched in Settings → Workout & logging (#97).
    val view = LocalView.current
    val keepOn = prefs.keepScreenOn
    DisposableEffect(view, keepOn) {
        if (keepOn) view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(selected, sets, template) {
        val chosen = selected?.let { id -> sets.firstOrNull { it.id == id } }
        if (selected != null && chosen == null) {
            selected = null
        } else {
            val source = chosen ?: template
            weight = source?.weightKg?.takeIf { it != 0.0 }?.let { fmtNum(snap.weight(it, exerciseId), 2) } ?: ""
            loadedWeightText = weight
            loadedWeightKg = source?.weightKg
            reps = source?.reps?.takeIf { it > 0 }?.toString() ?: ""
            distance = source?.distance?.takeIf { it > 0 }?.let { fmtNum(it, 2) } ?: ""
            duration = source?.durationSec?.takeIf { it > 0 }?.let { fmtDuration(it) } ?: ""
            metric = source?.metric?.let { fmtNum(it, 2) } ?: ""
            setType = chosen?.setType ?: SetTypes.WORKING
            rpe = chosen?.rpe
        }
    }

    val appContext = LocalContext.current.applicationContext
    val res = LocalContext.current.resources
    var restSheet by remember { mutableStateOf(false) }

    fun save() {
        // Untouched field: keep the stored kilograms exactly as they were, rather than round-tripping the
        // two-decimal display value back through the unit conversion (#75).
        val kg = if (weight == loadedWeightText) loadedWeightKg ?: 0.0 else snap.toKg(num(weight), exerciseId)
        val r = reps.trim().toIntOrNull() ?: 0
        val dist = num(distance)
        val dur = parseDuration(duration)
        // Blank means not recorded; 0 is a real value for a custom metric (#14).
        val met = if (showMetric) metric.trim().replace(',', '.').toDoubleOrNull() else null
        if (kg == 0.0 && r == 0 && dist == 0.0 && dur == 0 && met == null) {
            UiEvents.show(res.getString(R.string.set_enter_something))
            return
        }
        val chosen = selected?.let { id -> sets.firstOrNull { it.id == id } }
        AppScope.scope.launch {
            try {
                if (chosen == null) {
                    val firstOfDay = Store.snapshot.value?.setsByDate?.get(date).isNullOrEmpty()
                    val id = Workouts.addSet(exerciseId, date, kg, r, dist, dur, null, setType = setType, rpe = rpe, metric = met)
                    // In a superset, saving a set moves on to the next exercise of the group, round-robin, as FitNotes
                    // does (#18). The rest timer then starts only after the round's last exercise (#20).
                    val members = supersetMembers(snap, date, supersetOf(snap, date, exerciseId))
                    val at = members.indexOf(exerciseId)
                    val endOfRound = members.size < 2 || at == members.lastIndex
                    if (endOfRound) startRestAfterSet(appContext, snap, date, exerciseId, fromSave = true)
                    if (members.size > 1 && at >= 0) {
                        val next = members[(at + 1) % members.size]
                        if (nav.top is Screen.SetEntry) nav.replace(Screen.SetEntry(date, next, queue))
                    }
                    // The first set of today can start the workout timer (#12), unless a time is already recorded.
                    if (firstOfDay && date == Dates.today() && Settings.currentPortable().workoutTimerAuto &&
                        Store.snapshot.value?.workoutTimes?.get(date).isNullOrEmpty()
                    ) {
                        Workouts.setWorkoutTime(date, WorkoutClock.now(), null)
                    }
                    // The PR mark was decided as the set was saved; the reloaded snapshot carries it (#23).
                    val isPr = Store.snapshot.value?.setsByExercise?.get(exerciseId)?.any { it.id == id && it.isPr } == true
                    // A saved set confirms with one pulse; a record gets its own pattern (#93).
                    if (isPr && Settings.currentPortable().celebratePrs) {
                        Haptics.record(view)
                        UiEvents.show(res.getString(R.string.set_new_pr, snap.fmtWeight(kg, exerciseId), snap.weightUnitOf(exerciseId), r))
                    } else {
                        Haptics.confirm(view)
                    }
                } else {
                    Workouts.updateSet(
                        chosen.copy(weightKg = kg, reps = r, distance = dist, durationSec = dur, setType = setType, rpe = rpe, metric = met)
                    )
                    Haptics.confirm(view)
                    // With auto-select next on, the following set of the day is selected, ready to adjust (#97).
                    val next = if (Settings.currentPortable().autoSelectNext) {
                        sets.getOrNull(sets.indexOfFirst { it.id == chosen.id } + 1)
                    } else null
                    selected = next?.id
                    UiEvents.show(res.getString(if (next == null) R.string.set_updated else R.string.set_updated_next))
                }
            } catch (e: WorkoutDataException) {
                UiEvents.show(e.message ?: res.getString(R.string.set_save_failed))
            }
        }
    }

    val pager = rememberPagerState(initialPage = page.coerceIn(0, 2), pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val next = queue.firstOrNull()?.let { snap.exercises[it] }

    fun clear() {
        selected = null
        weight = ""; reps = ""; distance = ""; duration = ""; metric = ""
        loadedWeightText = ""; loadedWeightKg = null
        setType = SetTypes.WORKING; rpe = null
    }

    // The workout drawer (#85): opened from the top bar. Its edge swipe is off while closed, so it never fights the
    // Track / History / Graph pager.
    val drawer = rememberDrawerState(DrawerValue.Closed)
    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = drawer.isOpen,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Brand.Onyx, drawerContentColor = Brand.Ivory) {
                WorkoutDrawer(
                    snap = snap,
                    date = date,
                    current = exerciseId,
                    onOpen = { id ->
                        scope.launch { drawer.close() }
                        if (id != exerciseId) nav.replace(Screen.SetEntry(date, id))
                    },
                    onAddExercise = { scope.launch { drawer.close() }; nav.push(Screen.Library(date)) },
                    onDayLog = {
                        scope.launch { drawer.close() }
                        while (!nav.atHome && nav.top !is Screen.Day) nav.pop()
                    },
                    onAddToSuperset = {
                        scope.launch { drawer.close() }
                        if (dayExercises(snap, date).any { it != exerciseId }) grouping = true
                        else UiEvents.show(res.getString(R.string.set_superset_needs_another))
                    }
                )
            }
        }
    ) {
    Column(Modifier.fillMaxSize()) {
        FitTopBar(
            title = ex?.name ?: stringResource(R.string.drawer_exercise_fallback),
            centered = false,
            // FitNotes's bar (#129): the workout drawer's ≡ where the back arrow was, so the exercise's name has room
            // beside the rest timer, records and info. Back is the phone's back.
            navigation = TopBarAction(Icons.Filled.Menu, stringResource(R.string.set_drawer_open)) { scope.launch { drawer.open() } },
            // FitNotes's order (#122): the rest timer's alarm clock (the time left while a rest runs, #109), the
            // records trophy, then the exercise's info with Edit (#110).
            trailing = {
                RestTimerButton(onOpen = { restSheet = true })
                IconButton(onClick = { nav.push(Screen.ExerciseDetail(exerciseId)) }, enabled = allSets.isNotEmpty()) {
                    Icon(FitIcons.Trophy, contentDescription = stringResource(R.string.day_ex_records))
                }
                IconButton(onClick = { showInfo = true }, enabled = ex != null) {
                    Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.set_exercise_info))
                }
            },
            overflow = listOfNotNull(
                // The calculators (#28) fill in this set's weight.
                if (showWeight) MenuAction(stringResource(R.string.set_calculator)) { calculator = "set" } else null,
                if (showWeight) MenuAction(stringResource(R.string.set_plate_calculator)) { calculator = "plate" } else null
            )
        )
        FitTabRow(
            titles = listOf(stringResource(R.string.set_tab_track), stringResource(R.string.set_tab_history), stringResource(R.string.set_tab_graph)),
            selected = pager.currentPage,
            onSelect = { i -> scope.launch { pager.animateScrollToPage(i) } }
        )
        // Where this exercise sits in its superset (#18).
        supersetMembers(snap, date, supersetOf(snap, date, exerciseId)).takeIf { it.size > 1 }?.let { members ->
            val unnamed = stringResource(R.string.drawer_exercise_fallback)
            Text(
                stringResource(R.string.day_superset_heading, supersetLetters(snap, date)[supersetOf(snap, date, exerciseId)] ?: "") + "  ·  " +
                    members.joinToString(" → ") { id -> (snap.exercises[id]?.name ?: unnamed).let { if (id == exerciseId) it.uppercase() else it } },
                style = MaterialTheme.typography.labelSmall,
                color = Brand.Gold,
                maxLines = 2,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { tab ->
        when (tab) {
        1 -> ExerciseHistoryPane(snap, nav, exerciseId)
        2 -> ExerciseGraphPane(snap, nav, exerciseId)
        else ->
        // The inputs sit straight under the tabs, as in FitNotes (#136): no top padding, and the date and notes row
        // only when it has something to show.
        LazyColumn(contentPadding = PaddingValues(top = 0.dp, bottom = 32.dp)) {
            val notes = ex?.notes?.takeIf { it.isNotBlank() }
            if (date != Dates.today() || notes != null) {
                item {
                    if (date != Dates.today()) {
                        Text(
                            Dates.long(date).uppercase(),
                            Modifier.padding(start = 16.dp, top = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = Brand.Gold
                        )
                    }
                    if (notes != null) {
                        ExerciseNotes(notes, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    }
                }
            }

            // ---------- Entry ----------
            item {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (showWeight) {
                        StepperField(
                            label = stringResource(R.string.set_field_weight, snap.weightUnitOf(exerciseId)),
                            value = weight,
                            onValue = { weight = it },
                            onStep = { dir -> weight = fmtNum(max(0.0, num(weight) + dir * weightStep), 2) }
                        )
                    }
                    if (showReps) {
                        StepperField(
                            label = stringResource(R.string.set_field_reps),
                            value = reps,
                            onValue = { reps = it },
                            onStep = { dir -> reps = max(0, (reps.trim().toIntOrNull() ?: 0) + dir).toString() },
                            keyboard = KeyboardType.Number
                        )
                    }
                    if (showDistance) {
                        StepperField(
                            label = stringResource(R.string.set_field_distance, snap.distanceUnit(exerciseId)),
                            value = distance,
                            onValue = { distance = it },
                            onStep = { dir -> distance = fmtNum(max(0.0, num(distance) + dir * DISTANCE_STEP), 2) }
                        )
                    }
                    if (showDuration) {
                        StepperField(
                            label = stringResource(R.string.set_field_time),
                            value = duration,
                            onValue = { duration = it },
                            onStep = { dir ->
                                val next = max(0, parseDuration(duration) + dir * DURATION_STEP)
                                duration = if (next == 0) "" else fmtDuration(next)
                            },
                            keyboard = KeyboardType.Text
                        )
                    }
                    if (showMetric) {
                        // The type's own metric (#14), stepping by 1.
                        val unit = metricDef?.metricUnit?.let { " ($it)" }.orEmpty()
                        StepperField(
                            label = (metricDef?.metricName ?: stringResource(R.string.set_field_value)) + unit,
                            value = metric,
                            onValue = { metric = it },
                            onStep = { dir ->
                                val now = metric.trim().replace(',', '.').toDoubleOrNull() ?: 0.0
                                metric = fmtNum(max(0.0, now + dir), 2)
                            }
                        )
                    }
                    // Set type (#43) and optional effort (#44) as two dropdowns on one row (#129), so the sets below
                    // get the screen. Warm-ups stay out of records unless Settings counts them.
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        val types = SetTypes.all
                        DropdownPill(
                            stringResource(R.string.set_type),
                            types.map { t -> SetTypes.badge(t)?.let { "$it · ${SetTypes.label(t)}" } ?: SetTypes.label(t) },
                            types.indexOf(setType).coerceAtLeast(0)
                        ) { i -> setType = types[i] }
                        Spacer(Modifier.weight(1f))
                        if (prefs.effortMode != Effort.OFF) {
                            val rir = prefs.effortMode == Effort.RIR
                            val choices: List<Pair<String, Double?>> = listOf(stringResource(if (rir) R.string.set_rir_none else R.string.set_rpe_none) to null) +
                                if (rir) Effort.rirSteps.map { r -> "RIR ${if (r >= 5) "5+" else "$r"}" to Effort.rpeFromRir(r) }
                                else Effort.rpeSteps.map { v -> "RPE ${fmtNum(v, 1)}" to v }
                            DropdownPill(
                                stringResource(if (rir) R.string.set_reps_in_reserve else R.string.set_effort),
                                choices.map { it.first },
                                choices.indexOfFirst { it.second == rpe }.coerceAtLeast(0)
                            ) { i -> rpe = choices[i].second }
                        }
                    }
                    // As in FitNotes: Save and Clear for a new set, Update and Delete for the selected one.
                    if (selected == null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GoldButton(onClick = { save() }, modifier = Modifier.weight(1f).height(48.dp)) {
                                Text(stringResource(R.string.set_save), style = MaterialTheme.typography.labelLarge)
                            }
                            GlassOutlinedButton(onClick = { clear() }, modifier = Modifier.weight(1f).height(48.dp)) { Text(stringResource(R.string.cal_clear)) }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GoldButton(onClick = { save() }, modifier = Modifier.weight(1f).height(48.dp)) { Text(stringResource(R.string.set_update)) }
                            GlassOutlinedButton(
                                onClick = { deleting = sets.firstOrNull { it.id == selected } },
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) { Text(stringResource(R.string.day_delete)) }
                        }
                        // Move the selected set within this exercise (#70).
                        val at = sets.indexOfFirst { it.id == selected }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = { selected?.let { moveSet(snap, date, it, -1) } },
                                enabled = at > 0,
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.set_move_up)) }
                            TextButton(
                                onClick = { selected?.let { moveSet(snap, date, it, 1) } },
                                enabled = at in 0 until sets.lastIndex,
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.set_move_down)) }
                        }
                        TextButton(onClick = { selected = null }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.set_new_instead))
                        }
                    }
                    // The next of the exercises chosen together in the library (#83). Exercises already in the day's
                    // logged workout are reached by auto-advance instead (#136), so the button is only for the rest.
                    if (next != null && next.id !in displayOrder(snap, date)) {
                        GlassOutlinedButton(
                            onClick = { nav.replace(Screen.SetEntry(date, next.id, queue.drop(1))) },
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Text(
                                if (queue.size > 1) pluralStringResource(R.plurals.set_next_exercise_more, queue.size - 1, next.name, queue.size - 1)
                                else stringResource(R.string.set_next_exercise, next.name),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // As in FitNotes, the sets follow the buttons with no rule between (#141).
            item { Spacer(Modifier.height(Spacing.sm)) }

            if (sets.isEmpty()) {
                item {
                    Text(
                        when {
                            !fillFromLast -> stringResource(R.string.set_none_today)
                            template == null -> stringResource(R.string.set_none_ever)
                            else -> stringResource(R.string.set_none_today_filled)
                        },
                        Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // Each value in its own labelled column (#101), chosen by the exercise type.
            val fields = setFields(snap, exerciseId, sets)
            sets.forEachIndexed { i, s ->
                item(key = "s${s.id}") {
                    val isSelected = selected == s.id
                    val marks = setMarks(s, prefs)
                    SetRowView(
                        index = i + 1,
                        summary = describeSet(res, snap, s.weightKg, s.reps, s.distance, s.durationSec, s.exerciseId, s.metric),
                        cells = setCells(snap, fields, s),
                        comment = s.comment,
                        isPr = s.isPr,
                        badge = marks.badge,
                        badgeSpoken = marks.badgeSpoken,
                        effort = marks.effort,
                        effortSpoken = marks.effortSpoken,
                        selected = isSelected,
                        onClick = { selected = if (selected == s.id) null else s.id },
                        // Each set's own comment, one tap away mid-workout (#108).
                        onComment = { commenting = s },
                        // No "Edit" hint: the comment button needs the room at 320dp, and the gold-washed, gold-edged
                        // frame already marks the selected set (#108, #112).
                        // "Mark sets complete" (#19). Every set has its done tick (#129). Ticking one off starts the
                        // rest timer when Settings starts it automatically, unless saving that set has only just
                        // started it. Ticking the last set moves on by itself (#136).
                        done = s.done,
                        onDoneChange = { on ->
                            if (on) Haptics.confirm(view)
                            if (on) startRestAfterSet(
                                appContext, snap, date, exerciseId,
                                setId = s.id, last = sets.all { it.id == s.id || it.done }
                            )
                            AppScope.scope.launch {
                                Workouts.setDone(s.id, on)
                                if (on && sets.all { it.id == s.id || it.done }) {
                                    autoAdvance(res, snap, nav, date, exerciseId, s.id, queue)
                                }
                            }
                        }
                    )
                }
            }
            // One comment for the whole exercise in today's workout (#107), under its sets.
            // The last note on this exercise from an earlier workout shows under it, to use today (owner, 2026-10-03).
            item(key = "exercise-comment") {
                ExerciseCommentRow(
                    snap.exerciseComments[date.take(10)]?.get(exerciseId),
                    onEdit = { editingExerciseComment = true },
                    previous = earlierExerciseComments(snap, exerciseId, date, 1).firstOrNull()
                )
            }
            if (sets.isNotEmpty()) {
                item {
                    val volume = sets.sumOf { it.weightKg * it.reps }
                    Text(
                        pluralStringResource(R.plurals.day_sets, sets.size, sets.size) +
                            if (volume > 0) " · " + stringResource(R.string.set_volume, fmtNum(snap.weight(volume, exerciseId), 0), snap.weightUnitOf(exerciseId)) else "",
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        }
        }
    }
    }

    deleting?.let { s ->
        ConfirmDialog(
            title = stringResource(R.string.set_delete_title),
            text = describeSet(res, snap, s.weightKg, s.reps, s.distance, s.durationSec, s.exerciseId, s.metric),
            confirm = stringResource(R.string.day_delete),
            onDismiss = { deleting = null }
        ) {
            selected = null
            AppScope.scope.launch {
                Workouts.deleteSet(s.id)
                UiEvents.show(res.getString(R.string.set_deleted), res.getString(R.string.undo)) {
                    AppScope.scope.launch {
                        try {
                            // The whole row goes back (isPr included, #69), and restoring an imported set also
                            // clears the skip rule its delete left behind (#76).
                            Workouts.addSets(listOf(s))
                        } catch (e: Exception) {
                            UiEvents.show(res.getString(R.string.day_undo_failed, e.message ?: e.javaClass.simpleName))
                        }
                    }
                }
            }
        }
    }
    if (restSheet) RestTimerSheet(ex) { restSheet = false }
    if (grouping) {
        SearchablePicker(
            title = stringResource(R.string.day_superset_title, ex?.name ?: stringResource(R.string.set_this_exercise)),
            items = exercisePickerItems(snap).filter { it.id != exerciseId && it.id in dayExercises(snap, date) },
            multiSelect = true,
            onDismiss = { grouping = false },
            onPick = { ids ->
                grouping = false
                if (ids.isNotEmpty()) AppScope.scope.launch { Workouts.groupExercises(date, ids + exerciseId) }
            }
        )
    }
    if (editingExerciseComment) {
        SetCommentSheet(
            describe = "${ex?.name ?: stringResource(R.string.drawer_exercise_fallback)} · ${relativeDayLabel(date)}",
            initial = snap.exerciseComments[date.take(10)]?.get(exerciseId),
            onSave = { text -> AppScope.scope.launch { Workouts.setExerciseComment(date, exerciseId, text) } },
            onDismiss = { editingExerciseComment = false },
            title = stringResource(R.string.day_exercise_comment),
            detailed = true,
            earlier = earlierExerciseComments(snap, exerciseId, date, 5)
        )
    }
    commenting?.let { s ->
        val number = sets.indexOfFirst { it.id == s.id } + 1
        SetCommentSheet(
            describe = stringResource(R.string.set_number, number) + " · " + describeSet(res, snap, s.weightKg, s.reps, s.distance, s.durationSec, s.exerciseId, s.metric),
            initial = s.comment,
            onSave = { text ->
                AppScope.scope.launch {
                    try {
                        Workouts.setComment(s.id, text)
                    } catch (e: WorkoutDataException) {
                        UiEvents.show(e.message ?: res.getString(R.string.set_comment_failed))
                    }
                }
            },
            onDismiss = { commenting = null }
        )
    }
    when (calculator) {
        "set" -> SetCalculatorSheet(
            snap,
            bestOneRmKg = snap.statSetsByExercise[exerciseId].orEmpty().maxOfOrNull { Records.oneRepMax(it) } ?: 0.0,
            targetText = weight,
            stepShown = weightStep,
            onUse = { w, r -> weight = w; if (r != null) reps = r.toString() },
            onDismiss = { calculator = null },
            exerciseId = exerciseId
        )
        "plate" -> PlateCalculatorSheet(snap, weight, onUse = { w -> weight = w }, onDismiss = { calculator = null }, exerciseId = exerciseId)
    }
    if (showInfo && ex != null) {
        ExerciseInfoSheet(snap, ex, weightStep, onEdit = { editExercise = true }, onDismiss = { showInfo = false })
    }
    if (editExercise && ex != null) {
        ExerciseEditorSheet(snap, existing = ex, initialCategoryId = ex.categoryId, onDismiss = { editExercise = false })
    }
}
