package com.fitlens.companion.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.Photo
import com.fitlens.companion.data.Poses
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Store
import kotlinx.coroutines.launch

/** Filter / group key for photos without a pose. */
private const val UNSET = "Unset"
/** Label for "no pose" in pose pickers. */
private const val NOT_SET = "Not set"

private fun poseKey(p: Photo): String = p.pose.ifBlank { UNSET }

/** A titled block of photos in the Photos grid: one month, or one pose. */
private class PhotoSection(val key: String, val title: String, val photos: List<Photo>)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PhotosScreen(snap: Snapshot, nav: Nav) {
    var poseFilter by rememberSaveable { mutableStateOf("All") }
    var groupBy by rememberSaveable { mutableStateOf("Month") }
    val selected = remember { mutableStateListOf<Long>() }
    val selectedSet by remember { derivedStateOf { selected.toHashSet() } }
    var menu by remember { mutableStateOf(false) }
    var poseDialog by remember { mutableStateOf(false) }
    var dateDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    val importFiles = rememberPhotoImporter()
    val importFolder = rememberFolderPhotoImporter()

    val counts = remember(snap) { snap.datedPhotos.groupingBy { poseKey(it) }.eachCount() }
    val filtered = remember(snap, poseFilter) {
        val base = snap.datedPhotos.reversed()
        when (poseFilter) {
            "All" -> base
            UNSET -> base.filter { it.pose.isBlank() }
            else -> base.filter { it.pose == poseFilter }
        }
    }
    val sections = remember(filtered, groupBy) {
        if (groupBy == "Pose") {
            val byPose = filtered.groupBy { poseKey(it) }
            val order = Poses.all + UNSET
            (order.filter { it in byPose } + byPose.keys.filter { it !in order }).map { k ->
                PhotoSection("p$k", if (k == UNSET) "Pose not set" else k, byPose.getValue(k))
            }
        } else {
            filtered.groupBy { it.date!!.take(7) }.map { (month, list) ->
                PhotoSection("m$month", Dates.monthYear(Dates.parse("$month-01")!!), list)
            }
        }
    }
    val orderedIds = remember(sections) { sections.flatMap { s -> s.photos.map { it.id } } }

    /** Adds every id to the selection (keeping the order), or removes them all if they're all selected already. */
    fun toggleAll(ids: List<Long>) {
        if (ids.isNotEmpty() && selectedSet.containsAll(ids)) selected.removeAll(ids.toSet())
        else ids.forEach { if (it !in selectedSet) selected.add(it) }
    }

