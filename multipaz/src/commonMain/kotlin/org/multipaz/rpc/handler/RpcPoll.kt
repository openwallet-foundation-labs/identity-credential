package org.multipaz.rpc.handler

import org.multipaz.cbor.DataItem
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/** An interface to represent a long poll. */
interface RpcPoll {
    /**
     * Waits for an event/notification on one of the specified poll keys. If the wait exceeds
     * certain implementation-specific duration [TimeoutException] is thrown. Otherwise the
     * index of the receiving poll and notification payload is returned. In addition,
     * consumeToken passed to the next [poll] call serves as an indication that the
     * notification was processed by the caller and should not be returned again.
     */
    suspend fun poll(consumeToken: String, pollKeys: List<PollKey>): PollResult

    data class PollKey(val target: String, val opaqueState: DataItem)

    data class PollResult(
        val consumeToken: String,
        val index: Int,
        val notification: DataItem
    )

    class TimeoutException : Exception()

    companion object {
        val SILENT = object : RpcPoll {
            override suspend fun poll(consumeToken: String, pollKeys: List<PollKey>): PollResult {
                delay(1.seconds)
                throw TimeoutException()
            }
        }
    }
}