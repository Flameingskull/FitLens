package com.fitlens.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Progress photo and media preferences (#46): stored as text, validated when read back. */
class MediaPrefsTest {

    @Test
    fun slideshowOptionsRoundTrip() {
        val prefs = SlideshowPrefs(
            pose = "Side", onePerDay = false, seconds = 2.5f, fade = false, showDate = false, showDays = true,
            showPose = false, title = "Cut: week 1 = 82 kg; 100% & more\nnext line", format = "1080x1920",
            overlays = listOf("Bodyweight" to true, "Waist (cm)" to false, "Arm: left=right" to false)
        )
        val back = SlideshowPrefs.decode(prefs.encode())
        assertEquals(prefs.copy(title = prefs.title), back)
    }

    @Test
    fun emptyOverlayChoiceIsKept() {
        // Choosing no data at all is a choice, not "use the defaults".
        assertEquals(emptyList<Pair<String, Boolean>>(), SlideshowPrefs.decode(SlideshowPrefs(overlays = emptyList()).encode()).overlays)
        assertNull(SlideshowPrefs.decode(SlideshowPrefs().encode()).overlays)
    }

    @Test
    fun badValuesFallBackToDefaults() {
        val d = SlideshowPrefs()
        val decoded = SlideshowPrefs.decode(
            "pose=Upside%20down\nseconds=99\nfade=maybe\nformat=huge\nfuture=1\nnonsense\n=x\n" +
                "overlay=1:Weight\noverlay=1:Weight\noverlay=x\noverlay=0:\n" +
                (1..8).joinToString("\n") { "overlay=0:M$it" }
        )
        assertEquals(d.pose, decoded.pose)
        assertEquals(d.seconds, decoded.seconds)
        assertEquals(d.fade, decoded.fade)
        assertEquals(d.format, decoded.format)
        // Repeats and blank names go, and no more than five are kept.
        assertEquals(listOf("Weight" to true, "M1" to false, "M2" to false, "M3" to false, "M4" to false), decoded.overlays)
        assertEquals(d, SlideshowPrefs.decode(null))
        assertEquals(d, SlideshowPrefs.decode("garbage without equals"))
    }

    @Test
    fun storedPreferencesAreValidated() {
        assertNull(MediaPrefs.defaultPoseOf(null))
        assertNull(MediaPrefs.defaultPoseOf("Sideways"))
        assertEquals(Poses.NONE, MediaPrefs.defaultPoseOf("none"))
        assertEquals("Back", MediaPrefs.defaultPoseOf("Back"))
        assertEquals("none", MediaPrefs.storeDefaultPose(Poses.NONE))
        assertNull(MediaPrefs.storeDefaultPose(null))
        assertEquals("month", MediaPrefs.groupOf("fortnight"))
        assertEquals("week", MediaPrefs.groupOf("week"))
        assertEquals(2, MediaPrefs.photosPerDayOf("9"))
        assertEquals(0, MediaPrefs.photosPerDayOf("0"))
        assertEquals(2, MediaPrefs.photosPerDayOf("x"))
    }

    @Test
    fun sectionsStartOnTheRightDay() {
        val thu = LocalDate.of(2026, 10, 1)
        assertEquals(LocalDate.of(2026, 9, 28), MediaPrefs.sectionStart(thu, MediaPrefs.GROUP_WEEK, 1))
        assertEquals(LocalDate.of(2026, 9, 27), MediaPrefs.sectionStart(thu, MediaPrefs.GROUP_WEEK, 7))
        assertEquals(LocalDate.of(2026, 10, 1), MediaPrefs.sectionStart(thu, MediaPrefs.GROUP_MONTH, 1))
        assertEquals(LocalDate.of(2026, 1, 1), MediaPrefs.sectionStart(thu, MediaPrefs.GROUP_YEAR, 1))
        assertEquals(thu, MediaPrefs.sectionStart(thu, MediaPrefs.GROUP_DAY, 1))
    }
}
