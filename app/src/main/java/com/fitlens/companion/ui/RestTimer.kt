@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.fitlens.companion.ui

import com.fitlens.companion.ui.design.SettingsSwitchRow
import com.fitlens.companion.ui.design.GlassOutlinedButton
import com.fitlens.companion.ui.design.GoldButton
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.res.stringResource
import com.fitlens.companion.R
import com.fitlens.companion.ui.design.SettingsActionRow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.Workouts
import com.fitlens.companion.ui.design.FitIcons
import com.fitlens.companion.ui.design.StepperField
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.draw.alpha
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

    /**
     * Changes the running rest's length to [seconds] (#105), keeping the time already rested, so the new length applies
     * from now. A length shorter than the time already rested leaves one second.
     */
    fun setLength(seconds: Int) {
        val s = _state.value
        if (!s.active) return
        val rested = max(0, s.total - left(s))
        val newLeft = max(1, seconds - rested)
        if (s.paused) {
            _state.value = s.copy(total = seconds, pausedLeft = newLeft)
            changed()
        } else {
            schedule(s.copy(total = seconds, endAt = now() + newLeft * 1000L))
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
        ctx?.let { UiEvents.show(it.getString(R.string.rt_over)) }
    }
}

/** The rest-over sound (#20): the chosen ringtone (or the phone's default notification sound) at FlexNotes's volume. */
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
        if (saved == null) context.getString(R.string.rt_phone_sound)
        else RingtoneManager.getRingtone(context, Uri.parse(saved))?.getTitle(context) ?: context.getString(R.string.rt_chosen_sound)
    } catch (e: Exception) {
        context.getString(R.string.rt_chosen_sound)
    }
}

/** The rest lengths offered as quick chips in the rest timer and in an exercise's own rest time (#15). */
val REST_CHOICES = listOf(30, 45, 60, 90, 120, 150, 180, 240, 300)

/** Any exact rest length can be set (#105), from one second to an hour, in 5 s steps on the stepper. */
const val REST_MIN = 1
const val REST_MAX = 3600
private const val REST_STEP = 5

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

/**
 * The rest timer's top-bar button (#109), as in FitNotes: an alarm clock while resting isn't timed, and the time left
 * in gold in its place while it runs (dimmed while paused). It keeps one width so the bar doesn't jump. Tapping it
 * calls [onOpen]. With [onlyWhileRunning] (the day log) it shows nothing until a rest starts.
 */
@Composable
fun RestTimerButton(onOpen: () -> Unit, onlyWhileRunning: Boolean = false) {
    val (st, left) = rememberRest()
    if (onlyWhileRunning && !st.active) return
    val res = LocalContext.current.resources
    val label = if (st.active) {
        res.getString(R.string.rt_cd_running, spokenDuration(res, left)) + if (st.paused) res.getString(R.string.rt_cd_paused) else ""
    } else stringResource(R.string.rt_cd)
    Box(
        Modifier
            .size(width = 64.dp, height = Spacing.touch)
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        if (st.active) {
            Text(
                fmtDuration(left),
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                color = if (st.paused) Brand.Gold.copy(alpha = 0.6f) else Brand.Gold,
                maxLines = 1
            )
        } else {
            Icon(FitIcons.Alarm, contentDescription = null, tint = Brand.Gold)
        }
    }
}

/**
 * The rest length as FitNotes sets it (#105): − and + in 5 s steps either side of a number of seconds the user can
 * also type, with the m:ss beside it, then the presets as quick chips (the one matching is selected). [onDefault]
 * adds a first chip, "Default", selected when [isDefault], for an exercise that follows the rest timer's length.
 */
