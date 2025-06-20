package org.multipaz.testapp

import androidx.compose.runtime.Composable
import org.multipaz.compose.digitalcredentials.CredentialManagerPresentmentActivity
import org.multipaz.testapp.ui.AppTheme

class TestAppCredentialManagerPresentmentActivity: CredentialManagerPresentmentActivity() {
    @Composable
    override fun ApplicationTheme(content: @Composable (() -> Unit)) {
        AppTheme(content)
    }

    override suspend fun getSettings(): Settings {
        val app = App.Companion.getInstance()
        app.init()

        val stream = assets.open("privilegedUserAgents.json")
        val data = ByteArray(stream.available())
        stream.read(data)
        stream.close()
        val privilegedAllowList = data.decodeToString()

        return Settings(
            appName = platformAppName,
            appIcon = platformAppIcon,
            promptModel = app.promptModel,
            documentTypeRepository = app.documentTypeRepository,
            presentmentSource = app.getPresentmentSource(),
            privilegedAllowList = privilegedAllowList,
        )
    }
}
