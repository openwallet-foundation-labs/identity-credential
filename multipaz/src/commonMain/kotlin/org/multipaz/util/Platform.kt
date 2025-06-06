@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.multipaz.util

import org.multipaz.securearea.SecureArea
import org.multipaz.storage.Storage

expect object Platform {
    val name: String

    suspend fun getBackedUpStorage(): Storage

    suspend fun getNonBackedUpStorage(): Storage

    suspend fun getSecureArea(storage: Storage): SecureArea
}
