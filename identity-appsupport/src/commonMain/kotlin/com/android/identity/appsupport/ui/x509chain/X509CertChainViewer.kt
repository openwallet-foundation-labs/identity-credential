package com.android.identity.appsupport.ui.x509chain

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.identity.crypto.X509CertChain
import identitycredential.identity_appsupport.generated.resources.*
import org.jetbrains.compose.resources.stringResource

private val PAGER_INDICATOR_HEIGHT = 30.dp
private val PAGER_INDICATOR_PADDING = 8.dp

/**
 * Display Pager for the list of certs, with corresponding List of additional warnings and infos.
 * composables.
 *
 * @param infos list of strings with information messages.
 * If provided, the list must match the chain size and sequence with nulls where no info is needed.
 * @param warnings list of strings with WARNING messages.
 * If provided, the list must match the chain size and sequence with nulls where no warning is needed.
 */
@Composable
fun X509CertChainViewer(
    x509Chain: X509CertChain,
    infos: List<String>? = null,
    warnings: List<String>? = null
) {
    Box(
        modifier = Modifier.fillMaxHeight()
    ) {
        val pagerState = rememberPagerState(pageCount = { x509Chain.certificates.size })

        if (x509Chain.certificates.isEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    modifier = Modifier.padding(8.dp),
                    text = "No certificates in the chain.",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Column(
                modifier = Modifier.padding(bottom = PAGER_INDICATOR_HEIGHT + PAGER_INDICATOR_PADDING)
            ) {
                HorizontalPager(
                    state = pagerState,
                ) { page ->
                    Column {
                        val info = infos?.getOrNull(page)
                        val warning = warnings?.getOrNull(page)
                        X509ChainItem(
                            x509Chain.certificates[page],
                            info,
                            warning
                        )
                    }
                }
            }

            if (pagerState.pageCount > 1) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .wrapContentHeight()
                        .fillMaxWidth()
                        .height(PAGER_INDICATOR_HEIGHT)
                        .padding(PAGER_INDICATOR_PADDING),
                ) {
                    repeat(pagerState.pageCount) { iteration ->
                        val color =
                            if (pagerState.currentPage == iteration) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                                    .copy(alpha = .2f)
                            }
                        Box(
                            modifier = Modifier
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(color)
                                .size(8.dp)
                        )
                    }
                }
            }
        }
    }
}

