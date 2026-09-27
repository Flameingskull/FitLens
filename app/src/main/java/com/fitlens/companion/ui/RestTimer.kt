@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.fitlens.companion.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.fitlens.companion.data.Exercise
import com.fitlens.companion.data.Settings
import com.fitlens.companion.ui.design.ListRowWithMenu
import com.fitlens.companion.data.fmtDuration
import com.fitlens.companion.ui.design.FitSheet
import kotlin.math.max
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The rest timer (#20), shared by every exercise screen so it keeps counting as you move between exercises. While it
 * runs, [TimerService] keeps it going with the screen off and shows it in a notification. When it ends it alerts
 * (a sound and vibration, each a setting) and says so.
 */
object RestTimer {
    /** [endAt] is the wall-clock end in ms while running; [pausedLeft] the seconds left while paused. */
    data class State(val total: Int = 0, val endAt: Long = 0L, val pausedLeft: Int? = null) {
        val active: Boolean get() = endAt > 0L || pausedLeft != null
        val paused: Boolean get() = pausedLeft != null
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state
    private var job: Job? = null
    private var appContext: Context? = null

    private fun now() = System.currentTimeMillis()

    fun left(s: State, at: Long = now()): Int = s.pausedLeft ?: max(0, ((s.endAt - at + 999) / 1000).toInt())

    fun start(context: Context, seconds: Int) {
        appContext = context.applicationContext
        schedule(State(seconds, now() + seconds * 1000L))
    }

    /** Adds or takes away [seconds], never below zero. */
    fun adjust(seconds: Int) {
        val s = _state.value
        if (!s.active) return
        val p = s.pausedLeft
        if (p != null) {
            _state.value = s.copy(pausedLeft = max(0, p + seconds), total = max(s.total, p + seconds))
            changed()
        } else {
            val end = max(now(), s.endAt + seconds * 1000L)
            schedule(s.copy(endAt = end, total = max(s.total, left(s) + seconds)))
        }
    }

    fun pause() {
        val s = _state.value
        if (!s.active || s.paused) return
        job?.cancel()
        _state.value = s.copy(endAt = 0L, pausedLeft = left(s))
        changed()
    }

    fun resume() {
        val s = _state.value
        val p = s.pausedLeft ?: return
        schedule(State(s.total, now() + p * 1000L))
    }

    fun stop() {
        job?.cancel()
        _state.value = State()
        changed()
    }

    private fun changed() {
        appContext?.let { TimerService.refresh(it) }
    }

    private fun schedule(s: State) {
        _state.value = s
        job?.cancel()
        job = AppScope.scope.launch {
            delay(max(0L, s.endAt - now()))
            finish()
        }
        changed()
    }

    private fun finish() {
        _state.value = State()
        changed()
        val ctx = appContext
        val prefs = Settings.currentPortable()
        if (ctx != null && prefs.restSound) RestSound.play(ctx, Settings.current().restSoundUri, prefs.restVolume)
        // With notifications allowed, the "Rest over" alert vibrates and wakes the screen; otherwise vibrate here.
        if (ctx != null && Settings.currentPortable().restVibrate && TimerService.canNotify(ctx)) {
            TimerService.alertRestOver(ctx)
        } else if (ctx != null && Settings.currentPortable().restVibrate) {
            try {
                ctx.getSystemService(Vibrator::class.java)
                    ?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1))
            } catch (e: Exception) {
                // No vibrator, or vibration not allowed: the message below still says rest is over.
            }
        }
        UiEvents.show("Rest over. Time for your next set.")
    }
}

/** The rest-over sound (#20): the chosen ringtone (or the phone's default notification sound) at FitLens's volume. */
object RestSound {
    private var playing: Ringtone? = null

    fun uri(saved: String?): Uri? = saved?.let { Uri.parse(it) } ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    fun play(context: Context, saved: String?, volumePercent: Int) {
        stop()
        val u = uri(saved) ?: return
        try {
            val r = RingtoneManager.getRingtone(context.applicationContext, u) ?: return
            r.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            r.volume = volumePercent.coerceIn(10, 100) / 100f
            r.isLooping = false
            r.play()
            playing = r
        } catch (e: Exception) {
            // A sound that has been deleted or can't be read: the vibration and the message still say rest is over.
        }
    }

    fun stop() {
        try { playing?.stop() } catch (e: Exception) { }
        playing = null
    }

    /** The sound's name as the phone shows it, for the rest timer sheet. */
    fun title(context: Context, saved: String?): String = try {
        if (saved == null) "Phone's notification sound"
        else RingtoneManager.getRingtone(context, Uri.parse(saved))?.getTitle(context) ?: "Chosen sound"
    } catch (e: Exception) {
        "Chosen sound"
    }
}

/** The rest lengths offered in the rest timer and in an exercise's own rest time (#15). */
val REST_CHOICES = listOf(30, 45, 60, 90, 120, 150, 180, 240, 300)

/**
 * Asks once for the notification permission (Android 13+), which the timers' notification needs. Returns a function
 * to call when a timer is started by hand.
 */
