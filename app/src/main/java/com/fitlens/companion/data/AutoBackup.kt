package com.fitlens.companion.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.system.Os
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.fitlens.companion.ui.Brand
import com.fitlens.companion.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Automatic backups in the background (#34), using Android's job scheduler through WorkManager.
 * Backups go only to the folder the user chose ([Backups.autoFolder]). There's no network constraint and FitLens has
 * no internet permission; jobs run when the battery isn't low.
 *
 * - **Scheduled:** a periodic job checks every few hours and saves a backup when the daily or weekly one is due,
 *   even if FitLens isn't opened.
 * - **After changes (optional):** when FitLens goes to the background after data changed, a one-off job saves a
 *   backup, at most once an hour.
 * - If the folder can't be reached, a notification explains how to fix it, and Settings shows the problem until a
 *   backup works again. A failed backup is retried at the next check, so backups are never skipped silently.
 */
object AutoBackup {

    const val KEY_REASON = "reason"
    const val REASON_SCHEDULED = "scheduled"
    const val REASON_CHANGES = "changes"
    /** Intent extra: open Settings → Backups. */
    const val EXTRA_OPEN_BACKUPS = "com.fitlens.companion.OPEN_BACKUPS"
    const val THROTTLE_MS = 3600_000L

    private const val WORK_PERIODIC = "fitlens_auto_backup"
    private const val WORK_CHANGES = "fitlens_backup_after_changes"
    private const val CHECK_HOURS = 3L
    private const val CHANNEL = "backups"
    private const val NOTIFY_ID = 3401

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun afterChangesEnabled(): Boolean = Settings.current().backupAfterChanges
    fun setAfterChanges(on: Boolean) = Settings.updateDevice { it.copy(backupAfterChanges = on) }
    fun isDirty(): Boolean = Settings.current().backupDirty

    /** Called when a backup to the folder succeeded. */
    suspend fun markBackedUp() = Settings.updateDeviceNow { it.copy(backupDirty = false) }

    /** Called once per process, from [com.fitlens.companion.App]: keeps the schedule and watches for data changes. */
    fun start(context: Context) {
        val app = context.applicationContext
        scope.launch {
            try {
                schedule(app)
            } catch (e: Exception) {
                // WorkManager unavailable; backups still run when FitLens opens.
            }
            // Every snapshot after the first one means FitLens data changed (imports, edits, photos, restores).
            Store.snapshot.filterNotNull().drop(1).collect {
                try {
                    if (!isDirty()) Settings.updateDevice { it.copy(backupDirty = true) }
                } catch (e: Exception) {
                    // Database being replaced by a restore; the next change marks it.
                }
            }
        }
    }

    private fun constraints(): Constraints = Constraints.Builder().setRequiresBatteryNotLow(true).build()

    /** Starts, updates or stops the periodic job to match the automatic backup settings. */
    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context.applicationContext)
        if (Backups.autoFolder() == null || Backups.autoDays() <= 0) {
            wm.cancelUniqueWork(WORK_PERIODIC)
            return
        }
        val request = PeriodicWorkRequestBuilder<BackupWorker>(CHECK_HOURS, TimeUnit.HOURS)
            .setConstraints(constraints())
            .setInputData(workDataOf(KEY_REASON to REASON_SCHEDULED))
            .build()
        wm.enqueueUniquePeriodicWork(WORK_PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** FitLens went to the background: queue a backup if data changed and "back up after changes" is on. */
    fun onAppBackground(context: Context) {
        val app = context.applicationContext
        scope.launch {
            try {
                if (!afterChangesEnabled() || Backups.autoFolder() == null || !isDirty()) return@launch
                val last = Backups.lastAutoBackup() ?: 0L
                val wait = (last + THROTTLE_MS - System.currentTimeMillis()).coerceAtLeast(60_000L)
                val request = OneTimeWorkRequestBuilder<BackupWorker>()
                    .setInitialDelay(wait, TimeUnit.MILLISECONDS)
                    .setConstraints(constraints())
                    .setInputData(workDataOf(KEY_REASON to REASON_CHANGES))
                    .build()
                WorkManager.getInstance(app).enqueueUniqueWork(WORK_CHANGES, ExistingWorkPolicy.KEEP, request)
            } catch (e: Exception) {
                // Nothing to do: the scheduled backup still covers the change.
            }
        }
    }

    /** When the next scheduled backup is due (it runs at the first check after this, when the battery isn't low). */
    fun nextDue(): Long? {
        val days = Backups.autoDays()
        if (days <= 0 || Backups.autoFolder() == null) return null
        val now = System.currentTimeMillis()
        val last = Backups.lastAutoBackup() ?: return now
        return maxOf(now, last + days * 24L * 3600_000L - 3600_000L)
    }

    // ---------- Folder status ----------

    /** Whether the backup folder can be reached, and its free space when the storage reports it. */
    class FolderStatus(val reachable: Boolean, val freeBytes: Long?)

    suspend fun folderStatus(context: Context): FolderStatus? = withContext(Dispatchers.IO) {
        val tree = Backups.autoFolder() ?: return@withContext null
        try {
            val resolver = context.contentResolver
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            var probe: Uri? = null
            val cursor = resolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                ),
                null, null, null
            ) ?: return@withContext FolderStatus(false, null)
            cursor.use { c ->
                while (c.moveToNext()) {
                    if (c.getString(1) == DocumentsContract.Document.MIME_TYPE_DIR) continue
                    val isOurs = (c.getString(2) ?: "").startsWith("FitLens_")
                    if (probe == null || isOurs) probe = DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
                    if (isOurs) break
                }
            }
            // Free space is read from the storage holding a file in the folder (Android has no call for a folder).
            val free = probe?.let { u ->
                try {
                    resolver.openFileDescriptor(u, "r")?.use { pfd ->
                        val st = Os.fstatvfs(pfd.fileDescriptor)
                        st.f_bavail * st.f_frsize
                    }
                } catch (e: Exception) {
                    null
                }
            }
            FolderStatus(true, free?.takeIf { it > 0 })
        } catch (e: Exception) {
            FolderStatus(false, null)
        }
    }

    // ---------- Notification ----------

    fun clearProblem(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFY_ID)
    }

    /** Tells the user an automatic backup failed and how to fix it. Needs the notification permission on Android 13+. */
    fun notifyProblem(context: Context, result: ImportSummary) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Backups", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Problems with automatic backups"
            }
        )
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_BACKUPS, true)
        }
        val pending = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val title = if (result.folderProblem) "FitLens can't reach your backup folder" else "Automatic backup didn't finish"
        val text = if (result.folderProblem) {
            result.message + " Tap to open the backup settings."
        } else {
            result.message + " FitLens will try again."
        }
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setColor(Brand.Gold.toArgb())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(NOTIFY_ID, n)
        } catch (e: SecurityException) {
            // Permission withdrawn; Settings still shows the problem.
        }
    }
}

/** The background backup job. See [AutoBackup]. */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (Store.snapshot.value == null) Store.reload()
        if (Backups.autoFolder() == null) return Result.success()
        val reason = inputData.getString(AutoBackup.KEY_REASON) ?: AutoBackup.REASON_SCHEDULED
        val result: ImportSummary = if (reason == AutoBackup.REASON_CHANGES) {
            if (!AutoBackup.afterChangesEnabled() || !AutoBackup.isDirty()) return Result.success()
            val last = Backups.lastAutoBackup() ?: 0L
            // Another backup ran meanwhile: the next time FitLens goes to the background queues a new one.
            if (System.currentTimeMillis() - last < AutoBackup.THROTTLE_MS - 120_000L) return Result.success()
            Backups.backupToFolder(ctx)
        } else {
            Backups.autoBackupIfDue(ctx) ?: return Result.success()
        }
        if (result.ok) {
            AutoBackup.clearProblem(ctx)
            return Result.success()
        }
        AutoBackup.notifyProblem(ctx, result)
        // A missing folder won't come back by retrying now; the next periodic check tries again.
        return if (!result.folderProblem && runAttemptCount < 2) Result.retry() else Result.success()
    }
}
