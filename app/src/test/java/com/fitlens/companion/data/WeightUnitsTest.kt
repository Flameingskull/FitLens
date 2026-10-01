package com.fitlens.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** Switching between kg and lbs (#117): body values follow the setting, stored values never change. */
class WeightUnitsTest {

    private fun rec(value: Double, unit: String) = MRecord(1, "Bodyweight", unit, "2026-10-01", "08:00", value, null, "manual")

    @Test
    fun recognisesWeightUnitsOnly() {
        assertEquals("kg", WeightUnits.of(" KG "))
        assertEquals("lbs", WeightUnits.of("lb"))
        assertNull(WeightUnits.of("cm"))
        assertNull(WeightUnits.of("%"))
        assertEquals(170.0, WeightUnits.convert(170.0, "cm", "lbs"), 0.0)
    }

    @Test
    fun convertsBothWaysWithoutDrift() {
        val lbs = WeightUnits.convert(80.0, "kg", "lbs")
        assertEquals(176.37, lbs, 0.01)
        assertEquals(80.0, WeightUnits.convert(lbs, "lbs", "kg"), 1e-9)
    }

    @Test
    fun bodyValuesFollowTheSettingAndKeepWhatsStored() {
        val defs = listOf(
            MeasurementDef("Bodyweight", "kg", 0, MeasurementGoals.TARGET, 75.0, true),
            MeasurementDef("Waist", "cm", 1, 0, 0.0, true)
        )
        val records = listOf(rec(80.0, "kg"), rec(90.0, "cm").copy(id = 2, name = "Waist"))
        val kg = BodyPart(defs, records, "kg")
        val lbs = kg.withUnit("lbs")

        assertEquals(176.37, lbs.records[0].value, 0.01)
        assertEquals("lbs", lbs.records[0].unit)
        assertEquals(165.35, lbs.measurementDefs[0].goalValue, 0.01)
        // Other units are untouched, and the stored rows are the same objects.
        assertEquals(90.0, lbs.records[1].value, 0.0)
        assertEquals("cm", lbs.records[1].unit)
        assertSame(kg.rawRecords, lbs.rawRecords)
        assertSame(kg, kg.withUnit("kg"))
        assertEquals(80.0, lbs.withUnit("kg").records[0].value, 1e-9)
    }

    @Test
    fun plateListFollowsTheUnit() {
        val kg = PortableSettings(weightUnit = "kg", plates = "20, 10, 5")
        val lbs = withPlatesConverted(kg, kg.copy(weightUnit = "lbs"))
        assertEquals("44.09, 22.05, 11.02", lbs.plates)
        // No list, or a list edited in the same change, is left alone.
        assertNull(withPlatesConverted(kg.copy(plates = null), kg.copy(weightUnit = "lbs", plates = null)).plates)
        assertEquals("45", withPlatesConverted(kg, kg.copy(weightUnit = "lbs", plates = "45")).plates)
    }
}
