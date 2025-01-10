package com.android.identity.storage

import com.android.identity.storage.ephemeral.EphemeralStorage
import kotlinx.datetime.Clock

actual fun createTransientStorageList(testClock: Clock): List<Storage> {
    return listOf(
        EphemeralStorage(testClock)
    )
}

actual fun createPersistentStorage(name: String, testClock: Clock): Storage? {
    return null
}