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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.fitlens.companion.data.AutoBackup
import com.fitlens.companion.data.BackupSync
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.Backups
import com.fitlens.companion.data.FileKind
import com.fitlens.companion.data.FitNotesImporter
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.Store
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Destinations. Navigation follows FitNotes (#79): the day log ([Day]) is home and the root of the stack, and
 * everything else is pushed on top of it and returns with Back. There is no bottom tab bar.
 */
sealed interface Screen {
    /** Every day with photos, measurements or a workout (the old Log tab), from the day log's menu. */
    data object Timeline : Screen
    data object Calendar : Screen
    data object Body : Screen
    /** Saved workouts (#100): named groups of exercises with their sets. */
    data object SavedWorkouts : Screen
    /** Creating ([id] 0) or editing a saved workout (#100). */
    data class SavedWorkoutEditor(val id: Long) : Screen
    /** Routines (#21): saved workouts split into user-named days. */
    data object Routines : Screen
    /** Creating ([id] 0) or editing a routine (#21). */
    data class RoutineEditor(val id: Long) : Screen
    /** The Analysis hub (#90). */
    data object Analysis : Screen
    data object Photos : Screen
    /** The day log (#81). At the root of the stack it is the home screen. */
    data class Day(val date: String) : Screen
    /** The exercise library (#83), choosing exercises to log on [date] (null: today). */
    data class Library(val date: String? = null) : Screen
    /**
     * The exercise screen (#16, #82): Track, History and Graph for one exercise on one day. [queue] holds the exercises
     * chosen together in the library (#83), opened one after another with "Next exercise". [page] is the tab it opens
     * on: 0 Track, 1 History, 2 Graph.
     */
    data class SetEntry(val date: String, val exerciseId: Long, val queue: List<Long> = emptyList(), val page: Int = 0) : Screen
    data class ExerciseDetail(val id: Long) : Screen
    data class PhotoViewer(val ids: List<Long>, val index: Int) : Screen
    data class Compare(val a: Long, val b: Long) : Screen
    data class Slideshow(val ids: List<Long>? = null) : Screen
    data object Review : Screen
    /** The main Settings screen, opened from the day log's menu (#38). */
    data object SettingsHome : Screen
    data class SettingsPage(val section: SettingsSection) : Screen
    /** The guided setup (#29): first run, or again from Settings. */
    data object Setup : Screen
}

class Nav {
    val stack = mutableStateListOf<Screen>(Screen.Day(Dates.today()))
    val top: Screen get() = stack.last()
    val atHome: Boolean get() = stack.size == 1
    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    /** Clears the stack back to the day log (home), showing [date]. */
    fun home(date: String = Dates.today()) { stack.clear(); stack.add(Screen.Day(date)) }
}

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

    /** A Settings page, with Back leading to Settings and then the day log. */
    private fun openSettingsPage(section: SettingsSection) {
        nav.home()
        nav.push(Screen.SettingsHome)
        nav.push(Screen.SettingsPage(section))
    }

    private fun openBackups() = openSettingsPage(SettingsSection.Backups)

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
                    // Settings → FitNotes import shows what the backup adds before importing it (#35).
                    openSettingsPage(SettingsSection.Import)
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
            PhotoImports.request(PendingPhotoImport(images) { r -> if (r.needsReview > 0) { nav.home(); nav.push(Screen.Photos) } })
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
    // First run (#29): a phone with no data yet gets the guided setup once. Anyone updating with data already here
    // never sees it unasked; it's still in Settings.
    val loaded = snap != null
    LaunchedEffect(loaded) {
        val s = snap ?: return@LaunchedEffect
        Settings.awaitLoaded()
        if (Settings.current().setupDone) return@LaunchedEffect
        if (s.allDates.isEmpty() && s.exercises.isEmpty()) {
            if (nav.atHome) nav.push(Screen.Setup)
        } else {
            Settings.updateDevice { it.copy(setupDone = true) }
        }
    }
    val top = nav.top
    // At the root, Back leaves the app as it does in FitNotes.
    BackHandler(enabled = !nav.atHome) { nav.pop() }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) }
    ) { inner ->
        // With no bottom bar any more, the content keeps itself clear of the navigation bar (edge-to-edge, #81).
        Box(Modifier.fillMaxSize().padding(inner).consumeWindowInsets(inner).navigationBarsPadding()) {
            val s = snap
            if (s == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                val back = remember(nav) { { nav.pop() } }
                CompositionLocalProvider(LocalNavBack provides back) {
                when (top) {
                    Screen.Timeline -> TimelineScreen(s, nav)
                    Screen.Calendar -> CalendarScreen(s, nav)
                    Screen.Body -> BodyScreen(s, nav)
                    Screen.Analysis -> AnalysisScreen(s, nav)
                    Screen.SavedWorkouts -> SavedWorkoutsScreen(s, nav)
                    is Screen.SavedWorkoutEditor -> SavedWorkoutEditorScreen(s, nav, top.id)
                    Screen.Routines -> RoutinesScreen(s, nav)
                    is Screen.RoutineEditor -> RoutineEditorScreen(s, nav, top.id)
                    Screen.Photos -> PhotosScreen(s, nav)
                    is Screen.Day -> DayScreen(s, nav, top.date)
                    is Screen.Library -> ExerciseLibraryScreen(s, nav, top.date)
                    is Screen.SetEntry -> SetEntryScreen(s, nav, top.date, top.exerciseId, top.queue, top.page)
                    is Screen.ExerciseDetail -> ExerciseDetailScreen(s, nav, top.id)
                    is Screen.PhotoViewer -> PhotoViewerScreen(s, nav, top.ids, top.index)
                    is Screen.Compare -> CompareScreen(s, nav, top.a, top.b)
                    is Screen.Slideshow -> SlideshowScreen(s, nav, top.ids)
                    Screen.Review -> ReviewScreen(s, nav)
                    Screen.SettingsHome -> SettingsScreen(nav)
                    is Screen.SettingsPage -> SettingsPageScreen(s, nav, top.section)
                    Screen.Setup -> SetupScreen(s, nav)
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
