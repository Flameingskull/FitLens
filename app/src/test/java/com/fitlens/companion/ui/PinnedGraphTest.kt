package com.fitlens.companion.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pinned graphs (#55) and comparisons (#53), stored as text in the portable settings so they travel in backups. */
class PinnedGraphTest {

    @Test
    fun pinsRoundTripInOrder() {
        val pins = listOf(
            PinnedGraph(3, "Estimated 1RM", 2, listOf(7, 9), relative = true),
            PinnedGraph(1, "Max Pace", 4),
            PinnedGraph(3, "Workout Volume", 0)
        )
        assertEquals(pins, PinnedGraph.decode(PinnedGraph.encode(pins)))
    }

    @Test
    fun badLinesAreSkipped() {
        val decoded = PinnedGraph.decode("x|Max Weight|1\n5||2\n5|Max Weight|99|5,6,6|0\n5|Max Weight|1|8|1\nnonsense")
        // The bad exercise id, the blank graph and the junk line go; the range falls back to All; the exercise itself
        // and repeats leave the comparison; the second pin of the same graph is dropped.
        assertEquals(listOf(PinnedGraph(5, "Max Weight", 4, listOf(6), false)), decoded)
    }

    @Test
    fun nothingPinnedIsNull() {
        assertNull(PinnedGraph.encode(emptyList()))
        assertEquals(emptyList<PinnedGraph>(), PinnedGraph.decode(null))
    }

    @Test
    fun comparisonsRoundTripAndCap() {
        val m = mapOf(1L to listOf(2L, 3L), 4L to listOf(5L))
        assertEquals(m, GraphCompare.decode(GraphCompare.encode(m)))
        // At most four others, never the exercise itself; an empty list is forgotten.
        assertEquals(mapOf(1L to listOf(2L, 3L, 4L, 5L)), GraphCompare.decode("1=1,2,3,4,5,6;7="))
        assertNull(GraphCompare.encode(mapOf(1L to emptyList())))
    }
}
