package org.multipaz.util

import org.multipaz.prompt.PromptModel
import org.multipaz.securearea.SecureArea
import org.multipaz.storage.Storage

actual object Platform {
    actual val name = "JVM ${System.getProperty("java.vm.version")} / Java ${System.getProperty("java.version")}"

    actual val promptModel: PromptModel
        get() = throw NotImplementedError()

    actual fun getStorage(): Storage {
        throw NotImplementedError()
    }

    actual fun getNonBackedUpStorage(): Storage {
        throw NotImplementedError()
    }

    actual suspend fun getSecureArea(): SecureArea {
        throw NotImplementedError()
    }
}