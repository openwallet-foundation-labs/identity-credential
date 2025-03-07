package org.multipaz.storage

import kotlinx.datetime.Clock

/**
 * Creates a list of empty transient [Storage] objects for testing.
 */
expect fun createTransientStorageList(testClock: Clock): List<Storage>

/**
 * Creates a persistent [Storage] object for testing if supported by this platform.
 *
 * Passing the same name will connect [Storage] to the same storage area. First time a
 * particular name is used (during process lifetime), the storage area will be cleared.
 */
expect fun createPersistentStorage(name: String, testClock: Clock): Storage?