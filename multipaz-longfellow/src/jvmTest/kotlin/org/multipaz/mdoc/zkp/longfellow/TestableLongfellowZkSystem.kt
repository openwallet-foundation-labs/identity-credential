package org.multipaz.mdoc.zkp.longfellow

import kotlinx.datetime.Instant

class TestableLongfellowZkSystem() : LongfellowZkSystem() {
    var testTime: Instant? = null
    override fun getCurrentTimestamp() = testTime ?: super.getCurrentTimestamp()
}