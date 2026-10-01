@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.fitlens.companion.ui

import com.fitlens.companion.ui.design.SearchFieldIcon

import com.fitlens.companion.ui.design.WidthBucket
import com.fitlens.companion.ui.design.currentWidthBucket
import com.fitlens.companion.ui.design.GlassOutlinedButton
import com.fitlens.companion.ui.design.GoldButton
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.Backups
import com.fitlens.companion.data.Category
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.DistanceUnits
import com.fitlens.companion.data.Exercise
import com.fitlens.companion.data.ImportSummary
import com.fitlens.companion.data.Routine
import com.fitlens.companion.data.RoutineDay
import com.fitlens.companion.data.Routines
import com.fitlens.companion.ui.design.raisedGlass
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.ExerciseTypes
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.StarterLibrary
import com.fitlens.companion.data.WorkoutDataException
import com.fitlens.companion.data.Workouts
import com.fitlens.companion.data.fmtDuration
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.ConfirmSheet
import com.fitlens.companion.ui.design.DropdownPill
import com.fitlens.companion.data.WeightUnits
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.FitTopBar
import com.fitlens.companion.ui.design.ListRowWithMenu
import com.fitlens.companion.ui.design.MenuAction
import com.fitlens.companion.ui.design.PickerItem
import com.fitlens.companion.ui.design.SearchablePicker
import com.fitlens.companion.ui.design.OverflowMenu
import com.fitlens.companion.ui.design.TopBarAction
import com.fitlens.companion.ui.design.relativeDayLabel
import kotlinx.coroutines.launch

/**
 * The exercise library (#13, redesigned in #83 after FitNotes): categories first, then a category's exercises, with a
 * search across everything. Tapping an exercise opens it for logging on the day the library was opened for; a long
 * press starts choosing several, which are then opened one after another. Every write goes through [Workouts], so a
 * later FitNotes import follows renames and respects deletions.
 */

/** Category colours offered to the user, taken from the brand palette in [Brand]. */
val CategoryPalette: List<Color> = listOf(
    Brand.Gold,
    Brand.PurpleLight,
    Brand.GoldLight,
    Brand.ImperialPurple,
    Brand.GoldDeep,
    Brand.PurpleDeep,
    Brand.Ivory,
    Brand.Muted
)

private val CategoryPaletteArgb: List<Int> = CategoryPalette.map { it.toArgb() }

/** A category's dot colour, falling back to the theme outline when it has none (0). */
@Composable
fun categoryColour(colour: Int): Color =
    if (colour == 0) MaterialTheme.colorScheme.outline else Color(colour)

/** The "Favourites" pseudo-category in the category list. Real category ids are positive; uncategorised is 0. */
private const val FAVOURITES = -1L

private val LinkPattern = Regex("https?://\\S+")

private fun countOf(n: Int, word: String) = "$n $word${if (n == 1) "" else "s"}"

// ---------------------------------------------------------------------------------------------------------
// Library screen
// ---------------------------------------------------------------------------------------------------------

/**
 * The library for [forDate] (null: today). As in FitNotes, the first view lists the categories; a category lists its
 * exercises. Search looks through every exercise. Choosing exercises replaces this screen with the exercise screen, so
 * Back returns to the day log.
 */
