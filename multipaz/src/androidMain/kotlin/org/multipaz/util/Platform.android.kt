package org.multipaz.util

import android.os.Build
import org.multipaz.context.applicationContext
import org.multipaz.prompt.AndroidPromptModel
import org.multipaz.prompt.PromptModel
import org.multipaz.securearea.AndroidKeystoreSecureArea
import org.multipaz.securearea.SecureArea
import org.multipaz.storage.Storage
import org.multipaz.storage.android.AndroidStorage
import org.multipaz.storage.ephemeral.EphemeralStorage
import java.io.File

actual object Platform {
    actual val name = "Android ${Build.VERSION.SDK_INT}"

    actual val promptModel: PromptModel
        get() = AndroidPromptModel()

    actual fun getStorage(): Storage {
        return AndroidStorage(
            File(applicationContext.dataDir.path, "storage.db").absolutePath
        )
    }

    actual fun getNonBackedUpStorage(): Storage {
        return AndroidStorage(
            File(applicationContext.noBackupFilesDir.path, "storage.db").absolutePath
        )
    }

    actual suspend fun getSecureArea(): SecureArea {
        return AndroidKeystoreSecureArea.create(getNonBackedUpStorage())
    }
}