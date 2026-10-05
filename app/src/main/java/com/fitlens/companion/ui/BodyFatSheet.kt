package com.fitlens.companion.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.fitlens.companion.R
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.fitlens.companion.data.BodyFat
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.LengthUnits
import com.fitlens.companion.data.MRecord
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Store
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.SectionLabel
import com.fitlens.companion.ui.design.SegmentedSwitch
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Calculates body fat from the body measurements already logged (#153), with the US Navy formula ([BodyFat.navy]).
 * Each input shows its latest value on or before [date], with its date. A missing one is named and must be entered
 * here before anything is calculated; one older than [BodyFat.STALE_DAYS] days is flagged to re-measure. Values typed
 * here are saved as measurements on [date] when the result is used. [onUse] gets the result and a comment that
 * records how it was worked out.
 */
@Composable
fun BodyFatCalculatorSheet(snap: Snapshot, date: String, onDismiss: () -> Unit, onUse: (percent: Double, comment: String) -> Unit) {
    val prefs by Settings.portable.collectAsState()
    val res = LocalContext.current.resources
    val sex = BodyFat.Sex.of(prefs.profileSex)
    val typed = remember { mutableStateMapOf<BodyFat.Input, String>() }
    val day = Dates.epochDay(date)

    /** One input: the measurement it's logged under, its latest value by [date], and the unit to type a new one in. */
    class InputState(val input: BodyFat.Input, val name: String, val latest: MRecord?, val unit: String) {
        val ageDays: Long? get() = latest?.let { day - Dates.epochDay(it.date) }
        val stale: Boolean get() = (ageDays ?: 0) > BodyFat.STALE_DAYS
        val typedValue: Double? get() = typed[input]?.trim()?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it > 0 }
        /** What the formula uses, in centimetres: a value typed here, or the latest logged one. */
        val cm: Double? get() = typedValue?.let { LengthUnits.convert(it, unit, LengthUnits.CM) }
            ?: latest?.let { LengthUnits.convert(it.value, it.unit, LengthUnits.CM) }
        /** The value used, as it reads to the user. */
        val shown: String? get() = typedValue?.let { "${fmtNum(it, 1)} $unit" } ?: latest?.let { "${fmtNum(it.value, 1)} ${it.unit}".trim() }
    }

    val rows = (sex?.let { BodyFat.inputs(it) } ?: emptyList()).map { input ->
        val name = snap.recordsByName.keys.firstOrNull { input.matches(it) }
            ?: snap.allMeasurements.firstOrNull { input.matches(it.name) }?.name
            ?: input.label
        val latest = snap.recordsByName[name].orEmpty().lastOrNull { it.date.take(10) <= date }
        // New values are typed in the unit the measurement is shown in, or the length unit.
        val unit = snap.allMeasurements.firstOrNull { it.name == name }?.unit?.takeIf { LengthUnits.of(it) != null }
            ?: latest?.unit?.takeIf { LengthUnits.of(it) != null } ?: prefs.lengthUnit
        InputState(input, name, latest, unit)
    }
    val missing = rows.filter { it.cm == null }
    val result = when {
        sex == null || rows.isEmpty() -> null
        missing.isNotEmpty() -> null
        else -> BodyFat.navy(
            sex,
            heightCm = rows.first { it.input == BodyFat.Input.HEIGHT }.cm!!,
            neckCm = rows.first { it.input == BodyFat.Input.NECK }.cm!!,
            waistCm = rows.first { it.input == BodyFat.Input.WAIST }.cm!!,
            hipsCm = rows.firstOrNull { it.input == BodyFat.Input.HIPS }?.cm
        )
    }
    val percent = (result as? BodyFat.Result.Ok)?.percent

    fun use() {
        val p = percent ?: return
        val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        // Values typed here are logged too, so the calculation can always be traced back.
        val toSave = rows.mapNotNull { r -> r.typedValue?.let { Triple(r.name, r.unit, it) } }
        AppScope.scope.launch { toSave.forEach { (n, u, v) -> Store.addManualRecord(n, u, date, time, v, null) } }
        val comment = res.getString(
            R.string.bf_comment, sex!!.label.lowercase(), rows.joinToString(", ") { "${it.input.label.lowercase()} ${it.shown}" }
        )
        onUse(Math.round(p * 10) / 10.0, comment)
        onDismiss()
    }

    FitSheet(
        title = stringResource(R.string.bf_title),
        onDismiss = onDismiss,
        confirmLabel = percent?.let { stringResource(R.string.bf_use_value, fmtNum(it, 1)) } ?: stringResource(R.string.bf_use),
        confirmEnabled = percent != null,
        onConfirm = { use() }
    ) {
        Text(
            stringResource(R.string.bf_intro, BodyFat.METHOD, fmtNum(BodyFat.TYPICAL_ERROR, 1)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SectionLabel(stringResource(R.string.bf_sex))
        SegmentedSwitch(
            options = BodyFat.Sex.entries.map { it.label },
            selected = sex?.ordinal ?: -1,
            onSelect = { i -> Settings.updatePortable { it.copy(profileSex = BodyFat.Sex.entries[i].key) } }
        )
        if (sex == null) {
            Text(
                stringResource(R.string.bf_choose_sex),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            SectionLabel(stringResource(R.string.body_measurements))
            rows.forEach { r -> InputRow(r.input, r.latest, r.ageDays, r.stale, r.unit, typed[r.input].orEmpty()) { typed[r.input] = it } }
            when {
                missing.isNotEmpty() -> Text(
                    pluralStringResource(
                        R.plurals.bf_missing, missing.size, missing.joinToString(", ") { it.input.label.lowercase() }, Dates.medium(date)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                result is BodyFat.Result.Invalid -> Text(
                    result.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                percent != null -> Text(
                    stringResource(R.string.bf_result, fmtNum(percent, 1)),
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        }
    }
}

/** One of the formula's inputs: its latest value and age, where to measure it, and a field for a new value. */
@Composable
private fun InputRow(
    input: BodyFat.Input,
    latest: MRecord?,
    ageDays: Long?,
    stale: Boolean,
    unit: String,
    text: String,
    onText: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        val value = latest?.let { "${fmtNum(it.value, 1)} ${it.unit}".trim() }
        val status = when {
            latest == null -> stringResource(R.string.bf_not_logged)
            ageDays == 0L -> stringResource(R.string.bf_today, value.orEmpty())
            else -> pluralStringResource(R.plurals.bf_days_ago, (ageDays ?: 0L).toInt(), value.orEmpty(), Dates.medium(latest.date), (ageDays ?: 0L).toInt())
        }
        Text(stringResource(R.string.an_label_value, input.label, status), style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(R.string.bf_measure, input.howTo) + if (stale) " " + stringResource(R.string.bf_stale, BodyFat.STALE_DAYS) else "",
            style = MaterialTheme.typography.bodySmall,
            color = if (stale) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = text,
            onValueChange = onText,
            label = {
                Text(
                    if (latest == null) stringResource(R.string.bf_field_needed, input.label, unit)
                    else stringResource(R.string.bf_field_new, input.label.lowercase(), unit)
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
