package com.fitlens.companion.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Full-screen zoom (#96): time and values zoom apart, stay inside the chart and reset to the whole chart. */
class ChartViewportTest {

    private val eps = 1e-5f

    @Test
    fun zoomingValuesLeavesTimeAlone() {
        val v = ChartViewport().transformY(centroid = 0.5f, pan = 0f, zoom = 2f)
        assertEquals(0f, v.from, eps)
        assertEquals(1f, v.to, eps)
        assertEquals(0.25f, v.yFrom, eps)
        assertEquals(0.75f, v.yTo, eps)
        assertFalse(v.isFull)
        assertTrue(v.copy(yFrom = 0f, yTo = 1f).isFull)
    }

    @Test
    fun draggingDownShowsHigherValues() {
        val zoomed = ChartViewport().transformY(0.5f, 0f, 4f)
        val moved = zoomed.transformY(0.5f, pan = 0.5f, zoom = 1f)
        assertTrue(moved.yFrom > zoomed.yFrom)
        assertEquals(zoomed.yTo - zoomed.yFrom, moved.yTo - moved.yFrom, eps)
    }

    @Test
    fun valuesStayInsideTheChartAndStopAtTheLimit() {
        val v = ChartViewport().transformY(0f, pan = -10f, zoom = 1000f)
        assertEquals(0f, v.yFrom, eps)
        assertEquals(0.05f, v.yTo, eps)
        val out = v.transformY(0.5f, 0f, 0.001f)
        assertTrue(out.valuesFull)
    }

    @Test
    fun zoomingTimeLeavesValuesAlone() {
        val v = ChartViewport(yFrom = 0.2f, yTo = 0.6f).transform(0.5f, 0f, 2f)
        assertEquals(0.2f, v.yFrom, eps)
        assertEquals(0.6f, v.yTo, eps)
        assertEquals(0.5f, v.to - v.from, eps)
    }
}
