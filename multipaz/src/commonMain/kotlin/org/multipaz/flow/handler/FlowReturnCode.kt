package org.multipaz.flow.handler

/** Constants to determine the result kind in Cbor serialization of the method call response. */
enum class FlowReturnCode {
    RESULT,  // followed by DataItem representing result (either flow or serializable)
    EXCEPTION  // Followed by exceptionId, then DataItem representing exception
}