@Composable
fun ExerciseLibraryScreen(snap: Snapshot, nav: Nav, forDate: String?) {
    val date = forDate ?: Dates.today()
    var category by rememberSaveable { mutableStateOf<Long?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    // Exercises chosen with a long press, in the order they were ticked (#83).
    val picked = remember { mutableStateListOf<Long>() }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Exercise?>(null) }
    var deleting by remember { mutableStateOf<Exercise?>(null) }
    var merging by remember { mutableStateOf<Exercise?>(null) }
    var showCategories by remember { mutableStateOf(false) }
    var seeding by remember { mutableStateOf(false) }

    fun open(ids: List<Long>) {
        if (ids.isEmpty()) return
        nav.stack[nav.stack.lastIndex] = Screen.SetEntry(date, ids.first(), ids.drop(1))
    }
    fun back() {
        when {
            picked.isNotEmpty() -> picked.clear()
            searching -> { searching = false; query = "" }
            category != null -> category = null
            else -> nav.pop()
        }
    }
    BackHandler(enabled = picked.isNotEmpty() || searching || category != null) { back() }

    val q = query.trim()
    val listed: List<Exercise> = remember(snap, category, q, searching) {
        when {
            searching && q.isNotEmpty() -> snap.exercisesSorted.filter {
                it.name.contains(q, true) || (it.notes ?: "").contains(q, true)
            }
            category == FAVOURITES -> snap.favouriteExercises
            category != null -> snap.exercisesSorted.filter { it.categoryId == category }
            else -> emptyList()
        }
    }
    val showingExercises = category != null || (searching && q.isNotEmpty())
    // The workout switcher (#21, #106): as in FitNotes, the library's title chooses All exercises or one of your
    // workouts, and remembers the choice.
    val prefs by Settings.portable.collectAsState()
    val routine = snap.routinesById[prefs.lastRoutineId]
    val atTop = picked.isEmpty() && !searching && category == null
    val routineMode = routine != null && atTop
    val title = when {
        routineMode && routine != null -> routine.name
        picked.isNotEmpty() -> "${picked.size} selected"
        category == FAVOURITES -> "Favourites"
        category == Workouts.UNCATEGORISED -> "Uncategorised"
        else -> category?.let { snap.categories[it]?.name } ?: "Exercises"
    }

    // On wide screens (unfolded, landscape, tablets) the categories and their exercises sit side by side (#83).
    val wide = currentWidthBucket() == WidthBucket.Expanded
    val exerciseList: @Composable () -> Unit = {
        ExerciseList(
            snap = snap,
            exercises = listed,
            grouped = searching && q.isNotEmpty(),
            picked = picked,
            emptyText = if (searching && q.isNotEmpty()) "No exercise matches “$q”." else "No exercises here yet. Tap + to create one.",
            onOpen = { ex -> if (picked.isNotEmpty()) toggle(picked, ex.id) else open(listOf(ex.id)) },
            onPick = { ex -> toggle(picked, ex.id) },
            onEdit = { editing = it },
            onDetails = { nav.push(Screen.ExerciseDetail(it.id)) },
            onDelete = { deleting = it },
            onMerge = { merging = it }
        )
    }

    Column(Modifier.fillMaxSize()) {
        FitTopBar(
            title = title,
            subtitle = if (picked.isEmpty()) "For ${relativeDayLabel(date)}" else "Tap more, or add them below",
            onBack = { back() },
            backLabel = if (picked.isNotEmpty()) "Clear selection" else "Back",
            titleMenu = if (!atTop) emptyList() else buildList {
                add(MenuAction("All exercises") { Settings.updatePortable { it.copy(lastRoutineId = 0L) } })
                snap.routines.forEach { r -> add(MenuAction(r.name) { Settings.updatePortable { it.copy(lastRoutineId = r.id) } }) }
                add(MenuAction("Create new workout") { nav.push(Screen.WorkoutEditor(0L)) })
            },
            actions = if (routineMode && routine != null) listOf(
                TopBarAction(Icons.Filled.Edit, "Edit workout") { nav.push(Screen.WorkoutEditor(routine.id)) }
            ) else if (picked.isNotEmpty()) emptyList() else listOf(
                TopBarAction(Icons.Filled.Search, "Search exercises") { searching = !searching; if (!searching) query = "" },
                TopBarAction(Icons.Filled.Add, "New exercise") { creating = true }
            ),
            overflow = if (picked.isNotEmpty() || routineMode) emptyList() else listOf(
                MenuAction("Manage categories") { showCategories = true },
                MenuAction("Add starter library") { seeding = true }
            )
        )
        if (searching && picked.isEmpty()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text("Search every exercise") },
                leadingIcon = { SearchFieldIcon() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs)
            )
        }

        Box(Modifier.weight(1f)) {
            when {
                snap.exercises.isEmpty() -> EmptyState(
                    "Your library is empty",
                    "Create your own exercises, start from FitLens's starter library, or import a FitNotes backup from Settings → FitNotes import."
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        GoldButton(onClick = { seeding = true }) { Text("Add starter library") }
                        GlassOutlinedButton(onClick = { creating = true }) { Text("Create an exercise") }
                    }
                }
                routineMode && routine != null -> RoutineDayList(
                    snap, routine,
                    onOpen = { open(listOf(it)) },
                    onLogAll = { d ->
                        val toOpen = logWorkoutDay(snap, date, "${routine.name} · ${d.name}", d.exercises, routine.id, d.id)
                        // Logging a day returns to the day log, like choosing an exercise does; exercises with no sets
                        // to add open one after another instead.
                        if (toOpen.isEmpty()) nav.pop() else open(toOpen)
                    }
                )
                wide -> Row(Modifier.fillMaxSize()) {
                    CategoryList(snap, category, Modifier.weight(0.4f).fillMaxHeight()) { category = it }
                    Box(Modifier.width(1.dp).fillMaxHeight().background(Brand.Gold.copy(alpha = 0.35f)))
                    Box(Modifier.weight(0.6f).fillMaxHeight()) {
                        if (showingExercises) {
                            exerciseList()
                        } else {
                            Text(
                                "Choose a category, or search every exercise.",
                                Modifier.padding(Spacing.lg),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                showingExercises -> exerciseList()
                else -> CategoryList(snap, null) { category = it }
            }
        }

        if (picked.isNotEmpty()) {
            GoldHairline()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { picked.clear() }) { Text("Clear") }
                GoldButton(onClick = { open(picked.toList()) }) { Text("Add ${countOf(picked.size, "exercise")}") }
            }
        }
    }

    if (creating) {
        ExerciseEditorSheet(
            snap = snap,
            existing = null,
            initialCategoryId = category?.takeIf { it > 0L } ?: (snap.categoriesSorted.firstOrNull()?.id ?: Workouts.UNCATEGORISED),
            // As in FitNotes, a new exercise joins the list rather than opening straight away.
            onDismiss = { creating = false }
        )
    }
    editing?.let { ex ->
        ExerciseEditorSheet(snap = snap, existing = ex, initialCategoryId = ex.categoryId, onDismiss = { editing = null })
    }
    deleting?.let { ex -> DeleteExerciseSheet(snap, ex) { deleting = null } }
    merging?.let { ex -> MergeExerciseFlow(snap, ex) { merging = null } }
    if (showCategories) CategoryManagerSheet(snap) { showCategories = false }
    if (seeding) StarterLibraryDialog { seeding = false }
}

