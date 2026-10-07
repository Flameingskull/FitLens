package com.fitlens.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A screen's view of the snapshot (#60): it records the areas the screen reads, and counts as changed only when one
 * of those areas, or a unit, differs in a newer snapshot.
 */
class ScreenSnapshotTest {

    private fun set(id: Long, exercise: Long) = SetRow(id, exercise, "2026-09-01", 100.0, 5, 0.0, 0, false, null, position = id)

    private fun newLibrary() = LibraryPart(emptyMap(), emptyMap(), emptyList(), emptyList(), emptyMap())
    private fun sets(vararg rows: SetRow) = SetPart(rows.toList(), false)
    private fun newNotes() = NotesPart(emptyMap(), emptyMap(), emptyMap())
    private fun newBody() = BodyPart(emptyList(), emptyList(), "kg")
    private fun photos() = PhotoPart(emptyList())

    private fun snapshot(
        library: LibraryPart = newLibrary(),
        setPart: SetPart = sets(),
        notes: NotesPart = newNotes(),
        body: BodyPart = newBody(),
        photoPart: PhotoPart = photos(),
        weightUnit: String = "kg"
    ) = Snapshot(library, setPart, notes, body, photoPart, weightUnit, File("photos"))

    @Test
    fun aViewRecordsOnlyTheAreasRead() {
        var firstReads = 0
        val reads = AreaReads { firstReads++ }
        val view = snapshot(setPart = sets(set(1, 10))).viewFor(reads)

        assertEquals(1, view.sets.size)
        view.setsByDate
        view.trainingKey

        assertTrue(Area.SETS in reads)
        assertTrue(Area.LIBRARY in reads)
        assertTrue(Area.NOTES in reads)
        assertFalse(Area.PHOTOS in reads)
        assertFalse(Area.BODY in reads)
        // Each area counts once, however often it's read.
        assertEquals(3, firstReads)
    }

    @Test
    fun allDatesReadsEveryAreaItIsBuiltFrom() {
        val reads = AreaReads()
        snapshot(setPart = sets(set(1, 10))).viewFor(reads).allDates.let { assertEquals(listOf("2026-09-01"), it) }
        assertTrue(Area.SETS in reads && Area.BODY in reads && Area.PHOTOS in reads && Area.NOTES in reads)
        assertFalse(Area.LIBRARY in reads)
    }

    @Test
    fun aWriteInAnUnreadAreaLeavesTheViewCurrent() {
        val reads = AreaReads()
        val lib = newLibrary()
        val setPart = sets(set(1, 10))
        val first = snapshot(library = lib, setPart = setPart)
        val view = first.viewFor(reads)
        view.sets
        view.exercises

        // A photo import or a body value: new parts there, the same library and sets.
        val afterPhotos = snapshot(library = lib, setPart = setPart, photoPart = photos(), body = newBody())
        assertFalse(view.changedIn(afterPhotos, reads))

        // A saved set replaces the sets the screen shows.
        val afterSet = snapshot(library = lib, setPart = sets(set(1, 10), set(2, 10)))
        assertTrue(view.changedIn(afterSet, reads))

        // A unit change always counts.
        val inPounds = snapshot(library = lib, setPart = setPart, weightUnit = "lbs")
        assertTrue(view.changedIn(inPounds, reads))
    }

    @Test
    fun theSnapshotItselfRecordsNothing() {
        val s = snapshot(setPart = sets(set(1, 10)))
        // The live snapshot has no recorder, so the store and other readers are unaffected.
        assertEquals(1, s.sets.size)
        assertEquals(listOf("2026-09-01"), s.allDates)
    }
}
