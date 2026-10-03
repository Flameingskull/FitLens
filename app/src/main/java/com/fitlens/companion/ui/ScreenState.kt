package com.fitlens.companion.ui

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 * A screen's own state (#37): which sheet or dialog is open, filters, a selection. It belongs to the screen's entry
 * in the back stack, so it outlives rotation, and it's kept in that entry's saved state, so it comes back after
 * Android stops FitLens in the background. Values must fit in a Bundle (Boolean, Int, Long, String and lists of them).
 *
 * A screen gets its own with `viewModel<DayState>()` and swaps `remember { mutableStateOf(x) }` for `state.saved("key", x)`.
 */
open class ScreenState(private val handle: SavedStateHandle) : ViewModel() {
    private val values = HashMap<String, MutableState<*>>()
    private val lists = HashMap<String, SnapshotStateList<Long>>()

    /** A value kept under [key], starting at [initial]. */
    @Suppress("UNCHECKED_CAST")
    fun <T> saved(key: String, initial: T): MutableState<T> =
        values.getOrPut(key) { SavedValue(handle, key, handle.get<T>(key) ?: initial) } as MutableState<T>

    /** A list of ids kept under [key], such as a selection. */
    fun savedIds(key: String): SnapshotStateList<Long> = lists.getOrPut(key) {
        val list = mutableStateListOf<Long>().apply { handle.get<LongArray>(key)?.let { addAll(it.toList()) } }
        viewModelScope.launch { snapshotFlow { list.toLongArray() }.collect { handle[key] = it } }
        list
    }
}

private class SavedValue<T>(private val handle: SavedStateHandle, private val key: String, initial: T) : MutableState<T> {
    private val state = mutableStateOf(initial)
    override var value: T
        get() = state.value
        set(v) {
            state.value = v
            handle[key] = v
        }
    override fun component1(): T = value
    override fun component2(): (T) -> Unit = { value = it }
}

/** The day log's open sheets and dialogs. */
class DayState(handle: SavedStateHandle) : ScreenState(handle)

/** The photo gallery's filters, grouping and selection. */
class PhotosState(handle: SavedStateHandle) : ScreenState(handle)

/** The photo viewer's open dialogs. */
class PhotoViewerState(handle: SavedStateHandle) : ScreenState(handle)