@Composable
fun RestLengthStepper(
    seconds: Int,
    onChange: (Int) -> Unit,
    label: String? = null,
    isDefault: Boolean = false,
    onDefault: (() -> Unit)? = null
) {
    var text by remember(seconds) { mutableStateOf(seconds.toString()) }
    val typed = text.trim().toIntOrNull()
    val valid = typed != null && typed in REST_MIN..REST_MAX
    StepperField(
        label = label ?: stringResource(R.string.rt_length),
        value = text,
        onValue = { t ->
            text = t.filter { it.isDigit() }.take(4)
            text.toIntOrNull()?.takeIf { it in REST_MIN..REST_MAX }?.let(onChange)
        },
        onStep = { dir ->
            val base = typed?.coerceIn(REST_MIN, REST_MAX) ?: seconds
            // Snap to the 5 s grid, so 93 goes to 95 or 90.
            val next = if (dir > 0) (base / REST_STEP + 1) * REST_STEP else ((base - 1) / REST_STEP) * REST_STEP
            onChange(next.coerceIn(REST_MIN, REST_MAX))
        },
        keyboard = KeyboardType.Number
    )
    Text(
        if (valid) stringResource(R.string.rt_mss, fmtDuration(typed ?: seconds)) else stringResource(R.string.rt_invalid),
        modifier = if (valid) Modifier else Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.bodySmall,
        color = if (valid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
    )
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (onDefault != null) {
            FilterChip(selected = isDefault, onClick = onDefault, label = { Text(stringResource(R.string.rt_default)) })
        }
        REST_CHOICES.forEach { secs ->
            FilterChip(
                selected = !isDefault && seconds == secs,
                onClick = { onChange(secs) },
                label = { Text(fmtDuration(secs)) }
            )
        }
    }
}

/**
 * What happens when a rest ends, and whether one starts by itself (#20): start after saving a set, vibrate, the sound,
 * its volume and a test. Shared by the rest timer sheet and Settings → Rest timer (#86), so both always agree.
 */
@Composable
internal fun RestAlertOptions() {
    val ctx = LocalContext.current
    val prefs by Settings.portable.collectAsState()
    val device by Settings.device.collectAsState()
    val notify = rememberNotificationAccess()
    val notifyReason = stringResource(R.string.notify_reason_rest)
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
        SettingsSwitchRow(stringResource(R.string.rest_auto_start), prefs.restAutoStart) { on ->
            // Asked when it's switched on, with the reason first (#41).
            if (on) notify.ask(notifyReason)
            Settings.updatePortable { it.copy(restAutoStart = on) }
        }
        NotificationsOffNote(notify, prefs.restAutoStart)
        SettingsSwitchRow(stringResource(R.string.rest_vibrate), prefs.restVibrate) { on -> Settings.updatePortable { it.copy(restVibrate = on) } }
        SettingsSwitchRow(stringResource(R.string.rest_sound_switch), prefs.restSound) { on -> Settings.updatePortable { it.copy(restSound = on) } }
        // With the sound off, its options stay in place, dimmed, and say why (#41).
        val soundOn = prefs.restSound
        val soundOff = stringResource(R.string.rest_sound_off_reason)
        val pickerTitle = stringResource(R.string.rest_picker_title)
        val noPicker = stringResource(R.string.rest_no_picker)
        run {
            SettingsActionRow(
                title = stringResource(R.string.rest_sound_title),
                value = remember(device.restSoundUri) { RestSound.title(ctx, device.restSoundUri) },
                enabled = soundOn,
                disabledReason = soundOff,
                onClick = {
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION or RingtoneManager.TYPE_ALARM)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, pickerTitle)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, RestSound.uri(device.restSoundUri))
                    }
                    try {
                        pickSound.launch(intent)
                    } catch (e: ActivityNotFoundException) {
                        UiEvents.show(noPicker)
                    }
                }
            )
            var volume by remember(prefs.restVolume) { mutableFloatStateOf(prefs.restVolume.toFloat()) }
            val volumeLabel = stringResource(R.string.rest_volume_description)
            // Lined up with the rows above (#86).
            Column(Modifier.padding(horizontal = Spacing.lg).alpha(if (soundOn) 1f else 0.45f)) {
                Text(stringResource(R.string.rest_volume, volume.toInt()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = volume,
                    onValueChange = { volume = it },
                    enabled = soundOn,
                    valueRange = 10f..100f,
                    onValueChangeFinished = {
                        val v = volume.toInt()
                        Settings.updatePortable { it.copy(restVolume = v) }
                        RestSound.play(ctx, device.restSoundUri, v)
                    },
                    modifier = Modifier.semantics { contentDescription = volumeLabel }
                )
                TextButton(
                    onClick = { RestSound.play(ctx, device.restSoundUri, volume.toInt()) },
                    enabled = soundOn,
                    modifier = Modifier.heightIn(min = Spacing.touch)
                ) { Text(stringResource(R.string.rest_play_sound)) }
            }
        }
}

