package com.android.identity.testapp.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import com.android.identity.appsupport.ui.consent.ConsentDocument
import com.android.identity.appsupport.ui.consent.ConsentRelyingParty
import com.android.identity.appsupport.ui.consent.MdocConsentField
import com.android.identity.testapp.presentation.Presentation
import com.android.identity.testapp.presentation.PresentationModel
import com.android.identity.testapp.TestAppUtils
import kotlinx.coroutines.CancellableContinuation

private const val TAG = "PresentationScreen"

private data class ConsentSheetData(
    val showConsentPrompt: Boolean,
    val continuation:  CancellableContinuation<Boolean>,
    val document: ConsentDocument,
    val consentFields: List<MdocConsentField>,
    val relyingParty: ConsentRelyingParty,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresentationScreen(
    presentationModel: PresentationModel,
    allowMultipleRequests: Boolean,
    onPresentationComplete: () -> Unit,
    showToast: (message: String) -> Unit
) {
    Presentation(
        documentStore = TestAppUtils.documentStore,
        documentTypeRepository = TestAppUtils.documentTypeRepository,
        readerTrustManager = TestAppUtils.readerTrustManager,
        allowMultipleRequests = allowMultipleRequests,
        showToast = showToast,
        onPresentationComplete = onPresentationComplete
    )
}