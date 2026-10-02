package com.fitlens.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The 1RM formulas and the exact unit constants (#139). Every call names its formula, so no settings are read. */
class RecordsTest {

    private fun f(reps: Int, formula: Records.Formula) = Records.factor(reps, formula)

    @Test
    fun eachFormulaGivesItsPublishedValue() {
        assertEquals(1 + 5 / 30.0, f(5, Records.Formula.EPLEY), 1e-9)
        assertEquals(36.0 / 32.0, f(5, Records.Formula.BRZYCKI), 1e-9)
        assertEquals(Math.pow(5.0, 0.1), f(5, Records.Formula.LOMBARDI), 1e-9)
        assertEquals(1.125, f(5, Records.Formula.OCONNER), 1e-9)
        assertEquals(1.190107, f(5, Records.Formula.MAYHEW), 1e-6)
        assertEquals(1.165825, f(5, Records.Formula.WATHAN), 1e-6)
    }

    @Test
    fun oneRepIsTheWeightLiftedForEveryFormula() {
        Records.Formula.entries.forEach { assertEquals(it.name, 1.0, f(1, it), 0.0) }
    }

    @Test
    fun automaticBlendsMayhewAndWathanUpToTenReps() {
        assertEquals(1.082922, f(2, Records.Formula.AUTO), 1e-6)
        assertEquals(1.177966, f(5, Records.Formula.AUTO), 1e-6)
        assertEquals(1.328405, f(10, Records.Formula.AUTO), 1e-6)
        assertEquals(117.7966, Records.oneRepMax(100.0, 5, Records.Formula.AUTO), 1e-3)
    }

    @Test
    fun automaticUsesWathanFromElevenToFifteenThenStops() {
        assertEquals(f(11, Records.Formula.WATHAN), f(11, Records.Formula.AUTO), 1e-12)
        assertEquals(1.509063, f(15, Records.Formula.AUTO), 1e-6)
        assertEquals(0.0, f(16, Records.Formula.AUTO), 0.0)
        assertEquals(0.0, f(0, Records.Formula.AUTO), 0.0)
        assertTrue(Records.approximate(11))
        assertFalse(Records.approximate(10))
    }

    @Test
    fun automaticRisesWithEveryRep() {
        val factors = (1..Records.MAX_ESTIMATE_REPS).map { f(it, Records.Formula.AUTO) }
        factors.zipWithNext().forEach { (a, b) -> assertTrue("$a then $b", b > a) }
    }

    @Test
    fun weightForInvertsTheEstimate() {
        val oneRm = Records.oneRepMax(100.0, 8, Records.Formula.AUTO)
        assertEquals(100.0, Records.weightFor(oneRm, 8, Records.Formula.AUTO), 1e-9)
    }

    @Test
    fun poundsUseTheExactInternationalDefinition() {
        assertEquals(0.45359237, WeightUnits.KG_PER_LB, 0.0)
        assertEquals(2.20462262185, WeightUnits.LB_PER_KG, 1e-11)
        val lbs = WeightUnits.convert(100.0, "kg", "lbs")
        assertEquals(100.0, WeightUnits.convert(lbs, "lbs", "kg"), 1e-9)
    }
}
