package com.android.identity.wallet.util

import com.android.identity.util.Timestamp

fun Int.toTimestampFromNow(): Timestamp {
    val now = Timestamp.now().toEpochMilli()
    val validityDuration = this * 24 * 60 * 60 * 1000L
    return Timestamp.ofEpochMilli(now + validityDuration)
}
