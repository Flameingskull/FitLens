@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.fitlens.companion.ui

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
import com.fitlens.companion.data.Category
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.Exercise
import com.fitlens.companion.data.ExerciseTypes
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.StarterLibrary
import com.fitlens.companion.data.WorkoutDataException
import com.fitlens.companion.data.Workouts
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.ConfirmSheet
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.FitTopBar
import com.fitlens.companion.ui.design.ListRowWithMenu
import com.fitlens.companion.ui.design.MenuAction
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
    val title = when {
        picked.isNotEmpty() -> "${picked.size} selected"
        category == FAVOURITES -> "Favourites"
        category == Workouts.UNCATEGORISED -> "Uncategorised"
        else -> category?.let { snap.categories[it]?.name } ?: "Exercises"
    }

    Column(Modifier.fillMaxSize()) {
        FitTopBar(
            title = title,
            subtitle = if (picked.isEmpty()) "For ${relativeDayLabel(date)}" else "Tap more, or add them below",
            onBack = { back() },
            backLabel = if (picked.isNotEmpty()) "Clear selection" else "Back",
            actions = if (picked.isNotEmpty()) emptyList() else listOf(
                TopBarAction(Icons.Filled.Search, "Search exercises") { searching = !searching; if (!searching) query = "" },
                TopBarAction(Icons.Filled.Add, "New exercise") { creating = true }
            ),
            overflow = if (picked.isNotEmpty()) emptyList() else listOf(
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
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
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
                        Button(onClick = { seeding = true }) { Text("Add starter library") }
                        OutlinedButton(onClick = { creating = true }) { Text("Create an exercise") }
                    }
                }
                showingExercises -> ExerciseList(
                    snap = snap,
                    exercises = listed,
                    grouped = searching && q.isNotEmpty(),
                    picked = picked,
                    emptyText = if (searching && q.isNotEmpty()) "No exercise matches “$q”." else "No exercises here yet. Tap + to create one.",
                    onOpen = { ex -> if (picked.isNotEmpty()) toggle(picked, ex.id) else open(listOf(ex.id)) },
                    onPick = { ex -> toggle(picked, ex.id) },
                    onEdit = { editing = it },
                    onDetails = { nav.push(Screen.ExerciseDetail(it.id)) },
                    onDelete = { deleting = it }
                )
                else -> CategoryList(snap) { category = it }
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
                Button(onClick = { open(picked.toList()) }) { Text("Add ${countOf(picked.size, "exercise")}") }
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
    if (showCategories) CategoryManagerSheet(snap) { showCategories = false }
    if (seeding) StarterLibraryDialog { seeding = false }
}

private fun toggle(picked: MutableList<Long>, id: Long) {
    if (id in picked) picked.remove(id) else picked.add(id)
}

/** The first view: Favourites (when there are any), every category, and Uncategorised (when used). */
@Composable
private fun CategoryList(snap: Snapshot, onOpen: (Long) -> Unit) {
    val counts = remember(snap) { snap.exercisesSorted.groupingBy { it.categoryId }.eachCount() }
    LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xxl)) {
        if (snap.favouriteExercises.isNotEmpty()) {
            item(key = "fav") { CategoryRow("Favourites", Brand.Gold, snap.favouriteExercises.size) { onOpen(FAVOURITES) } }
        }
        snap.categoriesSorted.forEach { c ->
            item(key = "c${c.id}") { CategoryRow(c.name, categoryColour(c.colour), counts[c.id] ?: 0) { onOpen(c.id) } }
        }
        val loose = counts[Workouts.UNCATEGORISED] ?: 0
        if (loose > 0) {
            item(key = "none") { CategoryRow("Uncategorised", MaterialTheme.colorScheme.outline, loose) { onOpen(Workouts.UNCATEGORISED) } }
        }
    }
}

@Composable
private fun CategoryRow(name: String, colour: Color, count: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.row)
            .clickable(onClickLabel = "Show $name exercises", onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$name, ${countOf(count, "exercise")}" }
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
    onDelete: (Exercise) -> Unit
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
        if (!keepOpen) onDismiss()
        AppScope.scope.launch {
            try {
                val id = if (existing == null) {
                    Workouts.createExercise(n, c, t, note)
                } else {
                    Workouts.updateExercise(existing.id, n, c, t, note)
                    existing.id
                }
                if (step != existing?.weightStepKg || graph != (existing?.defaultGraph ?: -1)) {
                    Workouts.setExerciseDefaults(id, step, graph)
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
        FieldLabel("Type")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ExerciseTypes.all.forEach { t ->
                FilterChip(selected = type == t, onClick = { type = t }, label = { Text(ExerciseTypes.label(t)) })
            }
        }
        if (ExerciseTypes.usesWeight(type)) {
            val lbs = snap.weightUnit == "lbs"
            val steps = if (lbs) listOf(1.0, 2.5, 5.0, 10.0) else listOf(0.5, 1.0, 1.25, 2.5, 5.0)
            FieldLabel("Weight step")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = stepKg == null, onClick = { stepKg = null }, label = { Text("As in Settings") })
                steps.forEach { v ->
                    val kg = snap.toKg(v)
                    FilterChip(
                        selected = stepKg?.let { kotlin.math.abs(it - kg) < 0.001 } == true,
                        onClick = { stepKg = kg },
                        label = { Text("${fmtNum(v, 2)} ${snap.weightUnit}") }
                    )
                }
            }
        }
        FieldLabel("Opens on graph")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            graphLabels(timeBased = type != ExerciseTypes.WEIGHT_REPS).forEachIndexed { i, label ->
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
