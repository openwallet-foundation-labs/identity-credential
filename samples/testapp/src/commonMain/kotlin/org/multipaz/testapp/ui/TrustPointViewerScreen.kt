package org.multipaz.testapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.multipaz.compose.certificateviewer.X509CertViewer
import org.multipaz.compose.datetime.formattedDate
import org.multipaz.testapp.App
import org.multipaz.trustmanagement.TrustManagerLocal
import org.multipaz.trustmanagement.TrustManager
import org.multipaz.trustmanagement.TrustPoint
import org.multipaz.trustmanagement.VicalTrustManager
import org.multipaz.util.toHex

@OptIn(ExperimentalResourceApi::class)
@Composable
fun TrustPointViewerScreen(
    app: App,
    trustManager: TrustManager,
    trustPointId: String,
    showToast: (message: String) -> Unit,
) {
    val trustPoint = remember { mutableStateOf<TrustPoint?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(true) {
        coroutineScope.launch {
            trustPoint.value = trustManager.getTrustPoints().first {
                it.certificate.subjectKeyIdentifier!!.toHex() == trustPointId
            }
        }
    }

    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier.padding(8.dp).verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        trustPoint.value?.let {
            it.metadata.displayIcon?.let {
                val bitmap = remember { it.toByteArray().decodeToImageBitmap() }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        modifier = Modifier.size(160.dp).padding(16.dp),
                        bitmap = bitmap,
                        contentDescription = null,
                        tint = Color.Unspecified,
                    )
                }
            }
            Text(text = buildAnnotatedString {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append("Display Name: ")
                }
                append()
                append(it.metadata.displayName ?: "(not set)")
            })
            Text(text = buildAnnotatedString {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append("Privacy Policy URL: ")
                }
                append()
                append(it.metadata.privacyPolicyUrl ?: "(not set)")
            })

            when (trustManager) {
                is VicalTrustManager -> {
                    val vical = trustManager.signedVical.vical
                    Text(text = buildAnnotatedString {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("Source: ")
                        }
                        append("VICAL from ")
                        append(vical.vicalProvider)
                        append(" at ")
                        append(formattedDate(vical.date))
                    })
                }

                is TrustManagerLocal -> {
                    Text(text = buildAnnotatedString {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("Source: ")
                        }
                        append((trustManager as TrustManagerLocal).identifier)
                    })
                }
            }

            Text(text = buildAnnotatedString {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append("X.509 Certificate:")
                }
            })
            X509CertViewer(certificate = it.certificate)
        }
    }
}