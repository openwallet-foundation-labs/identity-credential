package org.multipaz.util

import org.multipaz.securearea.SecureArea
import org.multipaz.storage.Storage

actual object Platform {
    actual val name = "JVM ${System.getProperty("java.vm.version")} / Java ${System.getProperty("java.version")}"

    actual suspend fun getStorage(): Storage {
        throw NotImplementedError()
    }

    actual suspend fun getNonBackedUpStorage(): Storage {
        throw NotImplementedError()
    }

    actual suspend fun getSecureArea(storage: Storage): SecureArea {
        throw NotImplementedError()
    }
}