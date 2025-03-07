package org.multipaz.mrtd

/**
 * A class that provides a namespace for [Status] and its subclasses (which act a bit like an enum).
 */
sealed class MrtdNfc {
    sealed class Status {
        override fun toString(): String = javaClass.simpleName
    }
    object Initial : Status()
    object Connected : Status()
    object AttemptingPACE : Status()
    object PACESucceeded : Status()
    object PACENotSupported : Status()
    object PACEFailed : Status()
    object AttemptingBAC : Status()
    object BACSucceeded : Status()
    data class ReadingData(val progressPercent: Int) : Status()
    data class TunnelAuthenticating(val progressPercent: Int) : Status()
    data class TunnelReading(val progressPercent: Int) : Status()
    object Finished : Status()
}