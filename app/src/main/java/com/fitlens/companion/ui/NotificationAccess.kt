package com.fitlens.companion.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.fitlens.companion.R
import com.fitlens.companion.ui.design.ConfirmSheet
import com.fitlens.companion.ui.design.SettingsActionRow
import com.fitlens.companion.ui.design.SettingsNote

/**
 * Notifications as the settings that use them ask for them (#41, section 5.4). Switching such a setting on calls
 * [ask] with a one-line reason; FitLens shows the reason first, then Android's request (or, before Android 13, the
 * phone's notification settings). The settings still work in the app without notifications, so a setting stays on
 * when they're refused, and [NotificationsOffNote] under it says they're off, with a way to turn them on.
 */
@Stable
class NotificationAccess internal constructor(granted: Boolean) {
    /** Whether FitLens may post notifications, read again each time the screen comes back. */
    var granted by mutableStateOf(granted)
        internal set
    internal var reason by mutableStateOf<String?>(null)

    /** Explains [why] and asks, unless notifications are already allowed. */
    fun ask(why: String) {
        if (!granted) reason = why
    }
}

/** The notification access for this screen, with the reason sheet it shows while asking. */
@Composable
fun rememberNotificationAccess(): NotificationAccess {
    val ctx = LocalContext.current
    val access = remember { NotificationAccess(notificationsAllowed(ctx)) }
    LifecycleResumeEffect(Unit) {
        access.granted = notificationsAllowed(ctx)
        onPauseOrDispose { }
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        access.granted = notificationsAllowed(ctx)
    }
    access.reason?.let { why ->
        ConfirmSheet(
            title = stringResource(R.string.notify_ask_title),
            message = why,
            confirmLabel = stringResource(R.string.notify_ask_continue),
            dismissLabel = stringResource(R.string.notify_ask_not_now),
            onDismiss = { access.reason = null },
            onConfirm = {
                if (Build.VERSION.SDK_INT >= 33) request.launch(Manifest.permission.POST_NOTIFICATIONS)
                else openNotificationSettings(ctx)
            },
            destructive = false
        )
    }
    return access
}

/** Under a setting that's on while notifications are off: says so, and opens the phone's notification settings. */
@Composable
fun NotificationsOffNote(access: NotificationAccess, settingOn: Boolean) {
    if (!settingOn || access.granted) return
    val ctx = LocalContext.current
    SettingsNote(stringResource(R.string.notify_off), error = true)
    SettingsActionRow(stringResource(R.string.notify_open_settings)) { openNotificationSettings(ctx) }
}

/** Whether FitLens may post notifications: the permission on Android 13+, and not switched off for the app. */
fun notificationsAllowed(context: Context): Boolean =
    TimerService.canNotify(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()

/** Opens FitLens's page in the phone's notification settings, or its app page where that isn't offered. */
fun openNotificationSettings(context: Context) {
    val app = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(app)
    } catch (e: ActivityNotFoundException) {
        context.startActivity(
            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
