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
        val lbs = kg.withUnits("lbs", "cm")

        assertEquals(176.37, lbs.records[0].value, 0.01)
        assertEquals("lbs", lbs.records[0].unit)
        assertEquals(165.35, lbs.measurementDefs[0].goalValue, 0.01)
        // Other units are untouched, and the stored rows are the same objects.
        assertEquals(90.0, lbs.records[1].value, 0.0)
        assertEquals("cm", lbs.records[1].unit)
        assertSame(kg.rawRecords, lbs.rawRecords)
        assertSame(kg, kg.withUnits("kg", "cm"))
        assertEquals(80.0, lbs.withUnits("kg", "cm").records[0].value, 1e-9)
    }

    @Test
    fun lengthsFollowTheLengthUnitAndOtherUnitsStay() {
        assertEquals("in", LengthUnits.of("Inches"))
        assertNull(LengthUnits.of("kg"))
        val defs = listOf(MeasurementDef("Waist", "cm", 0, MeasurementGoals.TARGET, 80.0, true), MeasurementDef("Body fat", "%", 1, 0, 0.0, true))
        val records = listOf(rec(90.0, "cm").copy(name = "Waist"), rec(18.0, "%").copy(id = 2, name = "Body fat"))
        val inches = BodyPart(defs, records, "kg", "cm").withUnits("kg", "in")
        assertEquals(35.43, inches.records[0].value, 0.01)
        assertEquals("in", inches.records[0].unit)
        assertEquals(31.50, inches.measurementDefs[0].goalValue, 0.01)
        assertEquals(18.0, inches.records[1].value, 0.0)
        assertEquals("%", inches.records[1].unit)
        // A value typed in inches goes back to centimetres exactly.
        assertEquals(90.0, MeasureUnits.convert(inches.records[0].value, "in", "cm"), 1e-9)
        assertEquals(false, MeasureUnits.sameKind("cm", "kg"))
    }

    @Test
    fun distanceUnitsAreLabelsOnly() {
        assertEquals("mi", DistanceUnits.of(" MI "))
        assertNull(DistanceUnits.of("furlong"))
        assertEquals("Metres", DistanceUnits.label(DistanceUnits.M))
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
