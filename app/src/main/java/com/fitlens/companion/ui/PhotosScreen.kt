package com.fitlens.companion.ui

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.clickable
import com.fitlens.companion.ui.design.SectionLabel
import com.fitlens.companion.ui.design.raisedGlass
import com.fitlens.companion.ui.design.GlassOutlinedButton
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.Photo
import com.fitlens.companion.data.Poses
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Store
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Done
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.fitlens.companion.data.MediaPrefs
import com.fitlens.companion.data.Settings
import com.fitlens.companion.ui.design.ConfirmSheet
import com.fitlens.companion.ui.design.FitIcons
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.FitTopBar
import com.fitlens.companion.ui.design.GoldButton
import com.fitlens.companion.ui.design.MenuAction
import com.fitlens.companion.ui.design.TopBarAction
import java.time.LocalDate
import com.fitlens.companion.ui.design.DropdownPill

/** Filter / group key for photos without a pose. */
private const val UNSET = "Unset"
/** Label for "no pose" in pose pickers. */
private const val NOT_SET = "Not set"

private fun poseKey(p: Photo): String = p.pose.ifBlank { UNSET }

/** A titled block of photos in the Photos grid: one day, week, month or year, or one pose. */
private class PhotoSection(val key: String, val title: String, val photos: List<Photo>)

/** The heading of a date section starting on [start], grouped by [group] (#46). */
private fun sectionTitle(start: LocalDate, group: String): String = when (group) {
    MediaPrefs.GROUP_DAY -> Dates.long(start.toString())
    MediaPrefs.GROUP_WEEK -> "Week of ${Dates.medium(start.toString())}"
    MediaPrefs.GROUP_YEAR -> start.year.toString()
    else -> Dates.monthYear(start)
}

/**
 * The Photos gallery (#92): the FitLens top bar with icon actions and an overflow menu, which becomes a selection bar
 * with the count while photos are selected. Sections follow "Group photos by" (#46), remembered in Settings.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PhotosScreen(snap: Snapshot, nav: Nav) {
    val prefs by Settings.portable.collectAsState()
    var poseFilter by rememberSaveable { mutableStateOf("All") }
    val groupBy = prefs.photoGroupBy
    // The selection and open sheets come back after rotation or a restart in the background (#37).
    val state = viewModel<PhotosState>()
    val selected = state.savedIds("selected")
    val selectedSet by remember { derivedStateOf { selected.toHashSet() } }
    var poseDialog by state.saved("poseDialog", false)
    var dateDialog by state.saved("dateDialog", false)
    var deleteDialog by state.saved("deleteDialog", false)
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
    val sections = remember(filtered, groupBy, prefs.weekStart) {
        if (groupBy == MediaPrefs.GROUP_POSE) {
            val byPose = filtered.groupBy { poseKey(it) }
            val order = Poses.all + UNSET
            (order.filter { it in byPose } + byPose.keys.filter { it !in order }).map { k ->
                PhotoSection("p$k", if (k == UNSET) "Pose not set" else k, byPose.getValue(k))
            }
        } else {
            // The photos are newest first, so the sections are too.
            filtered.groupBy { p ->
                Dates.parse(p.date)?.let { MediaPrefs.sectionStart(it, groupBy, prefs.weekStart) } ?: LocalDate.MIN
            }.map { (start, list) -> PhotoSection("d$groupBy$start", sectionTitle(start, groupBy), list) }
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
            FitTopBar(
                title = "Photos",
                onBack = LocalNavBack.current,
                actions = listOf(
                    TopBarAction(Icons.Filled.Add, "Import photos") { importFiles() },
                    TopBarAction(Icons.Filled.PlayArrow, "Slideshow and video", enabled = snap.datedPhotos.isNotEmpty()) {
                        nav.push(Screen.Slideshow())
                    }
                ),
                overflow = buildList {
                    add(MenuAction("Import a whole folder…") { importFolder() })
                    if (snap.reviewPhotos.isNotEmpty()) {
                        add(MenuAction("Check photo dates (${snap.reviewPhotos.size})") { nav.push(Screen.Review) })
                    }
                    add(MenuAction("Photo and media settings") { nav.push(Screen.SettingsPage(SettingsSection.Media)) })
                }
            )
        } else {
            val allShown = orderedIds.isNotEmpty() && selectedSet.containsAll(orderedIds)
            FitTopBar(
                title = "${selected.size} selected",
                centered = false,
                navigation = TopBarAction(Icons.Filled.Close, "Clear selection") { selected.clear() },
                actions = listOf(
                    TopBarAction(FitIcons.Compare, "Compare the two selected photos", enabled = selected.size == 2) {
                        val (a, b) = selected.sortedBy { snap.photosById[it]?.date ?: "" }
                        selected.clear()
                        nav.push(Screen.Compare(a, b))
                    },
                    TopBarAction(Icons.Filled.PlayArrow, "Slideshow of the selected photos") {
                        val ids = selected.toList(); selected.clear(); nav.push(Screen.Slideshow(ids))
                    },
                    TopBarAction(Icons.Filled.Delete, "Delete the selected photos") { deleteDialog = true }
                ),
                overflow = listOf(
                    MenuAction("Set pose…") { poseDialog = true },
                    MenuAction("Change date…") { dateDialog = true },
                    MenuAction(if (allShown) "Deselect all" else "Select all shown (${orderedIds.size})") { toggleAll(orderedIds) }
                )
            )
        }

        if (snap.photos.isEmpty()) {
            EmptyState(
                "No progress photos yet",
                "Import photos in bulk. Each one is matched to its date from the photo's metadata, so it lines up with your training."
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoldButton(onClick = importFiles) { Text("Choose photos") }
                    GlassOutlinedButton(onClick = importFolder) { Text("Import folder") }
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
                        // A raised glass card, as the rest of the app draws a tappable notice (#146).
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp)
                                .raisedGlass(FitShapes.card)
                                .clickable(onClickLabel = "Review dates") { nav.push(Screen.Review) }
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
                        // One compact row (#115): which poses, and how the grid is grouped (remembered, #46).
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val poses = listOf("All") + Poses.all + UNSET
                            DropdownPill(
                                label = "Pose",
                                options = poses.map { p ->
                                    val n = if (p == "All") snap.datedPhotos.size else counts[p] ?: 0
                                    val name = when (p) { "All" -> "All poses"; UNSET -> "Pose not set"; else -> p }
                                    "$name · $n"
                                },
                                selected = poses.indexOf(poseFilter).coerceAtLeast(0)
                            ) { poseFilter = poses[it] }
                            val groups = MediaPrefs.GROUPS
                            DropdownPill(
                                label = "Group by",
                                options = groups.map { "By ${MediaPrefs.groupLabel(it).lowercase()}" },
                                selected = groups.indexOf(groupBy).coerceAtLeast(0)
                            ) { i -> Settings.updatePortable { it.copy(photoGroupBy = groups[i]) } }
                        }
                        if (selected.isEmpty()) {
                            Text(
                                "Long-press a photo, or tap Select on a section, to choose several and set their pose or date.",
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
                        // Each section under a FitNotes heading (#146), with Select beside it.
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            SectionLabel(section.title + " · ${section.photos.size}", Modifier.weight(1f))
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
                                .clip(FitShapes.row)
                                .semantics { this.selected = isSel }
                                .combinedClickable(
                                    onClickLabel = when {
                                        selected.isEmpty() -> "Open"
                                        isSel -> "Deselect"
                                        else -> "Select"
                                    },
                                    onLongClickLabel = if (isSel) "Deselect" else "Select",
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
                                Box(Modifier.fillMaxSize().border(3.dp, MaterialTheme.colorScheme.primary, FitShapes.row))
                                Icon(
                                    Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary,
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
    if (deleteDialog) ConfirmSheet(
        title = "Delete ${selected.size} photo${if (selected.size == 1) "" else "s"}?",
        message = "They're removed from FitLens only — the originals on your phone aren't touched.",
        confirmLabel = "Delete photos",
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
            GlassOutlinedButton(
                onClick = { onPick(if (p == NOT_SET) Poses.NONE else p) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
            ) { Text(p) }
        }
    }
}

/** Sets the pose of one or more existing photos, as a FitLens sheet (#92). */
@Composable
fun PoseDialog(onDismiss: () -> Unit, count: Int = 0, onPick: (String) -> Unit) {
    FitSheet(title = if (count > 1) "Set pose for $count photos" else "Set pose", onDismiss = onDismiss) {
        PoseOptions { pose -> onPick(pose); onDismiss() }
    }
}

