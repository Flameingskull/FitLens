@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.fitlens.companion.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.Category
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.Exercise
import com.fitlens.companion.data.ExerciseTypes
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.StarterLibrary
import com.fitlens.companion.data.WorkoutDataException
import com.fitlens.companion.data.Workouts
import kotlinx.coroutines.launch

/**
 * The exercise library (#13): categories and exercises, with quick add, favourites, notes, edit and delete.
 * Every write goes through [Workouts], so a later FitNotes import follows renames and respects deletions.
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

private const val FILTER_ALL = -2L
private const val FILTER_FAVOURITES = -1L

private val LinkPattern = Regex("https?://\\S+")

// ---------------------------------------------------------------------------------------------------------
// Library screen
// ---------------------------------------------------------------------------------------------------------

@Composable
fun ExerciseLibraryScreen(snap: Snapshot, nav: Nav) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(FILTER_ALL) }
    var menu by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Exercise?>(null) }
    var deleting by remember { mutableStateOf<Exercise?>(null) }
    var showCategories by remember { mutableStateOf(false) }
    var seeding by remember { mutableStateOf(false) }

    val shown = remember(snap, query, filter) {
        snap.exercisesSorted.filter { ex ->
            val matches = query.isBlank() || ex.name.contains(query, true) || (ex.notes ?: "").contains(query, true)
            val inFilter = when (filter) {
                FILTER_ALL -> true
                FILTER_FAVOURITES -> ex.favourite
                else -> ex.categoryId == filter
            }
            matches && inFilter
        }
    }
    val grouped = remember(shown, snap) {
        shown.groupBy { it.categoryId }.entries.sortedWith(
            compareBy({ snap.categories[it.key]?.sortOrder ?: 9999 }, { snap.categories[it.key]?.name?.lowercase() ?: "~" })
        )
    }

    Column(Modifier.fillMaxSize()) {
        BackTopBar("Exercise library", onBack = { nav.pop() }) {
            IconButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, contentDescription = "New exercise") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Library options") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Manage categories") }, onClick = { menu = false; showCategories = true })
                    DropdownMenuItem(text = { Text("Add starter library") }, onClick = { menu = false; seeding = true })
                }
            }
        }

        if (snap.exercises.isEmpty()) {
            EmptyState(
                "Your library is empty",
                "Add your own exercises, start from FitLens's starter library, or import a FitNotes backup from Settings → FitNotes import."
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { seeding = true }) { Text("Add starter library") }
                    OutlinedButton(onClick = { creating = true }) { Text("Create an exercise") }
                }
            }
        } else {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                label = { Text("Search exercises") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(selected = filter == FILTER_ALL, onClick = { filter = FILTER_ALL }, label = { Text("All") })
                FilterChip(
                    selected = filter == FILTER_FAVOURITES,
                    onClick = { filter = FILTER_FAVOURITES },
                    label = { Text("Favourites (${snap.favouriteExercises.size})") }
                )
                snap.categoriesSorted.forEach { c ->
                    FilterChip(selected = filter == c.id, onClick = { filter = c.id }, label = { Text(c.name) })
                }
                if (snap.exercisesSorted.any { it.categoryId == Workouts.UNCATEGORISED }) {
                    FilterChip(
                        selected = filter == Workouts.UNCATEGORISED,
                        onClick = { filter = Workouts.UNCATEGORISED },
                        label = { Text("Uncategorised") }
                    )
                }
            }
            GoldHairline()
            if (shown.isEmpty()) {
                Text(
                    "Nothing matches that.",
                    Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                grouped.forEach { (categoryId, list) ->
                    item(key = "cat$categoryId") {
                        val cat = snap.categories[categoryId]
                        Row(Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Dot(categoryColour(cat?.colour ?: 0), 10.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                (cat?.name ?: "Uncategorised").uppercase(),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    list.forEach { ex ->
                        item(key = "ex${ex.id}") {
                            ExerciseLibraryRow(
                                snap = snap,
                                ex = ex,
                                onEdit = { editing = ex },
                                onHistory = { nav.push(Screen.ExerciseDetail(ex.id)) },
                                onDelete = { deleting = ex }
                            )
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        ExerciseEditorDialog(
            snap = snap,
            existing = null,
            initialCategoryId = if (filter > 0L) filter else (snap.categoriesSorted.firstOrNull()?.id ?: Workouts.UNCATEGORISED),
            onDismiss = { creating = false }
        )
    }
    editing?.let { ex ->
        ExerciseEditorDialog(snap = snap, existing = ex, initialCategoryId = ex.categoryId, onDismiss = { editing = null })
    }
    deleting?.let { ex -> DeleteExerciseDialog(snap, ex) { deleting = null } }
    if (showCategories) CategoryManagerDialog(snap) { showCategories = false }
    if (seeding) StarterLibraryDialog { seeding = false }
}

@Composable
private fun ExerciseLibraryRow(
    snap: Snapshot,
    ex: Exercise,
    onEdit: () -> Unit,
    onHistory: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val workouts = snap.workoutsByExercise[ex.id] ?: 0
    val last = snap.lastUsedByExercise[ex.id]
    val sub = buildList {
        add(if (workouts == 0) "Not logged yet" else "$workouts workout${if (workouts == 1) "" else "s"}")
        if (last != null) add("last ${Dates.medium(last)}")
        add(ExerciseTypes.label(ex.type))
    }.joinToString(" · ")

    Row(
        Modifier.fillMaxWidth().clickable { onEdit() }.padding(start = 34.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(ex.name, style = MaterialTheme.typography.bodyLarge)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!ex.notes.isNullOrBlank()) {
                Text(
                    ex.notes!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        FavouriteButton(ex)
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Options for ${ex.name}") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit() })
                DropdownMenuItem(
                    text = { Text("History and graphs") },
                    onClick = { menu = false; onHistory() },
                    enabled = workouts > 0
                )
                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

/** Gold star toggle. Favourites come first in every exercise picker. */
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
@OptIn(ExperimentalLayoutApi::class)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseEditorDialog(
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
                    // "Save & new" keeps the editor open for the next exercise. onSaved is what the picker uses to
                    // choose the exercise and move on, so firing it here closed the dialog instead (#71).
                    name = ""
                    notes = ""
                    UiEvents.show("Saved $n")
                } else {
                    onSaved(id)
                    onDismiss()
                }
            } catch (e: WorkoutDataException) {
                UiEvents.show(e.message ?: "That exercise couldn't be saved.")
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New exercise" else "Edit exercise") },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Text("Category", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    snap.categoriesSorted.forEach { c ->
                        FilterChip(selected = categoryId == c.id, onClick = { categoryId = c.id }, label = { Text(c.name) })
                    }
                    FilterChip(
                        selected = categoryId == Workouts.UNCATEGORISED,
                        onClick = { categoryId = Workouts.UNCATEGORISED },
                        label = { Text("Uncategorised") }
                    )
                    FilterChip(selected = false, onClick = { newCategory = true }, label = { Text("New category…") })
                }
                Text("Type", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExerciseTypes.all.forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(ExerciseTypes.label(t)) })
                    }
                }
                if (ExerciseTypes.usesWeight(type)) {
                    val lbs = snap.weightUnit == "lbs"
                    val steps = if (lbs) listOf(1.0, 2.5, 5.0, 10.0) else listOf(0.5, 1.0, 1.25, 2.5, 5.0)
                    Text("Weight step", style = MaterialTheme.typography.labelLarge)
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
                Text("Opens on graph", style = MaterialTheme.typography.labelLarge)
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
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (existing == null) TextButton(onClick = { save(keepOpen = true) }) { Text("Save & new") }
                TextButton(onClick = { save(keepOpen = false) }) { Text("Save") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (newCategory) {
        CategoryEditorDialog(snap, existing = null, onDismiss = { newCategory = false }) { id -> categoryId = id }
    }
}

@Composable
private fun DeleteExerciseDialog(snap: Snapshot, ex: Exercise, onDismiss: () -> Unit) {
    val sets = snap.setsByExercise[ex.id]?.size ?: 0
    val days = snap.workoutsByExercise[ex.id] ?: 0
    ConfirmDialog(
        title = "Delete ${ex.name}?",
        text = if (sets == 0) {
            "Nothing has been logged for it, so nothing else is lost."
        } else {
            "$sets set${if (sets == 1) "" else "s"} across $days workout${if (days == 1) "" else "s"} will be deleted " +
                "with it. This can't be undone, and a later FitNotes import won't bring them back."
        },
        onDismiss = onDismiss
    ) {
        AppScope.scope.launch {
            Workouts.deleteExercise(ex.id)
            UiEvents.show("Deleted ${ex.name}")
        }
    }
}

// ---------------------------------------------------------------------------------------------------------
// Categories
// ---------------------------------------------------------------------------------------------------------

@Composable
fun CategoryManagerDialog(snap: Snapshot, onDismiss: () -> Unit) {
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Category?>(null) }
    var deleting by remember { mutableStateOf<Category?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categories") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                if (snap.categoriesSorted.isEmpty()) {
                    Text("No categories yet.", Modifier.padding(vertical = 12.dp))
                }
                snap.categoriesSorted.forEach { c ->
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(categoryColour(c.colour), 12.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.titleSmall)
                            val count = snap.exercisesSorted.count { it.categoryId == c.id }
                            Text(
                                "$count exercise${if (count == 1) "" else "s"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { editing = c }) { Icon(Icons.Filled.Edit, contentDescription = "Edit ${c.name}") }
                        IconButton(onClick = { deleting = c }) { Icon(Icons.Filled.Delete, contentDescription = "Delete ${c.name}") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { creating = true }) { Text("New category") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )

    if (creating) CategoryEditorDialog(snap, existing = null, onDismiss = { creating = false })
    editing?.let { c -> CategoryEditorDialog(snap, existing = c, onDismiss = { editing = null }) }
    deleting?.let { c ->
        val count = snap.exercisesSorted.count { it.categoryId == c.id }
        ConfirmDialog(
            title = "Delete ${c.name}?",
            text = if (count == 0) "The category is empty." else
                "Its $count exercise${if (count == 1) "" else "s"} and all their logged history are kept — they become uncategorised.",
            onDismiss = { deleting = null }
        ) {
            AppScope.scope.launch {
                Workouts.deleteCategory(c.id)
                UiEvents.show("Deleted ${c.name}")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryEditorDialog(
    snap: Snapshot,
    existing: Category?,
    onDismiss: () -> Unit,
    onSaved: (Long) -> Unit = {}
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var colour by remember { mutableStateOf(existing?.colour?.takeIf { it != 0 } ?: CategoryPaletteArgb.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New category" else "Edit category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Text("Colour", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CategoryPaletteArgb.forEach { argb ->
                        Box(
                            Modifier
                                .size(if (colour == argb) 34.dp else 26.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .clickable { colour = argb }
                        )
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
        },
        confirmButton = {
            TextButton(onClick = {
                val n = name.trim()
                if (n.isEmpty()) {
                    UiEvents.show("Enter a name for the category.")
                } else {
                    val c = colour
                    AppScope.scope.launch {
                        try {
                            val id = if (existing == null) {
                                Workouts.createCategory(n, c)
                            } else {
                                Workouts.updateCategory(existing.id, n, c)
                                existing.id
                            }
                            onSaved(id)
                            onDismiss()
                        } catch (e: WorkoutDataException) {
                            UiEvents.show(e.message ?: "That category couldn't be saved.")
                        }
                    }
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
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

// ---------------------------------------------------------------------------------------------------------
// Exercise picker
// ---------------------------------------------------------------------------------------------------------

/**
 * Chooses one exercise: favourites first, then by category, with a search across everything and a way to create
 * one on the spot. Used by "Add exercise" on a day (#10).
 */
@Composable
fun ExercisePickerDialog(snap: Snapshot, title: String = "Add exercise", onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    var query by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var seeding by remember { mutableStateOf(false) }

    val matches = remember(snap, query) {
        snap.exercisesSorted.filter { query.isBlank() || it.name.contains(query, true) }
    }
    val favourites = remember(matches) { matches.filter { it.favourite } }
    val grouped = remember(matches, snap) {
        matches.groupBy { it.categoryId }.entries.sortedWith(
            compareBy({ snap.categories[it.key]?.sortOrder ?: 9999 }, { snap.categories[it.key]?.name?.lowercase() ?: "~" })
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 460.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text("Search") }, modifier = Modifier.fillMaxWidth()
                )
                if (snap.exercises.isEmpty()) {
                    Text(
                        "Your library is empty. Create an exercise, or add FitLens's starter library.",
                        Modifier.padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = { seeding = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Add starter library")
                    }
                } else {
                    LazyColumn(Modifier.fillMaxWidth()) {
                        if (favourites.isNotEmpty()) {
                            item(key = "favhead") { PickerHeader("Favourites", Brand.Gold) }
                            favourites.forEach { ex ->
                                item(key = "fav${ex.id}") { PickerRow(snap, ex, onPick) }
                            }
                        }
                        grouped.forEach { (categoryId, list) ->
                            item(key = "h$categoryId") {
                                val cat = snap.categories[categoryId]
                                PickerHeader(cat?.name ?: "Uncategorised", categoryColour(cat?.colour ?: 0))
                            }
                            list.forEach { ex ->
                                item(key = "p$categoryId-${ex.id}") { PickerRow(snap, ex, onPick) }
                            }
                        }
                        if (matches.isEmpty()) {
                            item(key = "none") {
                                Text(
                                    "Nothing matches “$query”.",
                                    Modifier.padding(vertical = 12.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { creating = true }) { Text("New exercise") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (creating) {
        ExerciseEditorDialog(
            snap = snap,
            existing = null,
            initialCategoryId = snap.categoriesSorted.firstOrNull()?.id ?: Workouts.UNCATEGORISED,
            onDismiss = { creating = false },
            onSaved = { id -> onPick(id) }
        )
    }
    if (seeding) StarterLibraryDialog { seeding = false }
}

@Composable
private fun PickerHeader(text: String, colour: Color) {
    Row(Modifier.padding(top = 12.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Dot(colour, 8.dp)
        Spacer(Modifier.width(8.dp))
        Text(text.uppercase(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun PickerRow(snap: Snapshot, ex: Exercise, onPick: (Long) -> Unit) {
    val last = snap.lastUsedByExercise[ex.id]
    Row(
        Modifier.fillMaxWidth().clickable { onPick(ex.id) }.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(ex.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                last?.let { "Last ${Dates.medium(it)}" } ?: "Not logged yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (ex.favourite) Icon(Icons.Filled.Star, contentDescription = "Favourite", tint = Brand.Gold)
    }
}

/** Opens the exercise library from a screen's top bar. */
@Composable
fun LibraryAction(nav: Nav) {
    IconButton(onClick = { nav.push(Screen.Library) }) {
        Icon(Icons.Filled.List, contentDescription = "Exercise library")
    }
}