/**
 * The rest timer sheet (#20): a large gold countdown, −15 s / +15 s, pause or resume, restart and stop, and its
 * settings (length, start after each saved set, sound, vibrate), which apply everywhere. On an exercise with its own
 * rest time (#15), [exercise]'s length is the one started.
 */
@Composable
fun RestTimerSheet(exercise: Exercise? = null, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val res = ctx.resources
    val prefs by Settings.portable.collectAsState()
    val own = exercise?.restSeconds
    val length = own ?: prefs.restSeconds
    DisposableEffect(Unit) { onDispose { RestSound.stop() } }
    val (st, left) = rememberRest()
    val askNotify = rememberNotificationAsk()
    // The stepper edits a local length that is saved a moment after the last change (#105), so holding + doesn't
    // write the setting, or reload the database for an exercise's own length, on every step.
    // Closing the sheet sooner saves it straight away.
    var chosen by remember(length) { mutableIntStateOf(length) }
    val saved by rememberUpdatedState(length)
    val latest by rememberUpdatedState(chosen)
    fun commit(secs: Int) {
        // A running rest takes the new length from now (#105). Waiting for typing to settle means "120" never passes
        // through a 1 s rest on the way.
        RestTimer.setLength(secs)
        if (own != null && exercise != null) {
            AppScope.scope.launch { Workouts.setExerciseDefaults(exercise.id, exercise.weightStepKg, exercise.defaultGraph, secs, exercise.distanceUnit, exercise.weightUnit) }
        } else {
            Settings.updatePortable { it.copy(restSeconds = secs) }
        }
    }
    LaunchedEffect(chosen) {
        if (chosen == length) return@LaunchedEffect
        delay(600)
        commit(chosen)
    }
    DisposableEffect(Unit) { onDispose { if (latest != saved) commit(latest) } }
    FitSheet(title = stringResource(R.string.rt_title), onDismiss = onDismiss, dismissLabel = stringResource(R.string.rt_close)) {
        RestLengthStepper(
            seconds = chosen,
            label = if (own != null && exercise != null) stringResource(R.string.rt_own_length, exercise.name) else stringResource(R.string.rt_length),
            onChange = { secs -> chosen = secs }
        )
        GoldHairline()
        Text(
            fmtDuration(if (st.active) left else chosen),
            Modifier.fillMaxWidth().semantics { contentDescription = res.getString(R.string.rt_left, spokenDuration(res, if (st.active) left else chosen)) },
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
                GlassOutlinedButton(onClick = { RestTimer.adjust(-15) }, modifier = Modifier.weight(1f).heightIn(min = Spacing.row)) { Text(stringResource(R.string.rt_minus15)) }
                GoldButton(
                    onClick = { if (st.paused) RestTimer.resume() else RestTimer.pause() },
                    modifier = Modifier.weight(1f).heightIn(min = Spacing.row)
                ) { Text(if (st.paused) stringResource(R.string.rt_resume) else stringResource(R.string.rt_pause)) }
                GlassOutlinedButton(onClick = { RestTimer.adjust(15) }, modifier = Modifier.weight(1f).heightIn(min = Spacing.row)) { Text(stringResource(R.string.rt_plus15)) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TextButton(onClick = { RestTimer.start(ctx, chosen) }, modifier = Modifier.weight(1f).heightIn(min = Spacing.touch)) { Text(stringResource(R.string.rt_restart)) }
                TextButton(onClick = { RestTimer.stop() }, modifier = Modifier.weight(1f).heightIn(min = Spacing.touch)) { Text(stringResource(R.string.rt_stop)) }
            }
        } else {
            GoldButton(
                onClick = { askNotify(); RestTimer.start(ctx, chosen) },
                modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.row)
            ) { Text(stringResource(R.string.rt_start, fmtDuration(chosen))) }
        }
        if (own != null && exercise != null) {
            Text(
                stringResource(R.string.rt_own_note, exercise.name, fmtDuration(prefs.restSeconds)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = {
                    AppScope.scope.launch {
                        Workouts.setExerciseDefaults(exercise.id, exercise.weightStepKg, exercise.defaultGraph, null, exercise.distanceUnit, exercise.weightUnit)
                    }
                },
                modifier = Modifier.heightIn(min = Spacing.touch)
            ) { Text(stringResource(R.string.rt_use_default, exercise.name)) }
        }
        GoldHairline()
        RestAlertOptions()
        Text(
            stringResource(R.string.rt_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
