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

    @Test
    fun sectionResetTouchesOnlyItsGroup() {
        val prefs = custom.copy(homeSetsShown = 3, restVolume = 40, photoGroupBy = MediaPrefs.GROUP_YEAR)
        val rest = prefs.withGroupFrom(PreferenceGroup.REST, PortableSettings())
        assertEquals(prefs.copy(restSeconds = 90, restSound = true, restVolume = 80), rest)
        val home = prefs.withGroupFrom(PreferenceGroup.HOME, PortableSettings())
        assertEquals(prefs.copy(homeSetsShown = 0), home)
        val media = prefs.withGroupFrom(PreferenceGroup.MEDIA, PortableSettings())
        assertEquals(prefs.copy(photoGroupBy = MediaPrefs.GROUP_MONTH, videoOpts = null, pdfPhotosPerDay = 2), media)
        // Undo puts the group back without undoing a change made since to another group.
        val later = rest.copy(homeSetsShown = 5)
        assertEquals(prefs.copy(homeSetsShown = 5), later.withGroupFrom(PreferenceGroup.REST, prefs))
    }

    @Test
    fun sectionResetOnThePhone() {
        val phone = DeviceSettings(restSoundUri = "content://sound", calendarFilter = "f", setupDone = true)
        assertEquals(phone.copy(restSoundUri = null), phone.withGroupFrom(PreferenceGroup.REST, DeviceSettings()))
        assertEquals(phone, phone.withGroupFrom(PreferenceGroup.HOME, DeviceSettings()))
    }
}
