package com.android.identity.flow.server

/**
 * Simple interface to access configuration parameters.
 */
interface Configuration {
    fun getValue(key: String): String?
}