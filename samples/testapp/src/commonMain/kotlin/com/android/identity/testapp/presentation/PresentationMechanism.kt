package com.android.identity.testapp.presentation

/**
 * Abstract interface to represent a mechanism used to connect a credential reader
 * with a credential provider.
 */
interface PresentationMechanism {

    /**
     * Closes down the connection and release all resources.
     */
    fun close()
}