@Composable
fun rememberNotificationAsk(): () -> Unit {
    val ctx = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return {
        if (!TimerService.canNotify(ctx) && Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

/** The rest timer's state and seconds left, ticking four times a second while it runs. */
@Composable
fun rememberRest(): Pair<RestTimer.State, Int> {
    val st by RestTimer.state.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(st) {
        while (st.active && !st.paused) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    return st to RestTimer.left(st, now)
}

/** A slim bar under the exercise screen's tabs while the rest timer is running. Tapping it opens the timer. */
@Composable
fun RestTimerStrip(onOpen: () -> Unit) {
    val (st, left) = rememberRest()
    if (!st.active) return
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.touch)
            .clickable(onClickLabel = "Open the rest timer", onClick = onOpen)
            .semantics(mergeDescendants = true) { contentDescription = "Rest timer, ${fmtDuration(left)} left" + if (st.paused) ", paused" else "" }
            .padding(horizontal = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("REST", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            fmtDuration(left) + if (st.paused) "  ·  paused" else "",
            Modifier.weight(1f).padding(start = Spacing.md),
            style = MaterialTheme.typography.titleMedium,
            color = Brand.Gold
        )
        LinearProgressIndicator(
            progress = { if (st.total > 0) left.toFloat() / st.total else 0f },
            modifier = Modifier.weight(1f),
            color = Brand.Gold,
            trackColor = Brand.Hairline
        )
    }
    GoldHairline()
}

/**
 * The rest timer sheet (#20): a large gold countdown, −15 s / +15 s, pause or resume, restart and stop, and its
 * settings (length, start after each saved set, sound, vibrate), which apply everywhere. On an exercise with its own
 * rest time (#15), [exercise]'s length is the one started.
 */
@Composable
fun RestTimerSheet(exercise: Exercise? = null, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val prefs by Settings.portable.collectAsState()
    val device by Settings.device.collectAsState()
    val own = exercise?.restSeconds
    val length = own ?: prefs.restSeconds
    val pickSound = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val picked: Uri? = if (Build.VERSION.SDK_INT >= 33) {
                res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            val default = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            Settings.updateDevice { it.copy(restSoundUri = picked?.takeIf { u -> u != default }?.toString()) }
        }
    }
    DisposableEffect(Unit) { onDispose { RestSound.stop() } }
    val (st, left) = rememberRest()
    val askNotify = rememberNotificationAsk()
    FitSheet(title = "Rest timer", onDismiss = onDismiss, dismissLabel = "Close") {
        Text(
            fmtDuration(if (st.active) left else length),
            Modifier.fillMaxWidth().semantics { contentDescription = "${fmtDuration(if (st.active) left else length)} left" },
            style = MaterialTheme.typography.displayLarge,
            color = Brand.Gold
        )
        if (st.active) {
            LinearProgressIndicator(
                progress = { if (st.total > 0) left.toFloat() / st.total else 0f },
                modifier = Modifier.fillMaxWidth(),
                color = Brand.Gold,
                trackColor = Brand.Hairline
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(onClick = { RestTimer.adjust(-15) }, modifier = Modifier.weight(1f).heightIn(min = Spacing.row)) { Text("−15 s") }
                Button(
                    onClick = { if (st.paused) RestTimer.resume() else RestTimer.pause() },
                    modifier = Modifier.weight(1f).heightIn(min = Spacing.row)
                ) { Text(if (st.paused) "Resume" else "Pause") }
                OutlinedButton(onClick = { RestTimer.adjust(15) }, modifier = Modifier.weight(1f).heightIn(min = Spacing.row)) { Text("+15 s") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TextButton(onClick = { RestTimer.start(ctx, length) }, modifier = Modifier.weight(1f).heightIn(min = Spacing.touch)) { Text("Restart") }
                TextButton(onClick = { RestTimer.stop() }, modifier = Modifier.weight(1f).heightIn(min = Spacing.touch)) { Text("Stop") }
            }
        } else {
            Button(
                onClick = { askNotify(); RestTimer.start(ctx, length) },
                modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.row)
            ) { Text("Start ${fmtDuration(length)} rest") }
        }
        if (own != null && exercise != null) {
            Text(
                "${exercise.name} rests ${fmtDuration(own)}, set in Edit exercise. The lengths below are for every other exercise.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        GoldHairline()
        Text("REST LENGTH", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            REST_CHOICES.forEach { secs ->
                FilterChip(
                    selected = prefs.restSeconds == secs,
                    onClick = { Settings.updatePortable { it.copy(restSeconds = secs) } },
                    label = { Text(fmtDuration(secs)) }
                )
            }
        }
        ToggleRow("Start after saving a set", prefs.restAutoStart) { on ->
            if (on) askNotify()
            Settings.updatePortable { it.copy(restAutoStart = on) }
        }
        ToggleRow("Vibrate when rest is over", prefs.restVibrate) { on -> Settings.updatePortable { it.copy(restVibrate = on) } }
        ToggleRow("Play a sound when rest is over", prefs.restSound) { on -> Settings.updatePortable { it.copy(restSound = on) } }
        if (prefs.restSound) {
            ListRowWithMenu(
                title = "Sound",
                subtitle = RestSound.title(ctx, device.restSoundUri),
                onClick = {
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION or RingtoneManager.TYPE_ALARM)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Rest over sound")
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, RestSound.uri(device.restSoundUri))
                    }
                    try {
                        pickSound.launch(intent)
                    } catch (e: ActivityNotFoundException) {
                        UiEvents.show("This phone has no sound picker, so the notification sound is used.")
                    }
                }
            )
            var volume by remember(prefs.restVolume) { mutableFloatStateOf(prefs.restVolume.toFloat()) }
            Text("VOLUME  ·  ${volume.toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = volume,
                onValueChange = { volume = it },
                valueRange = 10f..100f,
                onValueChangeFinished = {
                    val v = volume.toInt()
                    Settings.updatePortable { it.copy(restVolume = v) }
                    RestSound.play(ctx, device.restSoundUri, v)
                },
                modifier = Modifier.semantics { contentDescription = "Rest over sound volume" }
            )
            TextButton(
                onClick = { RestSound.play(ctx, device.restSoundUri, volume.toInt()) },
                modifier = Modifier.heightIn(min = Spacing.touch)
            ) { Text("Play the sound") }
        }
        Text(
            "The timer keeps running as you move between exercises, and with the screen off, where a notification shows it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