/**
 * A workout's days as cards (#106), as FitNotes shows a routine: each lists its exercises with how their sets are
 * filled, and **Log all** adds the whole day. The suggested next day is picked out in gold (#21). Tapping an exercise
 * opens it on its own.
 */
@Composable
private fun RoutineDayList(snap: Snapshot, routine: Routine, onOpen: (Long) -> Unit, onLogAll: (RoutineDay) -> Unit) {
    val next = Routines.nextDay(snap, routine)
    LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xxl)) {
        if (!routine.notes.isNullOrBlank()) {
            item(key = "notes") {
                Text(routine.notes, Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (routine.days.all { it.exercises.isEmpty() }) {
            item(key = "empty") {
                Text(
                    "This workout has no exercises yet. Tap the pencil to add them.",
                    Modifier.padding(Spacing.lg),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        routine.days.forEach { d ->
            item(key = d.id) {
                val isNext = d.id == next?.id
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                        .raisedGlass(FitShapes.card)
                ) {
                    Row(Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = Spacing.sm)) {
                            Text(d.name, style = MaterialTheme.typography.titleMedium, color = if (isNext) Brand.Gold else MaterialTheme.colorScheme.onSurface)
                            if (isNext) Text("NEXT", style = MaterialTheme.typography.labelSmall, color = Brand.Gold)
                        }
                        TextButton(
                            onClick = { onLogAll(d) },
                            enabled = d.exercises.isNotEmpty(),
                            modifier = Modifier.heightIn(min = Spacing.touch)
                        ) { Text("Log all") }
                    }
                    GoldHairline()
                    if (d.exercises.isEmpty()) {
                        Text("No exercises yet", Modifier.padding(Spacing.lg), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    d.exercises.forEach { p ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = Spacing.row)
                                .clickable(onClickLabel = "Open") { onOpen(p.exerciseId) }
                                .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                        ) {
                            Text(snap.exercises[p.exerciseId]?.name ?: "Exercise", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                planSummary(snap, p),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun toggle(picked: MutableList<Long>, id: Long) {
    if (id in picked) picked.remove(id) else picked.add(id)
}

/**
 * The first view: Favourites (when there are any), every category, and Uncategorised (when used). [selectedId] is
 * picked out when the list sits beside the exercises on a wide screen.
 */
@Composable
private fun CategoryList(snap: Snapshot, selectedId: Long?, modifier: Modifier = Modifier, onOpen: (Long) -> Unit) {
    val counts = remember(snap) { snap.exercisesSorted.groupingBy { it.categoryId }.eachCount() }
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = Spacing.xxl)) {
        if (snap.favouriteExercises.isNotEmpty()) {
            item(key = "fav") {
                CategoryRow("Favourites", Brand.Gold, snap.favouriteExercises.size, selectedId == FAVOURITES) { onOpen(FAVOURITES) }
            }
        }
        snap.categoriesSorted.forEach { c ->
            item(key = "c${c.id}") {
                CategoryRow(c.name, categoryColour(c.colour), counts[c.id] ?: 0, selectedId == c.id) { onOpen(c.id) }
            }
        }
        val loose = counts[Workouts.UNCATEGORISED] ?: 0
        if (loose > 0) {
            item(key = "none") {
                CategoryRow("Uncategorised", MaterialTheme.colorScheme.outline, loose, selectedId == Workouts.UNCATEGORISED) {
                    onOpen(Workouts.UNCATEGORISED)
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(name: String, colour: Color, count: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.row)
            .background(if (selected) Brand.ImperialPurple.copy(alpha = 0.35f) else Color.Transparent)
            .clickable(onClickLabel = "Show $name exercises", onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "$name, ${countOf(count, "exercise")}"
                this.selected = selected
            }
            .padding(end = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(6.dp).height(Spacing.row).background(colour))
        Spacer(Modifier.width(Spacing.lg))
        Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text("$count", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    GoldHairline()
}

/** A category's exercises, or search results grouped by category. */
@Composable
private fun ExerciseList(
    snap: Snapshot,
    exercises: List<Exercise>,
    grouped: Boolean,
    picked: List<Long>,
    emptyText: String,
    onOpen: (Exercise) -> Unit,
    onPick: (Exercise) -> Unit,
    onEdit: (Exercise) -> Unit,
    onDetails: (Exercise) -> Unit,
    onDelete: (Exercise) -> Unit,
    onMerge: (Exercise) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xxl)) {
        if (exercises.isEmpty()) {
            item(key = "empty") {
                Text(emptyText, Modifier.padding(Spacing.lg), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val groups = if (grouped) {
            exercises.groupBy { it.categoryId }.entries.sortedWith(
                compareBy({ snap.categories[it.key]?.sortOrder ?: 9999 }, { snap.categories[it.key]?.name?.lowercase() ?: "~" })
            ).map { it.key to it.value }
        } else listOf(null to exercises)
        groups.forEach { (catId, list) ->
            if (catId != null) {
                item(key = "h$catId") {
                    val cat = snap.categories[catId]
                    Row(Modifier.padding(start = Spacing.lg, top = Spacing.md, bottom = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                        Dot(categoryColour(cat?.colour ?: 0), 10.dp)
                        Spacer(Modifier.width(Spacing.sm))
                        Text((cat?.name ?: "Uncategorised").uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            list.forEach { ex ->
                item(key = "x${catId ?: ""}${ex.id}") {
                    ExerciseRow(
                        snap = snap,
                        ex = ex,
                        order = picked.indexOf(ex.id).takeIf { it >= 0 }?.plus(1),
                        choosing = picked.isNotEmpty(),
                        onOpen = { onOpen(ex) },
                        onPick = { onPick(ex) },
                        menu = listOf(
                            MenuAction("Edit") { onEdit(ex) },
                            MenuAction("Records and goals", enabled = (snap.workoutsByExercise[ex.id] ?: 0) > 0) { onDetails(ex) },
                            MenuAction("Merge into…") { onMerge(ex) },
                            MenuAction("Delete") { onDelete(ex) }
                        )
                    )
                }
            }
        }
    }
}

/**
 * One exercise: name, type hint and when it was last done, the favourite star and its menu. Tap opens it; a long
 * press chooses it for adding several at once, and [order] is its place in that choice.
 */
@Composable
private fun ExerciseRow(
    snap: Snapshot,
    ex: Exercise,
    order: Int?,
    choosing: Boolean,
    onOpen: () -> Unit,
    onPick: () -> Unit,
    menu: List<MenuAction>
) {
    val last = snap.lastUsedByExercise[ex.id]
    val sub = listOfNotNull(
        ExerciseTypes.label(ex.type).takeIf { ex.type != ExerciseTypes.WEIGHT_REPS },
        last?.let { "Last ${Dates.medium(it)}" } ?: "Not logged yet"
    ).joinToString(" · ")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.row)
            .background(if (order != null) Brand.ImperialPurple.copy(alpha = 0.35f) else Color.Transparent)
            .combinedClickable(
                onClickLabel = if (choosing) "Choose or unchoose" else "Log ${ex.name}",
                onLongClickLabel = "Choose several exercises",
                onLongClick = onPick,
                onClick = onOpen
            )
            .semantics { selected = order != null }
            .padding(start = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (choosing) {
            Checkbox(checked = order != null, onCheckedChange = null)
            Spacer(Modifier.width(Spacing.sm))
        }
        Column(Modifier.weight(1f).padding(vertical = Spacing.sm)) {
            Text(ex.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                if (order != null) "$sub · ${ordinal(order)}" else sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        FavouriteButton(ex)
        OverflowMenu(menu, description = "Options for ${ex.name}")
    }
}

private fun ordinal(n: Int): String = n.toString() + when {
    n % 100 in 11..13 -> "th"
    n % 10 == 1 -> "st"
    n % 10 == 2 -> "nd"
    n % 10 == 3 -> "rd"
    else -> "th"
}

/** Gold star toggle. Favourites come first in the library. */
@Composable
fun FavouriteButton(ex: Exercise) {
    IconButton(onClick = { AppScope.scope.launch { Workouts.setFavourite(ex.id, !ex.favourite) } }) {
        Icon(
            Icons.Filled.Star,
            contentDescription = if (ex.favourite) "Remove ${ex.name} from favourites" else "Make ${ex.name} a favourite",
            tint = if (ex.favourite) Brand.Gold else MaterialTheme.colorScheme.outline
        )
    }
}

/** Exercise notes, with a button for each link they contain. */
@Composable
fun ExerciseNotes(notes: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val links = remember(notes) {
        LinkPattern.findAll(notes).map { it.value.trimEnd('.', ',', ';', ')') }.distinct().toList()
    }
    Column(modifier) {
        Text(notes, style = MaterialTheme.typography.bodyMedium)
        if (links.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                links.forEach { url ->
                    TextButton(onClick = {
                        try {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (e: Exception) {
                            UiEvents.show("Nothing on this phone can open that link.")
                        }
                    }) {
                        Text(
                            url.removePrefix("https://").removePrefix("http://").take(30),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * An exercise's settings at a glance (#110), from the exercise screen's info button, as FitNotes shows them: notes
 * (with their link buttons), weight increment, rest time, default graph and type. **Edit** opens [ExerciseEditorSheet]
 * through [onEdit]. [weightStepShown] is the increment the steppers use, in the display unit.
 */
@Composable
fun ExerciseInfoSheet(snap: Snapshot, ex: Exercise, weightStepShown: Double, onEdit: () -> Unit, onDismiss: () -> Unit) {
    val prefs by Settings.portable.collectAsState()
    val graphs = graphLabels(ex.type, timeBased = ExerciseTypes.timeBased(ex.type, anyWeightOrReps = false))
    @Composable
    fun InfoRow(label: String, value: String) {
        Row(Modifier.fillMaxWidth().heightIn(min = Spacing.touch), verticalAlignment = Alignment.CenterVertically) {
            Text(label.uppercase(), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        GoldHairline()
    }
    FitSheet(
        title = ex.name,
        onDismiss = onDismiss,
        dismissLabel = "Close",
        confirmLabel = "Edit",
        onConfirm = { onDismiss(); onEdit() }
    ) {
        Text("NOTES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val notes = ex.notes
        if (notes.isNullOrBlank()) {
            Text("No notes saved for this exercise.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            ExerciseNotes(notes)
        }
        GoldHairline()
        if (ExerciseTypes.usesWeight(ex.type)) InfoRow("Weight increment", "${fmtNum(weightStepShown, 2)} ${snap.weightUnitOf(ex.id)}")
        InfoRow("Rest time", ex.restSeconds?.let { fmtDuration(it) } ?: "Default (${fmtDuration(prefs.restSeconds)})")
        InfoRow("Default graph", graphs.getOrNull(ex.defaultGraph.takeIf { it >= 0 } ?: 0) ?: "None")
        InfoRow("Type", ExerciseTypes.label(ex.type))
    }
}

// ---------------------------------------------------------------------------------------------------------
// Exercise editor
// ---------------------------------------------------------------------------------------------------------

/**
 * Creates or edits an exercise, as a sheet (#83). "Add another" saves and keeps the sheet open for the next exercise,
 * with the category kept. [onSaved] runs after a plain save of a new exercise, so the library can open it.
 */
@Composable
fun ExerciseEditorSheet(
    snap: Snapshot,
    existing: Exercise?,
    initialCategoryId: Long,
    onDismiss: () -> Unit,
    onSaved: (Long) -> Unit = {}
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var categoryId by remember { mutableStateOf(existing?.categoryId ?: initialCategoryId) }
    var type by remember { mutableStateOf(existing?.type ?: ExerciseTypes.WEIGHT_REPS) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var newCategory by remember { mutableStateOf(false) }
    // This exercise's defaults (#15): weight step in kg (null = the global step) and the graph it opens on.
    var stepKg by remember { mutableStateOf(existing?.weightStepKg) }
    var defaultGraph by remember { mutableStateOf(existing?.defaultGraph ?: -1) }
    var restSec by remember { mutableStateOf(existing?.restSeconds) }
    // Its own distance unit (#7), or null for the global one.
    var distUnit by remember { mutableStateOf(existing?.distanceUnit) }
    // Its own weight unit (#7), or null for the global one.
    var weightUnit by remember { mutableStateOf(existing?.weightUnit) }

    fun save(keepOpen: Boolean) {
        val n = name.trim()
        if (n.isEmpty()) {
            UiEvents.show("Enter a name for the exercise.")
            return
        }
        val c = categoryId
        val t = type
        val note = notes.trim().ifBlank { null }
        val step = stepKg
        val graph = defaultGraph
        val rest = restSec
        val dUnit = distUnit
        val wUnit = weightUnit
        if (!keepOpen) onDismiss()
        AppScope.scope.launch {
            try {
                val id = if (existing == null) {
                    Workouts.createExercise(n, c, t, note)
                } else {
                    Workouts.updateExercise(existing.id, n, c, t, note)
                    existing.id
                }
                if (step != existing?.weightStepKg || graph != (existing?.defaultGraph ?: -1) || rest != existing?.restSeconds ||
                    dUnit != existing?.distanceUnit || wUnit != existing?.weightUnit
                ) {
                    Workouts.setExerciseDefaults(id, step, graph, rest, dUnit, wUnit)
                }
                if (keepOpen) {
                    // Ready for the next one in the same category (#83). onSaved isn't fired: it would open the
                    // exercise and close the sheet (#71).
                    name = ""
                    notes = ""
                    UiEvents.show("Saved $n")
                } else if (existing == null) {
                    onSaved(id)
                }
            } catch (e: WorkoutDataException) {
                UiEvents.show(e.message ?: "That exercise couldn't be saved.")
            }
        }
    }

    FitSheet(
        title = if (existing == null) "New exercise" else "Edit exercise",
        onDismiss = onDismiss,
        confirmLabel = "Save",
        onConfirm = { save(keepOpen = false) },
        confirmEnabled = name.isNotBlank(),
        secondaryLabel = if (existing == null) "Add another" else null,
        onSecondary = if (existing == null) ({ save(keepOpen = true) }) else null
    ) {
        OutlinedTextField(
            value = name, onValueChange = { name = it }, label = { Text("Name") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        FieldLabel("Category")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            snap.categoriesSorted.forEach { c ->
                FilterChip(
                    selected = categoryId == c.id,
                    onClick = { categoryId = c.id },
                    label = { Text(c.name) },
                    leadingIcon = { Dot(categoryColour(c.colour), 8.dp) }
                )
            }
            FilterChip(
                selected = categoryId == Workouts.UNCATEGORISED,
                onClick = { categoryId = Workouts.UNCATEGORISED },
                label = { Text("Uncategorised") }
            )
            FilterChip(selected = false, onClick = { newCategory = true }, label = { Text("New category…") })
        }
        // The type decides what each set records (#14): the two main types first, as in FitNotes, then the rest.
        FieldLabel("Type")
        val main = listOf(ExerciseTypes.WEIGHT_REPS, ExerciseTypes.DISTANCE_TIME)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            main.forEach { t ->
                FilterChip(selected = type == t, onClick = { type = t }, label = { Text(ExerciseTypes.label(t)) })
            }
        }
        FieldLabel("More types")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ExerciseTypes.all.filter { it !in main }.forEach { t ->
                FilterChip(selected = type == t, onClick = { type = t }, label = { Text(ExerciseTypes.label(t)) })
            }
        }
        Text(
            "For example: ${ExerciseTypes.example(type)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (existing != null && type != existing.type && (snap.workoutsByExercise[existing.id] ?: 0) > 0) {
            Text(
                "Sets already logged keep every value. Any value the new type doesn't record still shows in its own column.",
                style = MaterialTheme.typography.bodySmall,
                color = Brand.GoldLight
            )
        }
        if (ExerciseTypes.usesWeight(type)) {
            // Its own weight unit (#7): weights are stored in kg, so a change only converts how they're shown.
            FieldLabel("Weight unit")
            DropdownPill(
                "Weight unit",
                listOf("As in Settings (${snap.weightUnit})", "Kilograms (kg)", "Pounds (lbs)"),
                when (weightUnit) { "kg" -> 1; "lbs" -> 2; else -> 0 }
            ) { i -> weightUnit = when (i) { 1 -> "kg"; 2 -> "lbs"; else -> null } }
            val shownUnit = weightUnit ?: snap.weightUnit
            val lbs = shownUnit == "lbs"
            val steps = if (lbs) listOf(1.0, 2.5, 5.0, 10.0) else listOf(0.5, 1.0, 1.25, 2.5, 5.0)
            FieldLabel("Weight step")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = stepKg == null, onClick = { stepKg = null }, label = { Text("As in Settings") })
                steps.forEach { v ->
                    val kg = WeightUnits.convert(v, shownUnit, "kg")
                    FilterChip(
                        selected = stepKg?.let { kotlin.math.abs(it - kg) < 0.001 } == true,
                        onClick = { stepKg = kg },
                        label = { Text("${fmtNum(v, 2)} $shownUnit") }
                    )
                }
            }
        }
        // Its own distance unit (#7): distances are kept as typed, so a change relabels them without converting.
        if (ExerciseTypes.usesDistance(type) || (existing != null && snap.setsByExercise[existing.id].orEmpty().any { it.distance > 0 })) {
            val global = Settings.portable.collectAsState().value.distanceUnit
            FieldLabel("Distance unit")
            DropdownPill(
                "Distance unit",
                listOf("As in Settings ($global)") + DistanceUnits.ALL.map { "${DistanceUnits.label(it)} ($it)" },
                distUnit?.let { DistanceUnits.ALL.indexOf(it) + 1 } ?: 0
            ) { i -> distUnit = if (i == 0) null else DistanceUnits.ALL[i - 1] }
            if (existing != null && distUnit != existing.distanceUnit && snap.setsByExercise[existing.id].orEmpty().any { it.distance > 0 }) {
                Text(
                    "Distances already logged keep their numbers and are shown in the new unit.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Brand.GoldLight
                )
            }
        }
        // Its own rest length (#15), any exact length (#105): the rest timer uses it after this exercise's sets.
        // Default follows the rest timer's own length.
        FieldLabel("Rest time")
        val defaultRest = Settings.portable.collectAsState().value.restSeconds
        RestLengthStepper(
            seconds = restSec ?: defaultRest,
            onChange = { restSec = it },
            isDefault = restSec == null,
            onDefault = { restSec = null }
        )
        FieldLabel("Opens on graph")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            graphLabels(type, timeBased = ExerciseTypes.timeBased(type, anyWeightOrReps = false)).forEachIndexed { i, label ->
                FilterChip(
                    selected = defaultGraph == i || (defaultGraph < 0 && i == 0),
                    onClick = { defaultGraph = i },
                    label = { Text(label) }
                )
            }
        }
        OutlinedTextField(
            value = notes, onValueChange = { notes = it },
            label = { Text("Notes (form cues, machine settings, links)") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp)
        )
        if (existing?.imported == true) {
            Text(
                "This exercise came from FitNotes. Editing it makes it FitLens's own; its history is kept and a " +
                    "later import follows the change.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (newCategory) {
        CategoryEditorSheet(snap, existing = null, onDismiss = { newCategory = false }) { id -> categoryId = id }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.xs)
    )
}

@Composable
private fun DeleteExerciseSheet(snap: Snapshot, ex: Exercise, onDismiss: () -> Unit) {
    val sets = snap.setsByExercise[ex.id]?.size ?: 0
    val days = snap.workoutsByExercise[ex.id] ?: 0
    ConfirmSheet(
        title = "Delete ${ex.name}?",
        message = if (sets == 0) {
            "Nothing has been logged for it, so nothing else is lost."
        } else {
            "${countOf(sets, "set")} across ${countOf(days, "workout")} will be deleted with it. This can't be undone, " +
                "and a later FitNotes import won't bring them back."
        },
        confirmLabel = "Delete exercise",
        onDismiss = onDismiss,
        onConfirm = {
            AppScope.scope.launch {
                Workouts.deleteExercise(ex.id)
                UiEvents.show("Deleted ${ex.name}")
            }
        }
    )
}

/**
 * Merges a duplicate exercise into another one (#57): pick the exercise to keep (same type only, so weights never mix
 * with distances or times), confirm what moves, then merge after a safety copy, so it can be undone from
 * Settings → Backups like other bulk changes.
 */
@Composable
private fun MergeExerciseFlow(snap: Snapshot, ex: Exercise, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var into by remember { mutableStateOf<Exercise?>(null) }
    val target = into
    if (target == null) {
        // Built in composition, because categoryColour reads the theme.
        val items = snap.exercisesSorted.filter { it.id != ex.id && it.type == ex.type }.map { other ->
            val cat = snap.categories[other.categoryId]
            PickerItem(
                id = other.id,
                title = other.name,
                subtitle = countOf(snap.setsByExercise[other.id]?.size ?: 0, "set"),
                section = cat?.name ?: "Uncategorised",
                color = categoryColour(cat?.colour ?: 0)
            )
        }
        SearchablePicker(
            title = "Merge ${ex.name} into",
            items = items,
            onDismiss = onDismiss,
            onPick = { ids -> into = ids.firstOrNull()?.let { snap.exercises[it] } },
            searchLabel = "Search exercises",
            emptyText = "No other exercise of the same type matches."
        )
        return
    }
    val sets = snap.setsByExercise[ex.id]?.size ?: 0
    val days = snap.workoutsByExercise[ex.id] ?: 0
    ConfirmSheet(
        title = "Merge into ${target.name}?",
        message = "${ex.name} becomes part of ${target.name}: ${countOf(sets, "set")} across ${countOf(days, "workout")}, " +
            "its goals and its places in saved workouts all move across, and ${ex.name} is removed. ${target.name} keeps " +
            "its name, category and settings. Personal records are worked out again, and later FitNotes imports add " +
            "${ex.name}'s history to ${target.name}. A safety copy is taken first, so you can undo this from " +
            "Settings → Backups for ${Backups.UNDO_DAYS} days.",
        confirmLabel = "Merge",
        onDismiss = onDismiss,
        onConfirm = {
            runBusy("Merging exercises…") {
                // The way back (#47). If it can't be made, nothing is merged.
                val safety = Backups.safetyCopy(ctx, "Before merging ${ex.name} into ${target.name}")
                if (!safety.ok) return@runBusy safety
                try {
                    val n = Workouts.mergeExercises(ex.id, target.id)
                    ImportSummary("Merged ${ex.name} into ${target.name} (${countOf(n, "set")}). Undo is in Settings → Backups.", ok = true)
                } catch (e: WorkoutDataException) {
                    ImportSummary(e.message ?: "Couldn't merge those exercises.", ok = false)
                }
            }
        }
    )
}

// ---------------------------------------------------------------------------------------------------------
// Categories
// ---------------------------------------------------------------------------------------------------------

/**
 * Every category with its colour and exercise count, to add, rename, recolour, delete or reorder (#83). The order
 * follows the drag handles at once and is saved when the sheet closes, so a drag doesn't reload the data at every step.
 */
@Composable
fun CategoryManagerSheet(snap: Snapshot, onDismiss: () -> Unit) {
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Category?>(null) }
    var deleting by remember { mutableStateOf<Category?>(null) }
    val ids = snap.categoriesSorted.map { it.id }
    val order = remember { mutableStateListOf<Long>().apply { addAll(ids) } }
    var moved by remember { mutableStateOf(false) }
    // Categories added or deleted while the sheet is open join or leave the order.
    LaunchedEffect(ids.toSet()) {
        val kept = order.filter { it in ids }
        order.clear()
        order.addAll(kept + ids.filter { it !in kept })
    }
    DisposableEffect(Unit) {
        onDispose {
            if (moved) {
                val final = order.toList()
                AppScope.scope.launch { Workouts.reorderCategories(final) }
            }
        }
    }
    fun move(from: Int, to: Int) {
        if (to !in order.indices) return
        order.add(to, order.removeAt(from))
        moved = true
    }

    FitSheet(
        title = "Categories",
        onDismiss = onDismiss,
        dismissLabel = "Done",
        confirmLabel = "New category",
        onConfirm = { creating = true },
        destructive = false
    ) {
        if (snap.categoriesSorted.isEmpty()) {
            Text("No categories yet.", style = MaterialTheme.typography.bodyMedium)
        }
        order.forEachIndexed { i, id ->
            val c = snap.categories[id] ?: return@forEachIndexed
            val count = snap.exercisesSorted.count { it.categoryId == c.id }
            // Keyed by id, so a row being dragged stays with its category as the list reorders.
            key(id) {
                ListRowWithMenu(
                    title = c.name,
                    subtitle = countOf(count, "exercise"),
                    leading = { Dot(categoryColour(c.colour), 12.dp) },
                    onClick = { editing = c },
                    menu = listOf(
                        MenuAction("Edit") { editing = c },
                        MenuAction("Delete") { deleting = c }
                    ),
                    onMoveUp = if (i > 0) ({ move(i, i - 1) }) else null,
                    onMoveDown = if (i < order.lastIndex) ({ move(i, i + 1) }) else null
                )
            }
            GoldHairline()
        }
    }

    if (creating) CategoryEditorSheet(snap, existing = null, onDismiss = { creating = false })
    editing?.let { c -> CategoryEditorSheet(snap, existing = c, onDismiss = { editing = null }) }
    deleting?.let { c ->
        val count = snap.exercisesSorted.count { it.categoryId == c.id }
        ConfirmSheet(
            title = "Delete ${c.name}?",
            message = if (count == 0) "The category is empty." else
                "Its ${countOf(count, "exercise")} and all their logged history are kept. They become uncategorised.",
            confirmLabel = "Delete category",
            onDismiss = { deleting = null },
            onConfirm = {
                AppScope.scope.launch {
                    Workouts.deleteCategory(c.id)
                    UiEvents.show("Deleted ${c.name}")
                }
            }
        )
    }
}

/** Names and colours a category, as a sheet (#83). */
@Composable
fun CategoryEditorSheet(
    snap: Snapshot,
    existing: Category?,
    onDismiss: () -> Unit,
    onSaved: (Long) -> Unit = {}
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var colour by remember { mutableStateOf(existing?.colour?.takeIf { it != 0 } ?: CategoryPaletteArgb.first()) }

    FitSheet(
        title = if (existing == null) "New category" else "Edit category",
        onDismiss = onDismiss,
        confirmLabel = "Save category",
        confirmEnabled = name.isNotBlank(),
        onConfirm = {
            val n = name.trim()
            val c = colour
            onDismiss()
            AppScope.scope.launch {
                try {
                    val id = if (existing == null) {
                        Workouts.createCategory(n, c)
                    } else {
                        Workouts.updateCategory(existing.id, n, c)
                        existing.id
                    }
                    onSaved(id)
                } catch (e: WorkoutDataException) {
                    UiEvents.show(e.message ?: "That category couldn't be saved.")
                }
            }
        }
    ) {
        OutlinedTextField(
            value = name, onValueChange = { name = it }, label = { Text("Name") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        FieldLabel("Colour")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            CategoryPaletteArgb.forEachIndexed { i, argb ->
                Box(
                    Modifier
                        .size(Spacing.touch)
                        .clip(CircleShape)
                        .clickable(onClickLabel = "Use colour ${i + 1}") { colour = argb }
                        .semantics { selected = colour == argb; contentDescription = "Colour ${i + 1}" },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(if (colour == argb) 36.dp else 26.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                    )
                }
            }
        }
        if (snap.categoriesSorted.isEmpty()) {
            Text(
                "Categories group your exercises and colour them through the app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------------------
// Starter library
// ---------------------------------------------------------------------------------------------------------

/** Asks before adding the starter library. It is never seeded without this. */
@Composable
fun StarterLibraryDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add the starter library?") },
        text = {
            Text(
                "This adds ${StarterLibrary.exerciseCount} common exercises in ${StarterLibrary.categories.size} " +
                    "categories (${StarterLibrary.categories.joinToString(", ") { it.name }}).\n\n" +
                    "Anything already in your library keeps its name, category and history — nothing is replaced or " +
                    "deleted. You can edit or delete any of them afterwards."
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                AppScope.scope.launch {
                    UiEvents.busy.value = "Adding the starter library…"
                    try {
                        UiEvents.show(Workouts.seedStarterLibrary(CategoryPaletteArgb).message)
                    } catch (e: Exception) {
                        UiEvents.show("The starter library couldn't be added: ${e.message}")
                    } finally {
                        UiEvents.busy.value = null
                    }
                }
            }) { Text("Add them") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Opens the exercise library from a screen's top bar. */
@Composable
fun LibraryAction(nav: Nav) {
    IconButton(onClick = { nav.push(Screen.Library()) }) {
        Icon(Icons.Filled.List, contentDescription = "Exercise library")
    }
}
