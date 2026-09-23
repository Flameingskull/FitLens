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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.Photo
import com.fitlens.companion.data.Poses
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Store
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PhotosScreen(snap: Snapshot, nav: Nav) {
    var poseFilter by rememberSaveable { mutableStateOf("All") }
    val selected = remember { mutableStateListOf<Long>() }
    var menu by remember { mutableStateOf(false) }
    var poseDialog by remember { mutableStateOf(false) }
    var dateDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    val importFiles = rememberPhotoImporter()
    val importFolder = rememberFolderPhotoImporter()

    val filtered = remember(snap, poseFilter) {
        val base = snap.datedPhotos.reversed()
        when (poseFilter) {
            "All" -> base
            "Unset" -> base.filter { it.pose.isBlank() }
            else -> base.filter { it.pose == poseFilter }
        }
    }
    val byMonth = remember(filtered) { filtered.groupBy { it.date!!.take(7) } }
    val orderedIds = remember(filtered) { filtered.map { it.id } }

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
                title = { Text("${selected.size} selected") },
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
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (snap.reviewPhotos.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
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
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (listOf("All") + Poses.all + "Unset").forEach { p ->
                            FilterChip(selected = poseFilter == p, onClick = { poseFilter = p }, label = { Text(p) })
                        }
                    }
                }
                byMonth.forEach { (month, list) ->
                    item(span = { GridItemSpan(maxLineSpan) }, key = "m$month") {
                        Text(
                            Dates.monthYear(Dates.parse("$month-01")!!) + " · ${list.size}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                        )
                    }
                    items(list, key = { it.id }) { p ->
                        val isSel = p.id in selected
                        Box(
                            Modifier.aspectRatio(0.75f)
                                .combinedClickable(
                                    onClick = {
                                        if (selected.isNotEmpty()) { if (isSel) selected.remove(p.id) else selected.add(p.id) }
                                        else nav.push(Screen.PhotoViewer(orderedIds, orderedIds.indexOf(p.id)))
                                    },
                                    onLongClick = { if (isSel) selected.remove(p.id) else selected.add(p.id) }
                                )
                        ) {
                            PhotoThumb(snap, p, Modifier.fillMaxSize())
                            Text(
                                Dates.short(p.date!!) + if (p.pose.isNotBlank()) " · ${p.pose}" else "",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.align(Alignment.BottomStart)
                                    .background(Color(0x88000000), RoundedCornerShape(topEnd = 6.dp)).padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                            if (isSel) {
                                Box(Modifier.fillMaxSize().border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)))
                                Icon(
                                    Icons.Filled.Check, contentDescription = "Selected", tint = Color.White,
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

    if (poseDialog) PoseDialog(onDismiss = { poseDialog = false }) { pose ->
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

@Composable
fun PoseDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set pose") },
        text = {
            Column {
                (Poses.all + "None").forEach { p ->
                    TextButton(onClick = { onPick(if (p == "None") Poses.NONE else p); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                        Text(p, Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
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
