package org.multipaz.rpc.backend

/**
 * Simple interface to access configuration parameters.
 */
interface Configuration {
    fun getValue(key: String): String?
}