    Column(Modifier.fillMaxSize()) {
        if (selected.isEmpty()) {
            PlainTopBar("Photos") {
                IconButton(onClick = { nav.push(Screen.Slideshow()) }, enabled = snap.datedPhotos.isNotEmpty()) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Slideshow and video")
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.Add, contentDescription = "Import photos") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Choose photos…") }, onClick = { menu = false; importFiles() })
                        DropdownMenuItem(text = { Text("Import a whole folder…") }, onClick = { menu = false; importFolder() })
                    }
                }
            }
        } else {
            TopAppBar(
                title = { Text("${selected.size} selected", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = { selected.clear() }) { Icon(Icons.Filled.Close, contentDescription = "Clear selection") } },
                actions = {
                    if (selected.size == 2) TextButton(onClick = {
                        val (a, b) = selected.sortedBy { snap.photosById[it]?.date ?: "" }
                        selected.clear()
                        nav.push(Screen.Compare(a, b))
                    }) { Text("Compare") }
                    TextButton(onClick = { poseDialog = true }) { Text("Pose") }
                    TextButton(onClick = { dateDialog = true }) { Text("Date") }
                    IconButton(onClick = { val ids = selected.toList(); selected.clear(); nav.push(Screen.Slideshow(ids)) }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Slideshow of selected")
                    }
                    IconButton(onClick = { deleteDialog = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                }
            )
            // Selection helper: select everything the current filter shows in one tap.
            val allShown = orderedIds.isNotEmpty() && selectedSet.containsAll(orderedIds)
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Tap photos to add or remove",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { toggleAll(orderedIds) }) {
                    Text(if (allShown) "Deselect all" else "Select all shown (${orderedIds.size})", maxLines = 1)
                }
            }
            GoldHairline()
        }

        if (snap.photos.isEmpty()) {
            EmptyState(
                "No progress photos yet",
                "Import photos in bulk. Each one is matched to its date from the photo's metadata, so it lines up with your FitNotes data."
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = importFiles) { Text("Choose photos") }
                    TextButton(onClick = importFolder) { Text("Import folder") }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 108.dp),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (snap.reviewPhotos.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "review") {
                        Card(
                            onClick = { nav.push(Screen.Review) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                        ) {
                            Text(
                                "${snap.reviewPhotos.size} photo${if (snap.reviewPhotos.size == 1) "" else "s"} need their date checked " +
                                    "(no camera date found) — tap to review",
                                Modifier.padding(12.dp)
                            )
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }, key = "filters") {
                    Column {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (listOf("All") + Poses.all + UNSET).forEach { p ->
                                val n = if (p == "All") snap.datedPhotos.size else counts[p] ?: 0
                                FilterChip(selected = poseFilter == p, onClick = { poseFilter = p }, label = { Text("$p · $n") })
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "GROUP BY",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            listOf("Month", "Pose").forEach { g ->
                                FilterChip(selected = groupBy == g, onClick = { groupBy = g }, label = { Text(g) })
                            }
                        }
                        if (selected.isEmpty()) {
                            Text(
                                "To set a pose on many photos, long-press one or tap Select on a section, then tap Pose.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
                if (sections.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "none") {
                        Text(
                            if (poseFilter == UNSET) "Every photo has a pose." else "No photos with this pose yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp, horizontal = 8.dp)
                        )
                    }
                }
                sections.forEach { section ->
                    item(span = { GridItemSpan(maxLineSpan) }, key = section.key) {
                        val ids = section.photos.map { it.id }
                        val allSel = ids.isNotEmpty() && selectedSet.containsAll(ids)
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                section.title + " · ${section.photos.size}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { toggleAll(ids) }) {
                                Text(
                                    when {
                                        allSel -> "Deselect"
                                        selected.isEmpty() -> "Select"
                                        else -> "Select all"
                                    },
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                    items(section.photos, key = { it.id }) { p ->
                        val isSel = p.id in selectedSet
                        Box(
                            Modifier.aspectRatio(0.75f)
                                .combinedClickable(
                                    onClick = {
                                        if (selected.isNotEmpty()) { if (isSel) selected.remove(p.id) else selected.add(p.id) }
                                        else nav.push(Screen.PhotoViewer(orderedIds, orderedIds.indexOf(p.id).coerceAtLeast(0)))
                                    },
                                    onLongClick = { if (isSel) selected.remove(p.id) else selected.add(p.id) }
                                )
                        ) {
                            PhotoThumb(snap, p, Modifier.fillMaxSize())
                            Text(
                                Dates.short(p.date!!) + if (p.pose.isNotBlank()) " · ${p.pose}" else "",
                                color = Brand.Ivory,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.align(Alignment.BottomStart)
                                    .background(Brand.Black.copy(alpha = 0.6f), RoundedCornerShape(topEnd = 6.dp)).padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                            if (isSel) {
                                Box(Modifier.fillMaxSize().border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)))
                                Icon(
                                    Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (poseDialog) PoseDialog(onDismiss = { poseDialog = false }, count = selected.size) { pose ->
        val ids = selected.toList(); selected.clear()
        AppScope.scope.launch { Store.setPhotoPose(ids, pose) }
    }
    if (dateDialog) {
        val first = selected.firstOrNull()?.let { snap.photosById[it]?.date }
        PickDateDialog(first, onDismiss = { dateDialog = false }) { d ->
            val ids = selected.toList(); selected.clear()
            AppScope.scope.launch { Store.setPhotoDate(ids, d) }
        }
    }
    if (deleteDialog) ConfirmDialog(
        "Delete ${selected.size} photos?",
        "They're removed from FitLens only — the originals on your phone aren't touched.",
        onDismiss = { deleteDialog = false }
    ) {
        val ids = selected.toList(); selected.clear()
        AppScope.scope.launch { Store.deletePhotos(ids) }
    }
}

/** Front / Side / Back / Other / Not set, as full-width buttons. */
@Composable
private fun PoseOptions(onPick: (String) -> Unit) {
    Column {
        (Poses.all + NOT_SET).forEach { p ->
            OutlinedButton(
                onClick = { onPick(if (p == NOT_SET) Poses.NONE else p) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
            ) { Text(p) }
        }
    }
}

/** Sets the pose of one or more existing photos. */
@Composable
fun PoseDialog(onDismiss: () -> Unit, count: Int = 0, onPick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (count > 1) "Set pose for $count photos" else "Set pose") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PoseOptions { pose -> onPick(pose); onDismiss() }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Asked before every bulk photo import: the pose is applied to all the photos being added. */
@Composable
fun ImportPoseDialog(count: Int, onCancel: () -> Unit, onPick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(if (count == 1) "Pose for this photo" else "Pose for these $count photos") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Applied to every photo in this import. You can change it later for one photo or many.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                PoseOptions(onPick)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel import") } }
    )
}

/** Photos whose date came from the file's modified time or wasn't found at all. */
@Composable
fun ReviewScreen(snap: Snapshot, nav: Nav) {
    val list = snap.reviewPhotos
    var editing by remember { mutableStateOf<Photo?>(null) }
    Column(Modifier.fillMaxSize()) {
        BackTopBar("Check photo dates", onBack = { nav.pop() }) {
            if (list.any { it.date != null }) TextButton(onClick = {
                val ids = list.filter { it.date != null }.map { it.id }
                AppScope.scope.launch { Store.confirmPhotoDates(ids) }
            }) { Text("Accept all") }
        }
        Text(
            "These photos had no camera date in their metadata, so FitLens used the file date or found nothing. " +
                "Accept the date if it's right, or set the correct one.",
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (list.isEmpty()) {
            EmptyState("All photo dates checked", "Every photo is matched to a date.") {
                TextButton(onClick = { nav.pop() }) { Text("Back to photos") }
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list.size, key = { list[it].id }) { i ->
                    val p = list[i]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PhotoThumb(snap, p, Modifier.padding(end = 12.dp).size(width = 84.dp, height = 112.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.date?.let { Dates.long(it) } ?: "No date", style = MaterialTheme.typography.titleMedium)
                            Text(p.originalName ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(com.fitlens.companion.data.DateSources.label(p.dateSource), style = MaterialTheme.typography.bodySmall)
                            Row {
                                if (p.date != null) TextButton(onClick = { AppScope.scope.launch { Store.confirmPhotoDates(listOf(p.id)) } }) { Text("Looks right") }
                                TextButton(onClick = { editing = p }) { Text("Set date") }
                            }
                        }
                    }
                }
            }
        }
    }
    editing?.let { p ->
        PickDateDialog(p.date, onDismiss = { editing = null }) { d ->
            AppScope.scope.launch { Store.setPhotoDate(listOf(p.id), d) }
        }
    }
}
