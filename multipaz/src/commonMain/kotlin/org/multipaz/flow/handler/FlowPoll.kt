package org.multipaz.flow.handler

import org.multipaz.cbor.DataItem
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/** An interface to represent a long poll. */
interface FlowPoll {
    /**
     * Waits for an event/notification on one of the specified flows. If the wait exceeds
     * certain implementation-specific duration [TimeoutException] is thrown. Otherwise the
     * index of the receiving flow and notification payload is returned. In addition,
     * consumeToken passed to the next [poll] call serves as an indication that the
     * notification was processed by the caller and should not be returned again.
     */
    suspend fun poll(consumeToken: String, flows: List<PollKey>): PollResult

    data class PollKey(val flowName: String, val opaqueState: DataItem)

    data class PollResult(
        val consumeToken: String,
        val index: Int,
        val notification: DataItem
    )

    class TimeoutException : Exception()

    companion object {
        val SILENT = object : FlowPoll {
            override suspend fun poll(consumeToken: String, flows: List<PollKey>): PollResult {
                delay(100.milliseconds)
                throw TimeoutException()
            }
        }
    }
}