package com.fitlens.companion.ui

import com.fitlens.companion.R
import com.fitlens.companion.data.DeviceSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The warning on Settings' Automatic Backup row (#41): failed, overdue, or nothing to say. */
class AutoBackupProblemTest {

    private val day = 24L * 3600_000L
    private val now = 100 * day
    private val daily = DeviceSettings(autoBackupFolder = "content://tree/backups", autoBackupDays = 1)

    @Test
    fun offOrWithoutAFolderNeverWarns() {
        assertNull(autoBackupProblem(DeviceSettings(), now))
        assertNull(autoBackupProblem(DeviceSettings(autoBackupDays = 1, autoBackupError = "${now - day}|No folder"), now))
    }

    @Test
    fun aRecentBackupIsFine() {
        assertNull(autoBackupProblem(daily.copy(autoBackupLast = now - day), now))
    }

    @Test
    fun aFailureSinceTheLastBackupWarns() {
        val failed = daily.copy(autoBackupLast = now - 2 * day, autoBackupError = "${now - day}|Folder missing")
        assertEquals(R.string.settings_auto_backup_failed, autoBackupProblem(failed, now))
        assertEquals(R.string.settings_auto_backup_failed, autoBackupProblem(daily.copy(autoBackupError = "$now|Folder missing"), now))
    }

    @Test
    fun aDayPastTheScheduleIsOverdue() {
        assertNull(autoBackupProblem(daily.copy(autoBackupLast = now - 2 * day + 60_000L), now))
        assertEquals(R.string.settings_auto_backup_overdue, autoBackupProblem(daily.copy(autoBackupLast = now - 3 * day), now))
        val weekly = daily.copy(autoBackupDays = 7, autoBackupLast = now - 5 * day)
        assertNull(autoBackupProblem(weekly, now))
    }

    @Test
    fun afterChangesOnlyIsNeverOverdue() {
        val onChanges = daily.copy(autoBackupDays = 0, backupAfterChanges = true, autoBackupLast = now - 30 * day)
        assertNull(autoBackupProblem(onChanges, now))
    }
}
