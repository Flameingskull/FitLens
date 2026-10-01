package com.fitlens.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The incremental snapshot update (#60): after a small set write, [mergeSets] swaps in the re-read sets of the
 * exercises or dates the write named and keeps every other set. Whatever it produces must equal what a full reload
 * (`ORDER BY date, position, id`) would have read.
 */
class StoreMergeTest {

    private fun set(id: Long, exercise: Long, date: String, position: Long, weight: Double = 100.0, reps: Int = 5) =
        SetRow(id, exercise, date, weight, reps, 0.0, 0, false, null, position = position)

    /** What a full reload reads from these rows. */
    private fun fullReload(rows: List<SetRow>) = rows.sortedWith(SET_ORDER)

    private val before = fullReload(
        listOf(
            set(1, 10, "2026-09-01", 1), set(2, 10, "2026-09-01", 2), set(3, 20, "2026-09-01", 3),
            set(4, 20, "2026-09-03", 1), set(5, 10, "2026-09-03", 2), set(6, 30, "2026-09-05", 1)
        )
    )

    @Test
    fun savingASetReReadsOnlyItsExercise() {
        val added = set(7, 10, "2026-09-05", 2, weight = 110.0)
        val database = before + added
        val fresh = database.filter { it.exerciseId == 10L }.sortedWith(SET_ORDER)

        val merged = mergeSets(before, fresh) { it.exerciseId == 10L }

        assertEquals(fullReload(database), merged)
        // Sets of other exercises are the very same objects, not copies.
        assertSame(before.first { it.id == 6L }, merged.first { it.id == 6L })
    }

    @Test
    fun editingADeletingAndMovingBetweenExercises() {
        // Set 2 is edited and moved from exercise 10 to 30; set 5 is deleted.
        val database = before.filter { it.id != 5L }.map { if (it.id == 2L) it.copy(exerciseId = 30, weightKg = 90.0) else it }
        val named = setOf(10L, 30L)
        val fresh = database.filter { it.exerciseId in named }.sortedWith(SET_ORDER)

        val merged = mergeSets(before, fresh) { it.exerciseId in named }

        assertEquals(fullReload(database), merged)
    }

    @Test
    fun aDayReorderedAsAWhole() {
        // A superset or a new order on 3 September changes both exercises' sets that day.
        val database = before.map { if (it.date == "2026-09-03") it.copy(position = 3 - it.position, superset = 1) else it }
        val fresh = database.filter { it.date == "2026-09-03" }.sortedWith(SET_ORDER)

        val merged = mergeSets(before, fresh) { it.date.take(10) == "2026-09-03" }

        assertEquals(fullReload(database), merged)
        assertEquals(listOf(5L, 4L), merged.filter { it.date == "2026-09-03" }.map { it.id })
    }

    @Test
    fun nothingNamedChangesNothing() {
        assertEquals(before, mergeSets(before, emptyList()) { false })
    }
}
