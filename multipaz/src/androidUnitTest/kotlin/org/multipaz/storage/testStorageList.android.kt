package org.multipaz.storage

import org.multipaz.storage.ephemeral.EphemeralStorage
import kotlinx.datetime.Clock

actual fun createTransientStorageList(testClock: Clock): List<Storage> {
    return listOf(
        EphemeralStorage(testClock)
    )
}

actual fun createPersistentStorage(name: String, testClock: Clock): Storage? {
    return null
}