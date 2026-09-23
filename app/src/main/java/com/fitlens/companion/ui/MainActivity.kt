package com.fitlens.companion.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.fitlens.companion.data.AutoBackup
import com.fitlens.companion.data.BackupSync
import com.fitlens.companion.data.Backups
import com.fitlens.companion.data.FileKind
import com.fitlens.companion.data.FitNotesImporter
import com.fitlens.companion.data.Store
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Timeline : Screen
    data object Calendar : Screen
    data object Body : Screen
    data object Training : Screen
    data object Photos : Screen
    data object Sync : Screen
    data class Day(val date: String) : Screen
    data class ExerciseDetail(val id: Long) : Screen
    data class PhotoViewer(val ids: List<Long>, val index: Int) : Screen
    data class Compare(val a: Long, val b: Long) : Screen
    data class Slideshow(val ids: List<Long>? = null) : Screen
    data object Review : Screen
}

class Nav {
    val stack = mutableStateListOf<Screen>(Screen.Timeline)
    val top: Screen get() = stack.last()
    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) else if (top != Screen.Timeline) tab(Screen.Timeline) }
    fun tab(s: Screen) { stack.clear(); stack.add(s) }
}

private data class TabItem(val screen: Screen, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem(Screen.Timeline, "Log", Icons.Filled.Home),
    TabItem(Screen.Calendar, "Calendar", Icons.Filled.DateRange),
    TabItem(Screen.Body, "Body", Icons.Filled.Person),
    TabItem(Screen.Training, "Training", Icons.Filled.Star),
    TabItem(Screen.Photos, "Photos", Icons.Filled.Face),
    TabItem(Screen.Sync, "Sync", Icons.Filled.Refresh)
)

class MainActivity : ComponentActivity() {

    private val nav = Nav()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { FitLensTheme { AppRoot(nav) } }
        lifecycleScope.launch {
            Store.reload()
            if (savedInstanceState == null) handleIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        lifecycleScope.launch { handleIntent(intent) }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            if (BackupSync.autoSyncEnabled() && BackupSync.folder() != null && UiEvents.busy.value == null) {
                UiEvents.busy.value = "Checking for a new FitNotes backup…"
                try {
                    BackupSync.syncIfNewer(this@MainActivity)?.let { UiEvents.show(it.message) }
                } finally {
                    UiEvents.busy.value = null
                }
            }
            // Automatic local backup when one is due. It runs quietly and only speaks up if something goes wrong.
            if (UiEvents.busy.value == null) {
                val app = applicationContext
                AppScope.scope.launch {
                    Backups.autoBackupIfDue(app)?.takeIf { !it.ok }?.let { UiEvents.show(it.message) }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // "Back up after changes" (#34): queue a background backup when FitLens leaves the screen.
        AutoBackup.onAppBackground(applicationContext)
    }

    private suspend fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra(AutoBackup.EXTRA_OPEN_BACKUPS, false)) {
            // Opened from the "backup folder unavailable" notification.
            intent.removeExtra(AutoBackup.EXTRA_OPEN_BACKUPS)
            nav.tab(Screen.Sync)
            return
        }
        val uris = ArrayList<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { uris.add(it) }
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { uris.addAll(it) }
            Intent.ACTION_VIEW -> intent.data?.let { uris.add(it) }
            else -> return
        }
        intent.action = null // don't re-import on configuration changes
        if (uris.isEmpty()) return
        val images = ArrayList<Uri>()
        for (u in uris) {
            when (FitNotesImporter.sniff(this, u)) {
                FileKind.IMAGE -> images.add(u)
                FileKind.FITNOTES_BACKUP -> {
                    // The Sync tab shows what the backup adds before importing it.
                    nav.tab(Screen.Sync)
                    FitNotesImports.pending.value = u
                }
                FileKind.BODY_CSV -> UiEvents.show(FitNotesImporter.importBodyCsv(this, u).message)
                FileKind.WORKOUT_CSV -> UiEvents.show("Workout CSVs aren't needed — share a FitNotes backup (.fitnotes) instead; it contains everything.")
                FileKind.ARCHIVE -> {
                    // A .fitlens backup: the Sync tab checks it and asks before restoring.
                    nav.tab(Screen.Sync)
                    UiEvents.pendingRestore.value = u
                }
                FileKind.UNKNOWN -> UiEvents.show("FitLens doesn't recognise that file.")
            }
        }
        if (images.isNotEmpty()) {
            // Shared photos ask for their pose first (PhotoImportHost), then import.
            PhotoImports.request(PendingPhotoImport(images) { r -> if (r.needsReview > 0) nav.tab(Screen.Photos) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppRoot(nav: Nav) {
    val snap by Store.snapshot.collectAsState()
    val busy by UiEvents.busy.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        UiEvents.messages.collect { snackbar.showSnackbar(it) }
    }
    val top = nav.top
    val onTab = tabs.any { it.screen == top }
    BackHandler(enabled = nav.stack.size > 1 || top != Screen.Timeline) { nav.pop() }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (onTab) {
                Column {
                    GoldHairline()
                    NavigationBar(containerColor = Brand.Black) {
                        tabs.forEach { t ->
                            NavigationBarItem(
                                selected = top == t.screen,
                                onClick = { nav.tab(t.screen) },
                                icon = { Icon(t.icon, contentDescription = t.label) },
                                label = { Text(t.label.uppercase(), maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Brand.GoldLight,
                                    selectedTextColor = Brand.Gold,
                                    indicatorColor = Brand.ImperialPurple,
                                    unselectedIconColor = Brand.Muted,
                                    unselectedTextColor = Brand.Muted
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner).consumeWindowInsets(inner)) {
            val s = snap
            if (s == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                when (top) {
                    Screen.Timeline -> TimelineScreen(s, nav)
                    Screen.Calendar -> CalendarScreen(s, nav)
                    Screen.Body -> BodyScreen(s, nav)
                    Screen.Training -> TrainingScreen(s, nav)
                    Screen.Photos -> PhotosScreen(s, nav)
                    Screen.Sync -> SyncScreen(s, nav)
                    is Screen.Day -> DayScreen(s, nav, top.date)
                    is Screen.ExerciseDetail -> ExerciseDetailScreen(s, nav, top.id)
                    is Screen.PhotoViewer -> PhotoViewerScreen(s, nav, top.ids, top.index)
                    is Screen.Compare -> CompareScreen(s, nav, top.a, top.b)
                    is Screen.Slideshow -> SlideshowScreen(s, nav, top.ids)
                    Screen.Review -> ReviewScreen(s, nav)
                }
            }
            if (busy != null) {
                Box(
                    Modifier.fillMaxSize().background(Color(0x99000000))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                    contentAlignment = Alignment.Center
                ) {
                    Card {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Text(busy ?: "", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 16.dp))
                        }
                    }
                }
            }
            PhotoImportHost()
        }
    }
}
