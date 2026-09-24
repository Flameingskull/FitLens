package com.fitlens.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fitlens.companion.ui.design.FitTopBar
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.Photo
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Store
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * How much a result weighs, which decides how it reaches the user (#62).
 *
 * [Info] is routine chatter that can safely be missed: a short snackbar, and nothing is kept.
 * [Success] is an outcome worth reading twice, such as an import's counts: a snackbar, and it is kept.
 * [Warning] and [Failure] have to be read: a dialog the user acknowledges, and they are kept too.
 *
 * Anything above [Info] is written to `meta`, so the one message that mattered survives the app being killed
 * instead of disappearing after four seconds with no way to get it back.
 */
enum class ResultLevel { Info, Success, Warning, Failure }

/** A message for the snackbar or the result dialog, optionally with one action such as Undo. */
data class UiMessage(
    val text: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val level: ResultLevel = ResultLevel.Info
) {
    /** True when this has to be acknowledged rather than flash past in a snackbar. */
    val mustAcknowledge: Boolean get() = level == ResultLevel.Warning || level == ResultLevel.Failure
}

/** A result worth keeping, so it can be read again long after the snackbar has gone. */
data class AppResult(val at: Long, val text: String, val level: ResultLevel)

/** App-wide transient messages and busy state. */
object UiEvents {
    /** Key in the `meta` key/value table. Not a schema change. */
    private const val LAST_RESULT = "last_result"

    val messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 16)
    val busy = MutableStateFlow<String?>(null)
    /** A backup file opened from outside the app, waiting for the Sync tab to confirm the restore. */
    val pendingRestore = MutableStateFlow<android.net.Uri?>(null)
    /** The most recent result above [ResultLevel.Info]. Survives rotation, navigation and process death. */
    val lastResult = MutableStateFlow<AppResult?>(null)

    fun show(msg: String) { emit(UiMessage(msg)) }

    /** A message the user can act on, e.g. "Set deleted" with Undo. */
    fun show(msg: String, actionLabel: String, onAction: () -> Unit) {
        emit(UiMessage(msg, actionLabel, onAction))
    }

    /** A result delivered at the weight it deserves. See [ResultLevel]. */
    fun show(msg: String, level: ResultLevel, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        emit(UiMessage(msg, actionLabel, onAction, level))
    }

    private fun emit(m: UiMessage) {
        if (m.level != ResultLevel.Info) keep(AppResult(System.currentTimeMillis(), m.text, m.level))
        messages.tryEmit(m)
    }

    /**
     * Records a result so it can be re-read. Guarded, because a message can be shown before [Store] is ready and
     * because the database is briefly closed part-way through a restore.
     */
    fun keep(r: AppResult) {
        lastResult.value = r
        runCatching { Store.db.setMeta(LAST_RESULT, "${r.at}|${r.level.name}|${r.text}") }
    }

    /** Reads the kept result back after the app was killed. Safe to call more than once. */
    fun loadLastResult() {
        if (lastResult.value != null) return
        val stored = runCatching { Store.db.getMeta(LAST_RESULT) }.getOrNull() ?: return
        val at = stored.substringBefore('|').toLongOrNull() ?: return
        val rest = stored.substringAfter('|')
        // The message itself may contain '|', so only the first two fields are split off.
        val level = runCatching { ResultLevel.valueOf(rest.substringBefore('|')) }.getOrDefault(ResultLevel.Info)
        lastResult.value = AppResult(at, rest.substringAfter('|'), level)
    }

    fun clearLastResult() {
        lastResult.value = null
        runCatching { Store.db.setMeta(LAST_RESULT, null) }
    }
}

/** Thin gold line that fades out at both ends. */
@Composable
fun GoldHairline(modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(1.dp).background(
            Brush.horizontalGradient(listOf(Color.Transparent, Brand.Gold.copy(alpha = 0.7f), Color.Transparent))
        )
    )
}

/** A pushed screen's top bar with a back arrow. A thin wrapper over [FitTopBar], kept until every screen moves (#80). */
@Composable
fun BackTopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    FitTopBar(title = title, onBack = onBack, trailing = { actions() })
}

/** A tab screen's top bar, left-aligned as before. A thin wrapper over [FitTopBar], kept until every screen moves (#80). */
@Composable
fun PlainTopBar(title: String, actions: @Composable () -> Unit = {}) {
    FitTopBar(title = title, centered = false, trailing = { actions() })
}

@Composable
fun PhotoThumb(snap: Snapshot, photo: Photo, modifier: Modifier = Modifier, sizePx: Int = 360, contentScale: ContentScale = ContentScale.Crop) {
    val ctx = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(ctx).data(snap.photoFile(photo)).size(sizePx).crossfade(true).build(),
        contentDescription = "Progress photo ${photo.date ?: ""} ${photo.pose}",
        contentScale = contentScale,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
    )
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}

/**
 * The one pattern for empty, loading and error screens (#80): a serif title, a one-line body and one primary
 * action, centred. [EmptyState], [LoadingState] and [ErrorState] are the three entry points.
 */
@Composable
private fun StatePanel(title: String, body: String, top: @Composable () -> Unit = {}, action: @Composable () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().padding(Spacing.xxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        top()
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.lg)
        )
        action()
    }
}

/** Nothing to show yet, with an optional action that fixes that. */
@Composable
fun EmptyState(title: String, body: String, action: @Composable () -> Unit = {}) {
    StatePanel(title, body, action = action)
}

/** Work in progress: a gold spinner above the title. */
@Composable
fun LoadingState(title: String, body: String) {
    StatePanel(title, body, top = {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = Spacing.lg))
    })
}

/** Something failed: a warning icon in the error colour, and one way forward such as "Try again". */
@Composable
fun ErrorState(title: String, body: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    StatePanel(
        title = title,
        body = body,
        top = {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = Spacing.md).size(Spacing.xxl)
            )
        },
        action = {
            if (actionLabel != null && onAction != null) Button(onClick = onAction) { Text(actionLabel) }
        }
    )
}

@Composable
fun LabelValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun InfoRow(left: String, right: String, sub: String? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(left, style = MaterialTheme.typography.bodyLarge)
            if (!sub.isNullOrBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(right, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Material date picker returning an ISO date string. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickDateDialog(initial: String?, onDismiss: () -> Unit, onPicked: (String) -> Unit) {
    val initMillis = (Dates.parse(initial) ?: LocalDate.now()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    val state = rememberDatePickerState(initialSelectedDateMillis = initMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val ms = state.selectedDateMillis
                if (ms != null) {
                    onPicked(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().format(Dates.ISO))
                }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(state = state)
    }
}

/**
 * A result the user has to acknowledge (#62). Restore and backup failures used to share a four-second snackbar
 * with "Backup saved with 12 photos"; here they stay on screen until they have been read, keep any action such as
 * Undo, and remain re-readable in Sync → Backups afterwards.
 */
@Composable
fun ResultDialog(m: UiMessage, onDismiss: () -> Unit) {
    val failure = m.level == ResultLevel.Failure
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    if (failure) "That didn't work" else "Worth knowing",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (failure) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                GoldHairline(Modifier.padding(top = 8.dp))
            }
        },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                Text(m.text, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            val action = m.actionLabel
            if (action != null) TextButton(onClick = { onDismiss(); m.onAction?.invoke() }) { Text(action) }
            else TextButton(onClick = onDismiss) { Text("OK") }
        },
        dismissButton = {
            if (m.actionLabel != null) TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String = "Delete", onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
