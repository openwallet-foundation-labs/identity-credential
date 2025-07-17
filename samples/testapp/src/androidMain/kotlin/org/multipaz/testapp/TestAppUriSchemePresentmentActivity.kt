package org.multipaz.testapp

import androidx.compose.runtime.Composable
import org.multipaz.compose.presentment.UriSchemePresentmentActivity
import org.multipaz.testapp.ui.AppTheme

class TestAppUriSchemePresentmentActivity: UriSchemePresentmentActivity() {
    @Composable
    override fun ApplicationTheme(content: @Composable (() -> Unit)) {
        AppTheme(content)
    }

    override suspend fun getSettings(): Settings {
        val app = App.Companion.getInstance()
        app.init()
        return Settings(
            appName = platformAppName,
            appIcon = platformAppIcon,
            promptModel = app.promptModel,
            documentTypeRepository = app.documentTypeRepository,
            presentmentSource = app.getPresentmentSource(),
            httpClientEngineFactory = platformHttpClientEngineFactory()
        )
    }
}
