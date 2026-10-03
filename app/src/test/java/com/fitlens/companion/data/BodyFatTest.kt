package com.fitlens.companion.data

import com.fitlens.companion.data.BodyFat.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.log10

/** Body fat (#153): the US Navy formula must give the published method's values exactly. */
class BodyFatTest {

    private fun ok(r: BodyFat.Result): Double = (r as BodyFat.Result.Ok).percent

    @Test
    fun manMatchesTheMetricDensityForm() {
        // 70 in tall, neck 15 in, waist 34 in, in centimetres.
        val p = ok(BodyFat.navy(Sex.MALE, 177.8, 38.1, 86.36, null))
        assertEquals(17.4347, p, 1e-3)
        // The Navy's published inch formula agrees to within a few tenths.
        val inches = 86.010 * log10(34.0 - 15.0) - 70.041 * log10(70.0) + 36.76
        assertEquals(inches, p, 0.3)
    }

    @Test
    fun womanMatchesTheMetricDensityForm() {
        // 65 in tall, neck 12.5 in, waist 28 in, hips 38 in, in centimetres.
        val p = ok(BodyFat.navy(Sex.FEMALE, 165.1, 31.75, 71.12, 96.52))
        assertEquals(26.3228, p, 1e-3)
        val inches = 163.205 * log10(28.0 + 38.0 - 12.5) - 97.684 * log10(65.0) - 78.387
        assertEquals(inches, p, 0.3)
    }

    @Test
    fun womenNeedHips() {
        assertTrue(BodyFat.navy(Sex.FEMALE, 165.0, 32.0, 70.0, null) is BodyFat.Result.Invalid)
        assertEquals(listOf("Height", "Neck", "Waist", "Hips"), BodyFat.inputs(Sex.FEMALE).map { it.label })
        assertEquals(listOf("Height", "Neck", "Waist"), BodyFat.inputs(Sex.MALE).map { it.label })
    }

    @Test
    fun impossibleInputsAreRefused() {
        assertTrue(BodyFat.navy(Sex.MALE, 180.0, 40.0, 38.0, null) is BodyFat.Result.Invalid)
        // Height typed in inches but read as centimetres.
        assertTrue(BodyFat.navy(Sex.MALE, 70.0, 38.0, 86.0, null) is BodyFat.Result.Invalid)
        // A waist barely larger than the neck gives an impossible result.
        assertTrue(BodyFat.navy(Sex.MALE, 180.0, 40.0, 41.0, null) is BodyFat.Result.Invalid)
    }

    @Test
    fun recognisesBodyFatAndInputNames() {
        assertTrue(BodyFat.isBodyFat("Body Fat"))
        assertTrue(BodyFat.isBodyFat("body fat %"))
        assertFalse(BodyFat.isBodyFat("Bodyweight"))
        assertTrue(BodyFat.Input.HIPS.matches(" Hip "))
        assertTrue(BodyFat.Input.WAIST.matches("WAIST"))
    }
}
