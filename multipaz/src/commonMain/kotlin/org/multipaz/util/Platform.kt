@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.multipaz.util

import org.multipaz.securearea.SecureArea
import org.multipaz.storage.Storage

/**
 * Object for selecting platform specific functionality.
 */
expect object Platform {
    /**
     * The name and version of the platform.
     *
     * @throws NotImplementedError if called on a platform which isn't Android or iOS.
     */
    val name: String

    /**
     * Gets a [Storage] instance suitable for the platform.
     *
     * @throws NotImplementedError if called on a platform which isn't Android or iOS.
     */
    suspend fun getStorage(): Storage

    /**
     * Gets a [Storage] instance suitable for the platform in a location where the
     * underlying data file is excluded from backups.
     *
     * @throws NotImplementedError if called on a platform which isn't Android or iOS.
     */
    suspend fun getNonBackedUpStorage(): Storage

    /**
     * Gets a [SecureArea] implementation suitable for the platform.
     *
     * @throws NotImplementedError if called on a platform which isn't Android or iOS.
     */
    suspend fun getSecureArea(storage: Storage): SecureArea
}
