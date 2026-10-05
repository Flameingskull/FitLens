package com.fitlens.companion.ui

import android.content.res.Resources
import com.fitlens.companion.R
import com.fitlens.companion.data.BodyFat
import com.fitlens.companion.data.fmtNum

/*
 * What the body fat calculator's data reads as on screen (#94). BodyFat keeps its English labels, which name the
 * measurements it creates and matches; the words shown come from strings.xml here.
 */

internal fun sexText(res: Resources, sex: BodyFat.Sex): String = res.getString(
    when (sex) {
        BodyFat.Sex.MALE -> R.string.bfx_male
        BodyFat.Sex.FEMALE -> R.string.bfx_female
    }
)

/** "Height", "Neck", "Waist", "Hips". */
internal fun inputText(res: Resources, input: BodyFat.Input): String = res.getString(
    when (input) {
        BodyFat.Input.HEIGHT -> R.string.bfx_height
        BodyFat.Input.NECK -> R.string.bfx_neck
        BodyFat.Input.WAIST -> R.string.bfx_waist
        BodyFat.Input.HIPS -> R.string.bfx_hips
    }
)

/** Where to measure: "just below the larynx (Adam's apple)". */
internal fun howToText(res: Resources, input: BodyFat.Input): String = res.getString(
    when (input) {
        BodyFat.Input.HEIGHT -> R.string.bfx_height_how
        BodyFat.Input.NECK -> R.string.bfx_neck_how
        BodyFat.Input.WAIST -> R.string.bfx_waist_how
        BodyFat.Input.HIPS -> R.string.bfx_hips_how
    }
)

/** Why the measurements can't give a body fat, in a sentence. */
internal fun invalidText(res: Resources, invalid: BodyFat.Result.Invalid): String = when (invalid.problem) {
    BodyFat.Problem.NOT_POSITIVE -> res.getString(R.string.bfx_not_positive)
    BodyFat.Problem.HEIGHT_RANGE -> res.getString(R.string.bfx_height_range, fmtNum(invalid.value, 1))
    BodyFat.Problem.WAIST_NECK -> res.getString(R.string.bfx_waist_neck)
    BodyFat.Problem.HIPS_NEEDED -> res.getString(R.string.bfx_hips_needed)
    BodyFat.Problem.WAIST_HIPS_NECK -> res.getString(R.string.bfx_waist_hips_neck)
    BodyFat.Problem.OUT_OF_RANGE -> res.getString(
        R.string.bfx_out_of_range, fmtNum(invalid.value, 1), fmtNum(BodyFat.MIN_PERCENT, 0), fmtNum(BodyFat.MAX_PERCENT, 0)
    )
}
