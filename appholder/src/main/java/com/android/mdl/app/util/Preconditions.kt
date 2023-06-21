@file:OptIn(ExperimentalContracts::class)

package com.android.mdl.app.util

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

inline fun <T : Any> requireValidProperty(value: T?, lazyMessage: () -> Any): T {
    contract {
        returns() implies (value != null)
    }

    if (value == null) {
        val message = lazyMessage()
        throw IllegalStateException(message.toString())
    } else {
        return value
    }
}