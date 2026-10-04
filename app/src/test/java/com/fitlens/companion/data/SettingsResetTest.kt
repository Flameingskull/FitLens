package com.fitlens.companion.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Reset settings to defaults (#41): preferences go back to their defaults, the user's own things stay. */
class SettingsResetTest {

    private val custom = PortableSettings(
        weightUnit = "lbs", weightUnitManual = true, distanceUnit = DistanceUnits.MI, lengthUnit = LengthUnits.IN,
        weightIncrementKg = 1.0, keepScreenOn = false, celebratePrs = false, restSeconds = 180, restSound = false,
        markComplete = true, graphKinds = "exercise:1=bar", pinnedGraphs = "pin", graphCompare = "1=2",
        lastRoutineId = 7L, profileSex = "female", plates = "45, 25", videoOpts = "x", pdfPhotosPerDay = 4
    )

    @Test
    fun preferencesGoBackToDefaults() {
        val reset = custom.resetToDefaults()
        val keep = PortableSettings(pinnedGraphs = "pin", graphCompare = "1=2", lastRoutineId = 7L, profileSex = "female")
        assertEquals(keep, reset)
    }

    @Test
    fun undoGivesBackTheOldPlates() {
        // Undo switches lbs back on with the old plate list: it must not be converted a second time.
        val reset = custom.resetToDefaults()
        assertEquals(custom, withPlatesConverted(reset, custom))
    }

    @Test
    fun phoneKeepsFoldersAndBackups() {
        val phone = DeviceSettings(
            backupFolder = "tree:a", autoBackupFolder = "tree:b", autoBackupDays = 7, autoBackupKeep = 3,
            lastImportName = "FitNotes.fitnotes", setupDone = true, whatsNewSeen = 100,
            restSoundUri = "content://sound", calendarFilter = "f"
        )
        val reset = phone.resetToDefaults()
        assertEquals(phone.copy(restSoundUri = null, calendarFilter = null), reset)
        assertEquals(phone, reset.withPreferencesFrom(phone))
    }
}
