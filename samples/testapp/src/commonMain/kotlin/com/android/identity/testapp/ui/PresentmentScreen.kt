package org.multipaz.testapp.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import org.multipaz.models.ui.presentment.PresentmentModel
import org.multipaz.testapp.App
import org.multipaz.testapp.TestAppPresentmentSource
import multipazproject.samples.testapp.generated.resources.Res
import multipazproject.samples.testapp.generated.resources.app_icon
import multipazproject.samples.testapp.generated.resources.app_name
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.multipaz.compose.presentment.Presentment

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
        appName = stringResource(Res.string.app_name),
        appIconPainter = painterResource(Res.drawable.app_icon),
    )
}