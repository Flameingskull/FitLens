package com.fitlens.companion.data

import kotlin.math.log10

/**
 * Body fat from body measurements (#153): the US Navy circumference method (Hodgdon & Beckett, 1984, Naval Health
 * Research Center reports 84-11 and 84-29), in its original metric body-density form, with the density converted to
 * body fat by Siri's equation (495 / density − 450). Checked against underwater weighing, it has a typical error of
 * about ±3.5 percentage points, the best of the tape-measure methods. Plain Kotlin, unit tested (BodyFatTest).
 *
 * - Men: density = 1.0324 − 0.19077 × log10(waist − neck) + 0.15456 × log10(height)
 * - Women: density = 1.29579 − 0.35004 × log10(waist + hips − neck) + 0.22100 × log10(height)
 *
 * All lengths in centimetres. The waist is measured at the navel for men and at its narrowest for women, the hips at
 * their widest, and the neck just below the larynx.
 */
object BodyFat {
    enum class Sex(val key: String, val label: String) {
        MALE("male", "Male"),
        FEMALE("female", "Female");

        companion object {
            fun of(key: String?): Sex? = entries.firstOrNull { it.key == key }
        }
    }

    /** A measurement the formula needs, with the names it may be logged under (matched ignoring capitals). */
    enum class Input(val label: String, val names: List<String>, val howTo: String) {
        HEIGHT("Height", listOf("height"), "standing, without shoes"),
        NECK("Neck", listOf("neck"), "just below the larynx (Adam's apple)"),
        WAIST("Waist", listOf("waist", "abdomen"), "at the navel for men, at the narrowest point for women"),
        HIPS("Hips", listOf("hips", "hip"), "at the widest point of the buttocks");

        fun matches(name: String): Boolean = name.trim().lowercase() in names
    }

    /** The default measurement every user has, alongside [Input.HEIGHT] (#153). */
    const val NAME = "Body fat"
    const val UNIT = "%"

    /** A measurement older than this, counted back from the day being logged, is flagged to re-measure. */
    const val STALE_DAYS = 14

    /** The method's typical error against underwater weighing, in percentage points. */
    const val TYPICAL_ERROR = 3.5

    /** Results outside this range mean a measurement was mistyped, not a real body fat. */
    const val MIN_PERCENT = 2.0
    const val MAX_PERCENT = 75.0

    /** Is [name] the body fat measurement, however it was named (FitNotes calls it "Body Fat")? */
    fun isBodyFat(name: String): Boolean =
        name.trim().lowercase().replace(" ", "") in setOf("bodyfat", "bodyfat%", "bodyfatpercentage")

    /** The measurements the formula needs for [sex], in the order they're asked for. */
    fun inputs(sex: Sex): List<Input> =
        if (sex == Sex.FEMALE) listOf(Input.HEIGHT, Input.NECK, Input.WAIST, Input.HIPS)
        else listOf(Input.HEIGHT, Input.NECK, Input.WAIST)

    sealed class Result {
        data class Ok(val percent: Double) : Result()
        /** Why the measurements can't give a result; [value] is the height or percentage it refers to. */
        data class Invalid(val problem: Problem, val value: Double = 0.0) : Result()
    }

    /** What's wrong with a set of measurements. The UI words each one (ui/BodyFatText.kt). */
    enum class Problem { NOT_POSITIVE, HEIGHT_RANGE, WAIST_NECK, HIPS_NEEDED, WAIST_HIPS_NECK, OUT_OF_RANGE
    }

    /** Body fat in percent from lengths in centimetres; [hipsCm] is needed for women only. */
    fun navy(sex: Sex, heightCm: Double, neckCm: Double, waistCm: Double, hipsCm: Double?): Result {
        if (heightCm <= 0 || neckCm <= 0 || waistCm <= 0) return Result.Invalid(Problem.NOT_POSITIVE)
        if (heightCm < 100 || heightCm > 250) {
            return Result.Invalid(Problem.HEIGHT_RANGE, heightCm)
        }
        val density = when (sex) {
            Sex.MALE -> {
                if (waistCm <= neckCm) return Result.Invalid(Problem.WAIST_NECK)
                1.0324 - 0.19077 * log10(waistCm - neckCm) + 0.15456 * log10(heightCm)
            }
            Sex.FEMALE -> {
                val hips = hipsCm ?: return Result.Invalid(Problem.HIPS_NEEDED)
                if (hips <= 0) return Result.Invalid(Problem.NOT_POSITIVE)
                if (waistCm + hips <= neckCm) return Result.Invalid(Problem.WAIST_HIPS_NECK)
                1.29579 - 0.35004 * log10(waistCm + hips - neckCm) + 0.22100 * log10(heightCm)
            }
        }
        val percent = 495.0 / density - 450.0
        if (percent < MIN_PERCENT || percent > MAX_PERCENT) {
            return Result.Invalid(Problem.OUT_OF_RANGE, percent)
        }
        return Result.Ok(percent)
    }
}
