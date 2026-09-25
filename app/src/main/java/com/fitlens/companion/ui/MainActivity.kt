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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Timeline : Screen
    data object Calendar : Screen
    data object Body : Screen
    data object Training : Screen
    data object Photos : Screen
    data object Sync : Screen
    data class Day(val date: String) : Screen
    data object Library : Screen
    /** Logging sets for one exercise on one day (#16). */
    data class SetEntry(val date: String, val exerciseId: Long) : Screen
    data class ExerciseDetail(val id: Long) : Screen
    data class PhotoViewer(val ids: List<Long>, val index: Int) : Screen
    data class Compare(val a: Long, val b: Long) : Screen
    data class Slideshow(val ids: List<Long>? = null) : Screen
    data object Review : Screen
    /** The main Settings screen, opened from the gear on every tab (#38). */
    data object SettingsHome : Screen
    data class SettingsPage(val section: SettingsSection) : Screen
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
            // A result the user hadn't read when the app was closed stays readable in Settings → Backups (#62).
            UiEvents.loadLastResult()
            // Safety copies (#47) are kept for a limited time only.
            Backups.pruneSafety(applicationContext)
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
                    BackupSync.syncIfNewer(this@MainActivity)?.let { UiEvents.show(it.message, it.level()) }
                } finally {
                    UiEvents.busy.value = null
                }
            }
            // Automatic local backup when one is due. It runs quietly and only speaks up if something goes wrong.
            if (UiEvents.busy.value == null) {
                val app = applicationContext
                AppScope.scope.launch {
                    Backups.autoBackupIfDue(app)?.takeIf { !it.ok }?.let { UiEvents.show(it.message, ResultLevel.Failure) }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // "Back up after changes" (#34): queue a background backup when FitLens leaves the screen.
        AutoBackup.onAppBackground(applicationContext)
    }

    /** Settings → Backups, with Back leading to Settings and then the Log tab. */
    private fun openBackups() {
        nav.tab(Screen.Timeline)
        nav.push(Screen.SettingsHome)
        nav.push(Screen.SettingsPage(SettingsSection.Backups))
    }

    private suspend fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra(AutoBackup.EXTRA_OPEN_BACKUPS, false)) {
            // Opened from the "backup folder unavailable" notification.
            intent.removeExtra(AutoBackup.EXTRA_OPEN_BACKUPS)
            openBackups()
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
                FileKind.BODY_CSV -> FitNotesImporter.importBodyCsv(this, u).let { UiEvents.show(it.message, it.level()) }
                FileKind.WORKOUT_CSV -> UiEvents.show("Workout CSVs aren't needed — share a FitNotes backup (.fitnotes) instead; it contains everything.")
                FileKind.ARCHIVE -> {
                    // A .fitlens backup: Settings → Backups checks it and asks before restoring.
                    openBackups()
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
    // A failure or warning waiting to be acknowledged (#62). Messages queue behind it until it is closed.
    var toAcknowledge by remember { mutableStateOf<UiMessage?>(null) }
    LaunchedEffect(Unit) {
        UiEvents.messages.collect { m ->
            if (m.mustAcknowledge) {
                toAcknowledge = m
                snapshotFlow { toAcknowledge }.first { it == null }
                return@collect
            }
            // An action (Undo) gets the longer duration, never Indefinite: the collector waits for each message.
            val result = snackbar.showSnackbar(
                message = m.text,
                actionLabel = m.actionLabel,
                withDismissAction = false,
                duration = if (m.actionLabel == null) SnackbarDuration.Short else SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) m.onAction?.invoke()
        }
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
                val openSettings = remember(nav) { { nav.push(Screen.SettingsHome) } }
                CompositionLocalProvider(LocalOpenSettings provides openSettings) {
                when (top) {
                    Screen.Timeline -> TimelineScreen(s, nav)
                    Screen.Calendar -> CalendarScreen(s, nav)
                    Screen.Body -> BodyScreen(s, nav)
                    Screen.Training -> TrainingScreen(s, nav)
                    Screen.Photos -> PhotosScreen(s, nav)
                    Screen.Sync -> SyncScreen(s, nav)
                    is Screen.Day -> DayScreen(s, nav, top.date)
                    Screen.Library -> ExerciseLibraryScreen(s, nav)
                    is Screen.SetEntry -> SetEntryScreen(s, nav, top.date, top.exerciseId)
                    is Screen.ExerciseDetail -> ExerciseDetailScreen(s, nav, top.id)
                    is Screen.PhotoViewer -> PhotoViewerScreen(s, nav, top.ids, top.index)
                    is Screen.Compare -> CompareScreen(s, nav, top.a, top.b)
                    is Screen.Slideshow -> SlideshowScreen(s, nav, top.ids)
                    Screen.Review -> ReviewScreen(s, nav)
                    Screen.SettingsHome -> SettingsScreen(nav)
                    is Screen.SettingsPage -> SettingsPageScreen(s, nav, top.section)
                }
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
            toAcknowledge?.let { m -> ResultDialog(m) { toAcknowledge = null } }
        }
    }
}
