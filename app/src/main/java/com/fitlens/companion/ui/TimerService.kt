package com.fitlens.companion.ui

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.Store
import com.fitlens.companion.data.fmtDuration
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the rest timer (#20) and the workout timer (#12) going with the screen off: a foreground service with one
 * ongoing notification. The system draws its countdown or count-up, so nothing ticks while the screen is off. It
 * starts when either timer starts and stops itself as soon as neither is running.
 */
class TimerService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A service started in the foreground must call startForeground straight away, even if it's about to stop.
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        try {
            ServiceCompat.startForeground(this, ONGOING_ID, build(this), type)
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_PAUSE -> if (RestTimer.state.value.paused) RestTimer.resume() else RestTimer.pause()
            ACTION_ADD -> RestTimer.adjust(15)
            ACTION_STOP_REST -> RestTimer.stop()
            ACTION_STOP_WORKOUT -> {
                val today = Dates.today()
                Store.snapshot.value?.let { WorkoutClock.running(it, today) }?.let { WorkoutClock.stop(today, it) }
            }
        }
        update()
        return START_NOT_STICKY
    }

    /** Redraws the notification, or stops the service when neither timer is running. */
    fun update() {
        if (!wanted()) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        try {
            getSystemService(NotificationManager::class.java)?.notify(ONGOING_ID, build(this))
        } catch (e: SecurityException) {
            // Notifications turned off: the timers still run.
        }
    }

    companion object {
        private const val CHANNEL_ONGOING = "timers"
        private const val CHANNEL_ALERT = "timer_alerts"
        private const val ONGOING_ID = 2001
        private const val ALERT_ID = 2002
        private const val ACTION_PAUSE = "com.fitlens.companion.timer.PAUSE"
        private const val ACTION_ADD = "com.fitlens.companion.timer.ADD"
        private const val ACTION_STOP_REST = "com.fitlens.companion.timer.STOP_REST"
        private const val ACTION_STOP_WORKOUT = "com.fitlens.companion.timer.STOP_WORKOUT"

        @Volatile
        private var instance: TimerService? = null

        private fun workoutStart(): String? = Store.snapshot.value?.let { WorkoutClock.running(it, Dates.today()) }

        private fun wanted(): Boolean = RestTimer.state.value.active || workoutStart() != null

        /**
         * Brings the notification up to date: updates the running service, or starts it when a timer has just
         * started. Starting can fail if FitLens is in the background; the timers still run while the app does.
         */
        fun refresh(context: Context) {
            val running = instance
            if (running != null) {
                running.update()
                return
            }
            if (!wanted()) return
            try {
                ContextCompat.startForegroundService(context, Intent(context, TimerService::class.java))
            } catch (e: Exception) {
                // Not allowed from the background; the in-app timers carry on regardless.
            }
        }

        /** Follows the workout timer, so starting or stopping it anywhere updates the notification. */
        fun watch(app: Context) {
            AppScope.scope.launch {
                Store.snapshot.map { s -> s?.let { WorkoutClock.running(it, Dates.today()) } }
                    .distinctUntilChanged()
                    .collect { refresh(app) }
            }
        }

        /** The "rest is over" alert: a heads-up notification that also vibrates with the screen off. */
        fun alertRestOver(context: Context) {
            if (!canNotify(context)) return
            channels(context)
            val n = NotificationCompat.Builder(context, CHANNEL_ALERT)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setColor(Brand.Gold.toArgb())
                .setContentTitle("Rest over")
                .setContentText("Time for your next set.")
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(openApp(context))
                .setAutoCancel(true)
                .setTimeoutAfter(60_000)
                .build()
            try {
                context.getSystemService(NotificationManager::class.java)?.notify(ALERT_ID, n)
            } catch (e: SecurityException) {
                // Permission withdrawn.
            }
        }

        fun canNotify(context: Context): Boolean = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        private fun channels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ONGOING, "Timers", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "The rest timer and workout timer while they run"
                    setShowBadge(false)
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ALERT, "Rest over", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "When the rest timer ends"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 400, 200, 400)
                }
            )
        }

        private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        private fun action(context: Context, action: String, code: Int): PendingIntent = PendingIntent.getService(
            context, code, Intent(context, TimerService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        /** The ongoing notification for whichever timers are running. */
        private fun build(context: Context): android.app.Notification {
            channels(context)
            val rest = RestTimer.state.value
            val workout = workoutStart()
            val b = NotificationCompat.Builder(context, CHANNEL_ONGOING)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setColor(Brand.Gold.toArgb())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
                .setContentIntent(openApp(context))
            val workoutLine = workout?.let { "Workout timer running since ${it.drop(11).take(5)}" }
            when {
                rest.active && rest.paused -> {
                    b.setContentTitle("Rest paused · ${fmtDuration(RestTimer.left(rest))} left")
                    workoutLine?.let { b.setContentText(it) }
                }
                rest.active -> {
                    b.setContentTitle("Rest")
                        .setUsesChronometer(true)
                        .setChronometerCountDown(true)
                        .setWhen(rest.endAt)
                        .setShowWhen(true)
                    b.setContentText(workoutLine ?: "Your next set is coming up")
                }
                workout != null -> {
                    val startMs = Dates.dateTime(workout)?.atZone(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                        ?: System.currentTimeMillis()
                    b.setContentTitle("Workout")
                        .setUsesChronometer(true)
                        .setWhen(startMs)
                        .setShowWhen(true)
                        .setContentText("Workout timer running")
                }
                else -> b.setContentTitle("Timers stopped")
            }
            if (rest.active) {
                b.addAction(0, if (rest.paused) "Resume" else "Pause", action(context, ACTION_PAUSE, 1))
                b.addAction(0, "+15 s", action(context, ACTION_ADD, 2))
                b.addAction(0, "Stop rest", action(context, ACTION_STOP_REST, 3))
            } else if (workout != null) {
                b.addAction(0, "Stop workout", action(context, ACTION_STOP_WORKOUT, 4))
            }
            return b.build()
        }
    }
}