/**
 * Asked before a photo import, one photo or many (#92): the pose is applied to every photo being added. Settings →
 * Progress photos and media can choose a pose for new photos instead, and then this isn't asked (#46).
 */
@Composable
fun ImportPoseDialog(count: Int, onCancel: () -> Unit, onPick: (String) -> Unit) {
    FitSheet(
        title = if (count == 1) "Pose for this photo" else "Pose for these $count photos",
        onDismiss = onCancel,
        dismissLabel = "Cancel import"
    ) {
        Text(
            "Applied to every photo in this import. You can change it later for one photo or many, or choose a pose " +
                "for every new photo in Settings → Progress Photos & Media.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PoseOptions(onPick)
    }
}

/** Photos whose date came from the file's modified time or wasn't found at all. */
@Composable
fun ReviewScreen(snap: Snapshot, nav: Nav) {
    val list = snap.reviewPhotos
    var editing by remember { mutableStateOf<Photo?>(null) }
    Column(Modifier.fillMaxSize()) {
        FitTopBar(
            title = "Check photo dates",
            onBack = { nav.pop() },
            actions = if (list.any { it.date != null }) listOf(
                TopBarAction(Icons.Filled.Done, "Accept every date shown") {
                    val ids = list.filter { it.date != null }.map { it.id }
                    AppScope.scope.launch { Store.confirmPhotoDates(ids) }
                }
            ) else emptyList()
        )
        Text(
            "These photos had no camera date in their metadata, so FitLens used the file date or found nothing. " +
                "Accept the date if it's right, or set the correct one.",
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (list.isEmpty()) {
            EmptyState("All photo dates checked", "Every photo is matched to a date.") {
                GlassOutlinedButton(onClick = { nav.pop() }) { Text("Back to photos") }
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
