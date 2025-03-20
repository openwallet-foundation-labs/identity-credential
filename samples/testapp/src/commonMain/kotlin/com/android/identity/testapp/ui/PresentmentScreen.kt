package org.multipaz.testapp.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import org.multipaz.models.presentment.PresentmentModel
import org.multipaz.testapp.App
import org.multipaz.testapp.TestAppPresentmentSource
import org.jetbrains.compose.resources.painterResource
import org.multipaz.compose.presentment.Presentment
import org.multipaz.testapp.platformAppIcon
import org.multipaz.testapp.platformAppName

private const val TAG = "PresentmentScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresentmentScreen(
    app: App,
    presentmentModel: PresentmentModel,
    onPresentationComplete: () -> Unit,
) {
    Presentment(
        presentmentModel = presentmentModel,
        promptModel = app.promptModel,
        documentTypeRepository = app.documentTypeRepository,
        source = TestAppPresentmentSource(app),
        onPresentmentComplete = onPresentationComplete,
        appName = platformAppName,
        appIconPainter = painterResource(platformAppIcon),
    )
}