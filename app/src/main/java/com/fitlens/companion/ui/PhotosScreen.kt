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
import android.content.res.Resources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.fitlens.companion.R

/** Filter / group key for photos without a pose. */
private const val UNSET = "Unset"
/** Label for "no pose" in pose pickers. */
private const val NOT_SET = "Not set"

private fun poseKey(p: Photo): String = p.pose.ifBlank { UNSET }

/** A titled block of photos in the Photos grid: one day, week, month or year, or one pose. */
private class PhotoSection(val key: String, val title: String, val photos: List<Photo>)

/** The heading of a date section starting on [start], grouped by [group] (#46). */
private fun sectionTitle(res: Resources, start: LocalDate, group: String): String = when (group) {
    MediaPrefs.GROUP_DAY -> Dates.long(start.toString())
    MediaPrefs.GROUP_WEEK -> res.getString(R.string.ph_week_of, Dates.medium(start.toString()))
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
    val res = LocalContext.current.resources
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
                PhotoSection("p$k", if (k == UNSET) res.getString(R.string.ph_pose_not_set) else poseText(res, k), byPose.getValue(k))
            }
        } else {
            // The photos are newest first, so the sections are too.
            filtered.groupBy { p ->
                Dates.parse(p.date)?.let { MediaPrefs.sectionStart(it, groupBy, prefs.weekStart) } ?: LocalDate.MIN
            }.map { (start, list) -> PhotoSection("d$groupBy$start", sectionTitle(res, start, groupBy), list) }
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
                title = stringResource(R.string.ph_title),
                onBack = LocalNavBack.current,
                actions = listOf(
                    TopBarAction(Icons.Filled.Add, stringResource(R.string.ph_import)) { importFiles() },
                    TopBarAction(Icons.Filled.PlayArrow, stringResource(R.string.ph_slideshow), enabled = snap.datedPhotos.isNotEmpty()) {
                        nav.push(Screen.Slideshow())
                    }
                ),
                overflow = buildList {
                    add(MenuAction(stringResource(R.string.ph_import_folder)) { importFolder() })
                    if (snap.reviewPhotos.isNotEmpty()) {
                        add(MenuAction(stringResource(R.string.ph_check_dates_n, snap.reviewPhotos.size)) { nav.push(Screen.Review) })
                    }
                    add(MenuAction(stringResource(R.string.ph_media_settings)) { nav.push(Screen.SettingsPage(SettingsSection.Media)) })
                }
            )
        } else {
            val allShown = orderedIds.isNotEmpty() && selectedSet.containsAll(orderedIds)
            FitTopBar(
                title = stringResource(R.string.ph_selected, selected.size),
                centered = false,
                navigation = TopBarAction(Icons.Filled.Close, stringResource(R.string.ph_clear_selection)) { selected.clear() },
                actions = listOf(
                    TopBarAction(FitIcons.Compare, stringResource(R.string.ph_compare_two), enabled = selected.size == 2) {
                        val (a, b) = selected.sortedBy { snap.photosById[it]?.date ?: "" }
                        selected.clear()
                        nav.push(Screen.Compare(a, b))
                    },
                    TopBarAction(Icons.Filled.PlayArrow, stringResource(R.string.ph_slideshow_selected)) {
                        val ids = selected.toList(); selected.clear(); nav.push(Screen.Slideshow(ids))
                    },
                    TopBarAction(Icons.Filled.Delete, stringResource(R.string.ph_delete_selected)) { deleteDialog = true }
                ),
                overflow = listOf(
                    MenuAction(stringResource(R.string.ph_set_pose_menu)) { poseDialog = true },
                    MenuAction(stringResource(R.string.ph_change_date_menu)) { dateDialog = true },
                    MenuAction(if (allShown) stringResource(R.string.ph_deselect_all) else stringResource(R.string.ph_select_all_shown, orderedIds.size)) { toggleAll(orderedIds) }
                )
            )
        }

        if (snap.photos.isEmpty()) {
            EmptyState(
                stringResource(R.string.ph_empty_title),
                stringResource(R.string.ph_empty_body)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoldButton(onClick = importFiles) { Text(stringResource(R.string.ph_choose_photos)) }
                    GlassOutlinedButton(onClick = importFolder) { Text(stringResource(R.string.ph_import_folder_short)) }
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
                                .clickable(onClickLabel = stringResource(R.string.ph_review_dates)) { nav.push(Screen.Review) }
                        ) {
                            Text(
                                pluralStringResource(R.plurals.ph_need_check, snap.reviewPhotos.size, snap.reviewPhotos.size),
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
                                label = stringResource(R.string.ph_pose),
                                options = poses.map { p ->
                                    val n = if (p == "All") snap.datedPhotos.size else counts[p] ?: 0
                                    val name = when (p) {
                                        "All" -> res.getString(R.string.ph_all_poses)
                                        UNSET -> res.getString(R.string.ph_pose_not_set)
                                        else -> poseText(res, p)
                                    }
                                    res.getString(R.string.ph_name_count, name, n)
                                },
                                selected = poses.indexOf(poseFilter).coerceAtLeast(0)
                            ) { poseFilter = poses[it] }
                            val groups = MediaPrefs.GROUPS
                            DropdownPill(
                                label = stringResource(R.string.ph_group_by),
                                options = groups.map { res.getString(R.string.ph_by_group, groupText(res, it).lowercase()) },
                                selected = groups.indexOf(groupBy).coerceAtLeast(0)
                            ) { i -> Settings.updatePortable { it.copy(photoGroupBy = groups[i]) } }
                        }
                        if (selected.isEmpty()) {
                            Text(
                                stringResource(R.string.ph_hint_select),
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
                            if (poseFilter == UNSET) stringResource(R.string.ph_every_has_pose) else stringResource(R.string.ph_none_with_pose),
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
                            SectionLabel(stringResource(R.string.ph_name_count, section.title, section.photos.size), Modifier.weight(1f))
                            TextButton(onClick = { toggleAll(ids) }) {
                                Text(
                                    when {
                                        allSel -> stringResource(R.string.ph_deselect)
                                        selected.isEmpty() -> stringResource(R.string.ph_select)
                                        else -> stringResource(R.string.ph_select_all)
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
                                        selected.isEmpty() -> stringResource(R.string.ph_open)
                                        isSel -> stringResource(R.string.ph_deselect)
                                        else -> stringResource(R.string.ph_select)
                                    },
                                    onLongClickLabel = if (isSel) stringResource(R.string.ph_deselect) else stringResource(R.string.ph_select),
                                    onClick = {
                                        if (selected.isNotEmpty()) { if (isSel) selected.remove(p.id) else selected.add(p.id) }
                                        else nav.push(Screen.PhotoViewer(orderedIds, orderedIds.indexOf(p.id).coerceAtLeast(0)))
                                    },
                                    onLongClick = { if (isSel) selected.remove(p.id) else selected.add(p.id) }
                                )
                        ) {
                            PhotoThumb(snap, p, Modifier.fillMaxSize())
                            Text(
                                Dates.short(p.date!!) + if (p.pose.isNotBlank()) " · ${poseText(res, p.pose)}" else "",
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
        title = pluralStringResource(R.plurals.ph_delete_n, selected.size, selected.size),
        message = stringResource(R.string.ph_delete_body),
        confirmLabel = stringResource(R.string.ph_delete_confirm),
        onDismiss = { deleteDialog = false },
        onConfirm = {
            val ids = selected.toList(); selected.clear()
            AppScope.scope.launch { Store.deletePhotos(ids) }
        }
    )
}

/** Front / Side / Back / Other / Not set, as full-width buttons. */
@Composable
private fun PoseOptions(onPick: (String) -> Unit) {
    val res = LocalContext.current.resources
    Column {
        (Poses.all + NOT_SET).forEach { p ->
            GlassOutlinedButton(
                onClick = { onPick(if (p == NOT_SET) Poses.NONE else p) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
            ) { Text(if (p == NOT_SET) res.getString(R.string.pose_not_set) else poseText(res, p)) }
        }
    }
}

/** Sets the pose of one or more existing photos, as a FitLens sheet (#92). */
@Composable
fun PoseDialog(onDismiss: () -> Unit, count: Int = 0, onPick: (String) -> Unit) {
    FitSheet(title = if (count > 1) pluralStringResource(R.plurals.ph_set_pose_n, count, count) else stringResource(R.string.ph_set_pose), onDismiss = onDismiss) {
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
        title = pluralStringResource(R.plurals.ph_import_pose_n, count, count),
        onDismiss = onCancel,
        dismissLabel = stringResource(R.string.ph_cancel_import)
    ) {
        Text(
            stringResource(R.string.ph_import_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PoseOptions(onPick)
    }
}

/** Photos whose date came from the file's modified time or wasn't found at all. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(snap: Snapshot, nav: Nav) {
    val list = snap.reviewPhotos
    var editing by remember { mutableStateOf<Photo?>(null) }
    Column(Modifier.fillMaxSize()) {
        FitTopBar(
            title = stringResource(R.string.ph_check_dates),
            onBack = { nav.pop() },
            actions = if (list.any { it.date != null }) listOf(
                TopBarAction(Icons.Filled.Done, stringResource(R.string.ph_accept_all)) {
                    val ids = list.filter { it.date != null }.map { it.id }
                    AppScope.scope.launch { Store.confirmPhotoDates(ids) }
                }
            ) else emptyList()
        )
        Text(
            stringResource(R.string.ph_review_body),
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (list.isEmpty()) {
            EmptyState(stringResource(R.string.ph_all_checked), stringResource(R.string.ph_all_checked_body)) {
                GlassOutlinedButton(onClick = { nav.pop() }) { Text(stringResource(R.string.ph_back_to_photos)) }
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list.size, key = { list[it].id }) { i ->
                    val p = list[i]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PhotoThumb(snap, p, Modifier.padding(end = 12.dp).size(width = 84.dp, height = 112.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.date?.let { Dates.long(it) } ?: stringResource(R.string.ph_no_date), style = MaterialTheme.typography.titleMedium)
                            Text(p.originalName ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(dateSourceText(LocalContext.current.resources, p.dateSource), style = MaterialTheme.typography.bodySmall)
                            Row {
                                if (p.date != null) TextButton(onClick = { AppScope.scope.launch { Store.confirmPhotoDates(listOf(p.id)) } }) { Text(stringResource(R.string.ph_looks_right)) }
                                TextButton(onClick = { editing = p }) { Text(stringResource(R.string.ph_set_date)) }
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
