package org.multipaz.testapp.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.multipaz.documenttype.knowntypes.DrivingLicense

enum class VerifierType(
    val description: String
) {

    KNOWN_VERIFIER_WITH_POLICY_PROXIMITY("Known Verifier with policy (Proximity)"),
    KNOWN_VERIFIER_PROXIMITY("Known Verifier (Proximity)"),
    UNKNOWN_VERIFIER_PROXIMITY("Unknown Verifier (Proximity)"),
    ANONYMOUS_VERIFIER_PROXIMITY("Anonymous Verifier (Proximity)"),

    KNOWN_VERIFIER_WITH_POLICY_WEBSITE("Known Verifier with policy (Website)"),
    KNOWN_VERIFIER_WEBSITE("Known Verifier (Website)"),
    UNKNOWN_VERIFIER_WEBSITE("Unknown Verifier (Website)"),
    ANONYMOUS_VERIFIER_WEBSITE("Anonymous Verifier (Website)"),

    KNOWN_VERIFIER_WITH_POLICY_APP("Known Verifier with policy (App)"),
    KNOWN_VERIFIER_APP("Known Verifier (App)"),
    UNKNOWN_VERIFIER_APP("Unknown Verifier (App)"),
    ANONYMOUS_VERIFIER_APP("Anonymous Verifier (App)"),
}

@Composable
fun ConsentModalBottomSheetListScreen(
    onConsentModalBottomSheetClicked: (mdlSampleRequest: String, verifierType: VerifierType) -> Unit,
) {
    val mdlRequests = remember {
        val documentType = DrivingLicense.getDocumentType()
        documentType.cannedRequests
    }

    LazyColumn(
        modifier = Modifier.padding(8.dp)
    ) {

        for (verifierType in VerifierType.values()) {
            item {
                Text(
                    text = verifierType.description,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            for (request in mdlRequests) {
                item {
                    TextButton(onClick = { onConsentModalBottomSheetClicked(request.id, verifierType) }) {
                        Text("${request.displayName}")
                    }
                }
            }
        }
    }
}
