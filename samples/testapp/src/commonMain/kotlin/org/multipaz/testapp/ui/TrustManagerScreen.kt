package org.multipaz.testapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.multipaz.compose.datetime.formattedDate
import org.multipaz.trustmanagement.LocalTrustManager
import org.multipaz.trustmanagement.TrustManager
import org.multipaz.trustmanagement.TrustPoint
import org.multipaz.trustmanagement.VicalTrustManager

@Composable
fun TrustManagerScreen(
    trustManager: TrustManager,
    onViewTrustPoint: (trustPoint: TrustPoint) -> Unit,
    showToast: (message: String) -> Unit,
) {
    val trustPoints = remember { mutableStateOf<List<TrustPoint>?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(true) {
        coroutineScope.launch {
            trustPoints.value = trustManager.getTrustPoints()
        }
    }

    LazyColumn(
        modifier = Modifier.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        trustPoints.value?.let {
            val trustManagers = it.map { it.trustManager }.toSet()
            for (trustManager in trustManagers) {
                item {
                    when (trustManager) {
                        is VicalTrustManager -> {
                            Text(text = buildAnnotatedString {
                                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append("VICAL")
                                }
                                append(" by ")
                                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append(trustManager.signedVical.vical.vicalProvider)
                                }
                                append(" at ")
                                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append(formattedDate(trustManager.signedVical.vical.date))
                                }
                            })
                        }
                        is LocalTrustManager -> {
                            Text(text = buildAnnotatedString {
                                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append("Local Storage (${trustManager.identifier})")
                                }
                            })
                        }
                    }
                }
                it.filter {it.trustManager == trustManager}.forEach { trustPoint ->
                    item {
                        TextButton(
                            onClick = { onViewTrustPoint(trustPoint) }
                        ) {
                            Text(text = trustPoint.metadata.displayName ?: trustPoint.identifier)
                        }
                    }
                }
            }
        }

    }
}