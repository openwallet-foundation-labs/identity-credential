package com.android.mdl.app.util

import androidx.lifecycle.SavedStateHandle

fun <T> SavedStateHandle.updateState(block: (T) -> T) {
    val prevValue = get<T>("state")!!
    val nextValue = block(prevValue)
    set("state", nextValue)
}

fun <T> SavedStateHandle.getState(initialState: T) = getStateFlow("state", initialState)