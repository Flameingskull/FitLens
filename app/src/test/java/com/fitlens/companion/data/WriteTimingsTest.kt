package com.fitlens.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WriteTimingsTest {
    private fun t(ms: Long) = WriteTimings.Timing(WriteTimings.SET, ms, 0, 0)

    @Test
    fun medianOfNothingIsNull() = assertNull(WriteTimings.median(emptyList()))

    @Test
    fun medianTakesTheMiddleOrTheLowerMiddle() {
        assertEquals(7L, WriteTimings.median(listOf(30L, 7L, 2L)))
        assertEquals(7L, WriteTimings.median(listOf(30L, 7L, 2L, 9L)))
    }

    @Test
    fun keepsOnlyTheNewest() {
        val list = (1..WriteTimings.KEEP + 5).map { t(it.toLong()) }
        val kept = WriteTimings.keep(list)
        assertEquals(WriteTimings.KEEP, kept.size)
        assertEquals(6L, kept.first().dbMs)
        assertEquals((WriteTimings.KEEP + 5).toLong(), kept.last().totalMs)
    }

    @Test
    fun recordSplitsTheDatabaseAndTheRefresh() {
        WriteTimings.record(WriteTimings.SET, 0L, 3_000_000L, 10_000_000L, 42)
        val last = WriteTimings.recent.value.last()
        assertEquals(3L, last.dbMs)
        assertEquals(7L, last.refreshMs)
        assertEquals(10L, last.totalMs)
        assertEquals(42, last.sets)
    }
}
