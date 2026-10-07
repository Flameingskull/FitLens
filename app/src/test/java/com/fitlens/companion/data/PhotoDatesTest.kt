package com.fitlens.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** How an imported photo is dated (#162): a capture date always wins, and EXIF's edit time comes last but one. */
class PhotoDatesTest {

    private val shot = LocalDateTime.of(2026, 8, 26, 13, 20, 0)
    private val edited = LocalDateTime.of(2026, 9, 30, 21, 5, 0)
    private val media = LocalDateTime.of(2026, 8, 26, 13, 20, 1)
    private val named = LocalDateTime.of(2026, 8, 26, 13, 20, 2)
    private val modified = LocalDateTime.of(2026, 10, 1, 9, 0, 0)

    private fun pick(exif: ExifDates, media: LocalDateTime? = null, named: LocalDateTime? = null, modified: LocalDateTime? = null) =
        PhotoImporter.pickDate(exif, { media }, { named }, { modified })

    @Test
    fun readsCaptureAndEditDatesFromSyntheticExif() {
        val both = PhotoImporter.exifDates("2026:08:26 13:20:00", null, "2026:09:30 21:05:00")
        assertEquals(shot, both.captured)
        assertEquals(edited, both.edited)
        assertEquals(shot, PhotoImporter.exifDates(null, "2026:08:26 13:20:00", null).captured)
        assertEquals(shot, PhotoImporter.exifDates("0000:00:00 00:00:00", "2026:08:26 13:20:00", null).captured)
        assertEquals(shot, PhotoImporter.exifDates("garbage", "2026:08:26 13:20:00", null).captured)
        assertNull(PhotoImporter.exifDates(null, null, "2026:09:30 21:05:00").captured)
        assertNull(PhotoImporter.exifDates("1999:01:01 00:00:00", null, null).captured)
    }

    @Test
    fun captureDateAlwaysWins() {
        val exif = PhotoImporter.exifDates("2026:08:26 13:20:00", null, "2026:09:30 21:05:00")
        assertEquals(shot to DateSources.EXIF, pick(exif, media, named, modified))
    }

    @Test
    fun editTimeRanksBelowMediaLibraryAndFileName() {
        val exif = PhotoImporter.exifDates(null, null, "2026:09:30 21:05:00")
        assertEquals(media to DateSources.MEDIA, pick(exif, media, named, modified))
        assertEquals(named to DateSources.FILENAME, pick(exif, named = named, modified = modified))
        assertEquals(PhotoImporter.fileNameDate("IMG_20260826_132002.jpg"), named)
    }

    @Test
    fun editTimeAloneIsFlaggedForReview() {
        val exif = PhotoImporter.exifDates(null, null, "2026:09:30 21:05:00")
        val picked = pick(exif, modified = modified)
        assertEquals(edited to DateSources.EXIF_EDITED, picked)
        assertTrue(DateSources.needsReview(picked!!.second))
        assertFalse(DateSources.needsReview(DateSources.EXIF))
        val result = PhotoImportResult(3, 0, 0, mapOf(DateSources.EXIF_EDITED to 1, DateSources.FILE to 1, DateSources.EXIF to 1), emptyList())
        assertEquals(2, result.needsReview)
    }

    @Test
    fun laterSourcesAreOnlyLookedUpWhenNeeded() {
        var asked = false
        val exif = PhotoImporter.exifDates("2026:08:26 13:20:00", null, null)
        PhotoImporter.pickDate(exif, { asked = true; media }, { asked = true; named }, { asked = true; modified })
        assertFalse(asked)
        assertEquals(modified to DateSources.FILE, pick(ExifDates(null, null), modified = modified))
        assertNull(pick(ExifDates(null, null)))
    }

    private fun photo(source: String, date: String? = "2026-09-30", takenAt: String? = "2026-09-30T21:05", name: String? = "photo.jpg") =
        Photo(7L, "abc.jpg", date, takenAt, source, Poses.NONE, null, name)

    @Test
    fun redateNeverTouchesDatesSetByHand() {
        val exif = PhotoImporter.exifDates("2026:08:26 13:20:00", null, null)
        assertNull(PhotoImporter.redate(photo(DateSources.MANUAL), exif))
    }

    @Test
    fun redateMovesAnEditDayPhotoToItsCaptureDate() {
        val exif = PhotoImporter.exifDates("2026:08:26 13:20:00", null, "2026:09:30 21:05:00")
        assertEquals(Redate(7L, "2026-08-26", shot.toString(), DateSources.EXIF), PhotoImporter.redate(photo(DateSources.EXIF), exif))
        assertEquals(Redate(7L, "2026-08-26", shot.toString(), DateSources.EXIF), PhotoImporter.redate(photo(DateSources.FILE), exif))
    }

    @Test
    fun redateUsesTheFileNameBeforeTheEditTime() {
        val exif = PhotoImporter.exifDates(null, null, "2026:09:30 21:05:00")
        val named = photo(DateSources.EXIF, name = "IMG_20260826_132002.jpg")
        assertEquals(Redate(7L, "2026-08-26", this.named.toString(), DateSources.FILENAME), PhotoImporter.redate(named, exif))
        // An old import dated by the edit time is flagged for review, with the same date.
        assertEquals(Redate(7L, "2026-09-30", edited.toString(), DateSources.EXIF_EDITED), PhotoImporter.redate(photo(DateSources.EXIF), exif))
    }

    @Test
    fun redateKeepsTheMediaLibraryDateOverWeakerSources() {
        val exif = PhotoImporter.exifDates(null, null, "2026:09:30 21:05:00")
        assertNull(PhotoImporter.redate(photo(DateSources.MEDIA, name = "IMG_20260826_132002.jpg"), exif))
        val captured = PhotoImporter.exifDates("2026:08:26 13:20:00", null, null)
        assertEquals(DateSources.EXIF, PhotoImporter.redate(photo(DateSources.MEDIA), captured)?.source)
    }

    @Test
    fun redateLeavesUnchangedAndUnreadablePhotosAlone() {
        val exif = PhotoImporter.exifDates("2026:08:26 13:20:00", null, null)
        assertNull(PhotoImporter.redate(photo(DateSources.EXIF, "2026-08-26", shot.toString()), exif))
        assertNull(PhotoImporter.redate(photo(DateSources.FILE), ExifDates(null, null)))
    }
}
