package com.fitlens.companion.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * How long workout writes take on this phone (#60): the database transaction, then bringing the in-memory snapshot up
 * to date, which is what the day log waits for before it shows the change. Kept in memory only, the last [KEEP]
 * writes, and shown in Settings › About, so "saving a set stays as fast as history grows" can be checked on a real
 * dataset instead of guessed at.
 */
object WriteTimings {
    const val KEEP = 50

    /** A set saved, edited or deleted, the case that has its own fast refresh. */
    const val SET = 0
    /** Every other workout write (library, notes, copy or move a workout and so on). */
    const val OTHER = 1

    /** One write: [dbMs] for the transaction, [refreshMs] for the snapshot, [sets] the history size afterwards. */
    data class Timing(val kind: Int, val dbMs: Long, val refreshMs: Long, val sets: Int) {
        val totalMs: Long get() = dbMs + refreshMs
    }

    private val _recent = MutableStateFlow<List<Timing>>(emptyList())
    val recent: StateFlow<List<Timing>> = _recent

    /** Records a write that started at [startNs], finished its transaction at [dbDoneNs] and its refresh at [endNs]. */
    fun record(kind: Int, startNs: Long, dbDoneNs: Long, endNs: Long, sets: Int) {
        val t = Timing(kind, (dbDoneNs - startNs) / 1_000_000, (endNs - dbDoneNs) / 1_000_000, sets)
        _recent.update { keep(it + t) }
    }

    /** The newest [KEEP] timings. */
    internal fun keep(list: List<Timing>): List<Timing> = if (list.size <= KEEP) list else list.takeLast(KEEP)

    /** The median of [values], or null when there are none. An even count takes the lower middle value. */
    fun median(values: List<Long>): Long? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        return sorted[(sorted.size - 1) / 2]
    }
}
