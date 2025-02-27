package com.android.identity.testapp.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import com.android.identity.appsupport.ui.presentment.PresentmentModel
import com.android.identity.testapp.App
import com.android.identity.testapp.TestAppPresentmentSource
import identitycredential.samples.testapp.generated.resources.Res
import identitycredential.samples.testapp.generated.resources.app_icon
import identitycredential.samples.testapp.generated.resources.app_name
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