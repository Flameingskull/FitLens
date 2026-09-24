@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.fitlens.companion.ui.design

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.fitlens.companion.data.Dates
import com.fitlens.companion.ui.Brand
import com.fitlens.companion.ui.GoldHairline
import com.fitlens.companion.ui.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.launch

/**
 * Letter-spaced uppercase tabs with a gold indicator, over a gold hairline (#80).
 * Five or more tabs scroll horizontally by default ([scrollable]).
 */
@Composable
fun FitTabRow(
    titles: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = titles.size >= 5
) {
    val tabs: @Composable () -> Unit = {
        titles.forEachIndexed { i, t ->
            Tab(
                selected = i == selected,
                onClick = { onSelect(i) },
                text = {
                    Text(t.uppercase(), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                selectedContentColor = Brand.Gold,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    val index = selected.coerceIn(0, (titles.size - 1).coerceAtLeast(0))
    Column(modifier) {
        if (scrollable) {
            ScrollableTabRow(
                selectedTabIndex = index,
                containerColor = Brand.Black,
                contentColor = Brand.Gold,
                edgePadding = Spacing.sm,
                divider = {},
                tabs = tabs
            )
        } else {
            TabRow(
                selectedTabIndex = index,
                containerColor = Brand.Black,
                contentColor = Brand.Gold,
                divider = {},
                tabs = tabs
            )
        }
        GoldHairline()
    }
}

/**
 * A [FitTabRow] with a swipeable pager underneath (#80). Tapping a tab animates to its page; swiping a page moves
 * the tab. [onPageChange] reports the settled page, for screens that remember it.
 */
@Composable
fun FitTabPager(
    titles: List<String>,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
    onPageChange: (Int) -> Unit = {},
    page: @Composable (Int) -> Unit
) {
    val state = rememberPagerState(initialPage = initialPage) { titles.size }
    val scope = rememberCoroutineScope()
    val report by rememberUpdatedState(onPageChange)
    LaunchedEffect(state) {
        snapshotFlow { state.settledPage }.collect { report(it) }
    }
    Column(modifier) {
        FitTabRow(titles, selected = state.currentPage, onSelect = { i -> scope.launch { state.animateScrollToPage(i) } })
        HorizontalPager(state = state, modifier = Modifier.fillMaxSize()) { i -> page(i) }
    }
}

/**
 * Material 3 segmented buttons for switching between two to four views of the same thing, for example
 * Exercises / Analysis (#80).
 */
@Composable
fun SegmentedSwitch(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { i, label ->
            SegmentedButton(
                selected = i == selected,
                onClick = { onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = Brand.PurpleDeep,
                    activeContentColor = Brand.GoldLight,
                    activeBorderColor = Brand.Gold,
                    inactiveContainerColor = Brand.Black,
                    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    inactiveBorderColor = MaterialTheme.colorScheme.outline
                ),
                label = {
                    Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            )
        }
    }
}

/** The preset windows offered by [RangeChips]. [months] is null for all time. */
enum class RangePreset(val label: String, val months: Long?) {
    OneMonth("1M", 1),
    ThreeMonths("3M", 3),
    SixMonths("6M", 6),
    OneYear("1Y", 12),
    All("All", null);

    /** The first ISO date inside this window, or null for all time. */
    fun startDate(today: LocalDate = LocalDate.now()): String? = months?.let { today.minusMonths(it).format(Dates.ISO) }
}

/**
 * 1M / 3M / 6M / 1Y / All chips plus "Custom", which opens a date-range picker (#80).
 *
 * Exactly one chip is selected: the [selected] preset, or Custom when [selected] is null and [custom] holds the
 * chosen ISO dates (from, to). The chips scroll sideways rather than wrap at large font sizes.
 */
@Composable
fun RangeChips(
    selected: RangePreset?,
    onPreset: (RangePreset) -> Unit,
    custom: Pair<String, String>?,
    onCustom: (from: String, to: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var picking by remember { mutableStateOf(false) }
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        RangePreset.values().forEach { p ->
            FilterChip(selected = p == selected, onClick = { onPreset(p) }, label = { Text(p.label) })
        }
        val customLabel = if (custom != null && selected == null) {
            "${Dates.short(custom.first)} – ${Dates.short(custom.second)}"
        } else {
            "Custom"
        }
        FilterChip(selected = selected == null && custom != null, onClick = { picking = true }, label = { Text(customLabel) })
    }
    if (picking) {
        DateRangePickerDialog(
            initialFrom = custom?.first,
            initialTo = custom?.second,
            onDismiss = { picking = false },
            onPicked = { from, to -> onCustom(from, to) }
        )
    }
}

private fun isoToMillis(iso: String?): Long? =
    Dates.parse(iso)?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli()

private fun millisToIso(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().format(Dates.ISO)

/** A Material date-range picker in a dialog, returning ISO dates. A single tapped day is a one-day range. */
@Composable
fun DateRangePickerDialog(
    initialFrom: String?,
    initialTo: String?,
    onDismiss: () -> Unit,
    onPicked: (from: String, to: String) -> Unit
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = isoToMillis(initialFrom),
        initialSelectedEndDateMillis = isoToMillis(initialTo)
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    val start = state.selectedStartDateMillis
                    if (start != null) {
                        val end = state.selectedEndDateMillis ?: start
                        onPicked(millisToIso(start), millisToIso(end))
                    }
                    onDismiss()
                }
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DateRangePicker(state = state, modifier = Modifier.weight(1f))
    }
}
