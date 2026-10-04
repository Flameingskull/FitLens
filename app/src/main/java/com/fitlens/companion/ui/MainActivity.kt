package com.fitlens.companion.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import com.fitlens.companion.ui.design.UndoSnackbarHost
import com.fitlens.companion.ui.design.CharacterBackdrop
import com.fitlens.companion.ui.design.ambientBackdrop
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fitlens.companion.R
import com.fitlens.companion.data.AutoBackup
import com.fitlens.companion.data.BackupSync
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.Backups
import com.fitlens.companion.data.FileKind
import com.fitlens.companion.data.FitNotesImporter
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Destinations. Navigation follows FitNotes (#79): the day log ([Day]) is home and the root of the stack, and
 * everything else is pushed on top of it and returns with Back. There is no bottom tab bar.
 */
@Serializable
sealed interface Screen {
    /** Every day with photos, measurements or a workout (the old Log tab), from the day log's menu. */
    @Serializable
    data object Timeline : Screen
    @Serializable
    data object Calendar : Screen
    @Serializable
    data object Body : Screen
    /** One body measurement (FitNotes's body tracker): Track, History and Graph; [page] is the tab it opens on. */
    @Serializable
    data class BodyMeasurement(val name: String, val page: Int = 0) : Screen
    /** Every body measurement: on/off, custom ones, the standard set (#88). */
    @Serializable
    data object Measurements : Screen
    /**
     * Creating ([id] 0) or editing a workout (#106, FitNotes's routine): exercises grouped by user-named days. Reached
     * from the library's title switcher and the day log's Add workout.
     */
    @Serializable
    data class WorkoutEditor(val id: Long) : Screen
    /** The Analysis hub (#90). */
    @Serializable
    data object Analysis : Screen
    @Serializable
    data object Photos : Screen
    /** The day log (#81). At the root of the stack it is the home screen. */
    @Serializable
    data class Day(val date: String) : Screen
    /** The exercise library (#83), choosing exercises to log on [date] (null: today). */
    @Serializable
    data class Library(val date: String? = null) : Screen
    /**
     * The exercise screen (#16, #82): Track, History and Graph for one exercise on one day. [queue] holds the exercises
     * chosen together in the library (#83), opened one after another with "Next exercise". [page] is the tab it opens
     * on: 0 Track, 1 History, 2 Graph. [setId] opens Track with that set selected for Update or Delete (#22).
     */
    @Serializable
    data class SetEntry(
        val date: String,
        val exerciseId: Long,
        val queue: List<Long> = emptyList(),
        val page: Int = 0,
        val setId: Long? = null
    ) : Screen
    /** An exercise's Records, Stats and Goals; [tab] is the one it opens on (2 is Goals, from Analysis, #90). */
    @Serializable
    data class ExerciseDetail(val id: Long, val tab: Int = 0) : Screen
    @Serializable
    data class PhotoViewer(val ids: List<Long>, val index: Int) : Screen
    @Serializable
    data class Compare(val a: Long, val b: Long) : Screen
    @Serializable
    data class Slideshow(val ids: List<Long>? = null) : Screen
    @Serializable
    data object Review : Screen
    /** The main Settings screen, opened from the day log's menu (#38). */
    @Serializable
    data object SettingsHome : Screen
    @Serializable
    data class SettingsPage(val section: SettingsSection) : Screen
    /** The guided setup (#29): first run, or again from Settings. */
    @Serializable
    data object Setup : Screen
}

/**
 * The one destination in the [NavHost] (#37). [s] is the [Screen] it shows, as JSON, so every destination and its
 * arguments live in Navigation's back stack, which survives rotation and process death.
 */
@Serializable
data class Route(val s: String)

/**
 * The back stack, as screens see it: [push], [pop], [home] and [replace] over Navigation Compose (#37). The day log
 * is the root. [MainActivity] builds it before the [NavHost] exists, so anything asked of it before [attach] (a file
 * shared to a cold start) waits until then.
 */
class Nav {
    private var controller: NavHostController? = null
    private val waiting = ArrayList<(NavHostController) -> Unit>()

    /** The screen on top. State, so screens that read it recompose when it changes. */
    var top: Screen by mutableStateOf<Screen>(Screen.Day(Dates.today()))
        private set
    /** Whether the top screen is the root (the day log as home). */
    var atHome: Boolean by mutableStateOf(true)
        private set
    /** Whether the last move went back towards home, so the screens slide the matching way. */
    var goingBack = false
        private set

    fun push(s: Screen) = act { c ->
        goingBack = false
        c.navigate(Route(encode(s)))
    }

    fun pop() = act { c ->
        if (c.previousBackStackEntry != null) {
            goingBack = true
            c.popBackStack()
        }
    }

    /** Clears the stack back to the day log (home), showing [date]. */
    fun home(date: String = Dates.today()) = act { c ->
        goingBack = true
        c.navigate(Route(encode(Screen.Day(date)))) { popUpTo(c.graph.id) { inclusive = true } }
    }

    /**
     * Swaps the top screen for [s]. One of the same kind (another day, the next exercise) keeps the screen in place
     * without animating, as before; another kind replaces it with the usual slide.
     */
    fun replace(s: Screen) = act { c ->
        val entry = c.currentBackStackEntry
        if (entry != null && entry.screen()::class == s::class) {
            entry.savedStateHandle[KEY] = encode(s)
        } else {
            goingBack = false
            c.navigate(Route(encode(s))) { popUpTo<Route> { inclusive = true } }
        }
    }

    /** Called once the [NavHost] has its graph: runs anything asked for before then. */
    fun attach(c: NavHostController) {
        controller = c
        sync(c)
        val queued = waiting.toList()
        waiting.clear()
        queued.forEach { it(c) }
        sync(c)
    }

    /** Follows the stack after a change made elsewhere (system Back, predictive back). */
    fun sync(c: NavHostController) {
        val entry = c.currentBackStackEntry ?: return
        top = entry.screen()
        atHome = c.previousBackStackEntry == null
    }

    private fun act(action: (NavHostController) -> Unit) {
        val c = controller
        if (c == null) {
            waiting.add(action)
        } else {
            action(c)
            sync(c)
        }
    }

    companion object {
        /** The [Route] argument holding the screen; also its key in each entry's saved state. */
        const val KEY = "s"
        private val json = Json { ignoreUnknownKeys = true }

        fun encode(s: Screen): String = json.encodeToString(Screen.serializer(), s)

        /** A screen saved by an older version that no longer reads falls back to today's day log. */
        fun decode(text: String?): Screen =
            text?.let { runCatching { json.decodeFromString(Screen.serializer(), it) }.getOrNull() }
                ?: Screen.Day(Dates.today())

        fun NavBackStackEntry.screen(): Screen =
            decode(savedStateHandle.get<String>(KEY) ?: arguments?.getString(KEY))
    }
}

class MainActivity : ComponentActivity() {

    private val nav = Nav()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { FitLensTheme { AppRoot(nav, onRetry = { startUp(null) }) } }
        startUp(if (savedInstanceState == null) intent else null)
    }

    /**
     * Loads the data, then the start-up chores and [opening] (the file or photos FitLens was opened with). If the data
     * can't be read, the error screen's Try again comes back here (#93).
     */
    private fun startUp(opening: Intent?) {
        lifecycleScope.launch {
            if (!Store.open()) return@launch
            // A result the user hadn't read when the app was closed stays readable in Settings → Backup (#62).
            UiEvents.loadLastResult()
            // Safety copies (#47) are kept for a limited time only.
            Backups.pruneSafety(applicationContext)
            opening?.let { handleIntent(it) }
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
                    // Settings → Import From FitNotes shows what the backup adds before importing it (#35).
                    openSettingsPage(SettingsSection.Import)
                    FitNotesImports.pending.value = u
                }
                FileKind.BODY_CSV -> FitNotesImporter.importBodyCsv(this, u).let { UiEvents.show(it.message, it.level()) }
                FileKind.WORKOUT_CSV -> UiEvents.show("Workout CSVs aren't needed — share a FitNotes backup (.fitnotes) instead; it contains everything.")
                FileKind.ARCHIVE -> {
                    // A .fitlens backup: Settings → Backup checks it and asks before restoring.
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
fun AppRoot(nav: Nav, onRetry: () -> Unit) {
    val snap by Store.snapshot.collectAsState()
    val openError by Store.openError.collectAsState()
    val busy by UiEvents.busy.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val a11y = androidx.compose.ui.platform.LocalContext.current
        .getSystemService(android.view.accessibility.AccessibilityManager::class.java)
    fun touchExploring() = a11y?.isTouchExplorationEnabled == true
    // A failure or warning waiting to be acknowledged (#62). Messages queue behind it until it is closed.
    var toAcknowledge by remember { mutableStateOf<UiMessage?>(null) }
    LaunchedEffect(Unit) {
        UiEvents.messages.collect { m ->
            if (m.mustAcknowledge) {
                toAcknowledge = m
                snapshotFlow { toAcknowledge }.first { it == null }
                return@collect
            }
            // An action (Undo) gets the longer duration, never Indefinite: the collector waits for each message. With
            // TalkBack on, every message does, so there's time to hear it and reach Undo (#41).
            val result = snackbar.showSnackbar(
                message = m.text,
                actionLabel = m.actionLabel,
                withDismissAction = false,
                duration = if (m.actionLabel == null && !touchExploring()) SnackbarDuration.Short else SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) m.onAction?.invoke()
        }
    }
    // First run (#29): a phone with no data yet gets the guided setup once. Anyone updating with data already here
    // never sees it unasked; it's still in Settings.
    val loaded = snap != null
    // "What's new" (#33): shown once after each update. A new phone starting setup skips it.
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val versionCode = remember { appVersion(ctx).second }
    var whatsNew by remember { mutableStateOf(false) }
    LaunchedEffect(loaded) {
        val s = snap ?: return@LaunchedEffect
        Settings.awaitLoaded()
        val fresh = !Settings.current().setupDone && s.allDates.isEmpty() && s.exercises.isEmpty()
        if (Settings.current().whatsNewSeen < versionCode) {
            val notes = withContext(Dispatchers.IO) { ReleaseNotes.load(ctx) }
            if (fresh || notes == null) Settings.updateDevice { it.copy(whatsNewSeen = versionCode) } else whatsNew = true
        }
        if (Settings.current().setupDone) return@LaunchedEffect
        if (s.allDates.isEmpty() && s.exercises.isEmpty()) {
            if (nav.atHome) nav.push(Screen.Setup)
        } else {
            Settings.updateDevice { it.copy(setupDone = true) }
        }
    }
    val controller = rememberNavController()
    // The first screen is today's day log. After rotation or process death Navigation restores the saved stack.
    val start = remember { Route(Nav.encode(Screen.Day(Dates.today()))) }
    LaunchedEffect(controller) {
        nav.attach(controller)
        controller.currentBackStackEntryFlow.collect { nav.sync(controller) }
    }

    Scaffold(
        // Transparent over the ambient glow the glass surfaces catch (#102).
        modifier = Modifier.ambientBackdrop(),
        containerColor = Brand.Black.copy(alpha = 0f),
        // A transparent container has no matching content colour, so text would fall back to black: ivory instead.
        contentColor = Brand.Ivory,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { UndoSnackbarHost(snackbar) }
    ) { inner ->
        // With no bottom bar any more, the content keeps itself clear of the navigation bar (edge-to-edge, #81).
        Box(Modifier.fillMaxSize().padding(inner).consumeWindowInsets(inner).navigationBarsPadding()) {
            // The full-body character, faint, behind every screen (owner's branding, 2026-10-01).
            CharacterBackdrop()
            val back = remember(nav) { { nav.pop() } }
            // With the system's "Remove animations" on, screens change at once (#93).
            val motion = animationsEnabled()
            CompositionLocalProvider(LocalNavBack provides back) {
                // Screens slide in when opened and back out when closed (#86, #93). Navigation handles Back: a
                // pushed screen pops, and at the root Back leaves the app as it does in FitNotes. On Android 14+ the
                // back gesture previews the screen underneath (predictive back, enabled in the manifest).
                NavHost(
                    navController = controller,
                    startDestination = start,
                    enterTransition = { if (motion) slideIn(if (nav.goingBack) -1 else 1) else EnterTransition.None },
                    exitTransition = { if (motion) slideOut(if (nav.goingBack) -1 else 1) else ExitTransition.None },
                    popEnterTransition = { if (motion) slideIn(-1) else EnterTransition.None },
                    popExitTransition = { if (motion) slideOut(-1) else ExitTransition.None }
                ) {
                    composable<Route> { entry ->
                        // Replacing a screen with one of its own kind updates this entry's saved state in place.
                        val text by remember(entry) {
                            entry.savedStateHandle.getStateFlow(Nav.KEY, entry.arguments?.getString(Nav.KEY) ?: "")
                        }.collectAsState()
                        val screen = remember(text) { Nav.decode(text) }
                        val s = snap
                        val failed = openError
                        if (s == null && failed != null) {
                            ErrorState(
                                title = stringResource(R.string.app_open_failed_title),
                                body = stringResource(R.string.app_open_failed_body, failed),
                                actionLabel = stringResource(R.string.app_open_retry),
                                onAction = onRetry
                            )
                        } else if (s == null) {
                            LoadingState(stringResource(R.string.app_opening_title), stringResource(R.string.app_opening_body))
                        } else {
                            ScreenContent(s, nav, screen)
                        }
                    }
                }
            }
            if (busy != null) {
                Box(
                    Modifier.fillMaxSize().background(Brand.Scrim)
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
            if (whatsNew) {
                WhatsNewSheet {
                    whatsNew = false
                    Settings.updateDevice { it.copy(whatsNewSeen = versionCode) }
                }
            }
            toAcknowledge?.let { m -> ResultDialog(m) { toAcknowledge = null } }
        }
    }
}

private fun slideIn(dir: Int): EnterTransition =
    slideInHorizontally(tween(Motion.STANDARD)) { w -> dir * w / 5 } + fadeIn(tween(Motion.STANDARD))

private fun slideOut(dir: Int): ExitTransition =
    slideOutHorizontally(tween(Motion.STANDARD)) { w -> -dir * w / 5 } + fadeOut(tween(Motion.FAST))

/** The composable for each [Screen]. A new destination adds a [Screen] and a line here. */
@Composable
private fun ScreenContent(s: Snapshot, nav: Nav, screen: Screen) {
    when (screen) {
        Screen.Timeline -> TimelineScreen(s, nav)
        Screen.Calendar -> CalendarScreen(s, nav)
        Screen.Body -> BodyScreen(s, nav)
        is Screen.BodyMeasurement -> BodyMeasurementScreen(s, nav, screen.name, screen.page)
        Screen.Measurements -> MeasurementsScreen(s, nav)
        Screen.Analysis -> AnalysisScreen(s, nav)
        is Screen.WorkoutEditor -> WorkoutEditorScreen(s, nav, screen.id)
        Screen.Photos -> PhotosScreen(s, nav)
        is Screen.Day -> DayScreen(s, nav, screen.date)
        is Screen.Library -> ExerciseLibraryScreen(s, nav, screen.date)
        // A set opened from History is a fresh screen, on Track with that set selected (#22).
        is Screen.SetEntry -> key(screen.setId) {
            SetEntryScreen(s, nav, screen.date, screen.exerciseId, screen.queue, screen.page, screen.setId)
        }
        is Screen.ExerciseDetail -> ExerciseDetailScreen(s, nav, screen.id, screen.tab)
        is Screen.PhotoViewer -> PhotoViewerScreen(s, nav, screen.ids, screen.index)
        is Screen.Compare -> CompareScreen(s, nav, screen.a, screen.b)
        is Screen.Slideshow -> SlideshowScreen(s, nav, screen.ids)
        Screen.Review -> ReviewScreen(s, nav)
        Screen.SettingsHome -> SettingsScreen(s, nav)
        is Screen.SettingsPage -> SettingsPageScreen(s, nav, screen.section)
        Screen.Setup -> SetupScreen(s, nav)
    }
}
