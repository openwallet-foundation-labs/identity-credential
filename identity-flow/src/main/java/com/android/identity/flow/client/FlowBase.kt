package com.android.identity.flow.client

import com.android.identity.cbor.DataItem

/**
 * Base class for all flow interfaces. All flow interfaces should derive from
 * this interface and should be marked with
 * [com.android.identity.flow.annotation.FlowInterface] annotation
 */
interface FlowBase {
    /** Flow path. Only needed in generated code, do not use. */
    val flowPath: String

    /** Opaque flow data accessor. Only needed in generated code, do not use. */
    val flowState: DataItem

    /**
     * Completes this flow, notifies its parent flow (if any), and destroys
     * this flow state. Flow should not be used after this call.
     */
    suspend fun complete()
}