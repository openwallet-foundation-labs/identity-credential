package org.multipaz.compose.consent

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource
import org.multipaz.compose.ApplicationInfo
import org.multipaz.compose.cards.WarningCard
import org.multipaz.compose.certificateviewer.X509CertViewer
import org.multipaz.compose.getApplicationInfo
import org.multipaz.compose.getOutlinedImageVector
import org.multipaz.crypto.X509Cert
import org.multipaz.multipaz_compose.generated.resources.Res
import org.multipaz.multipaz_compose.generated.resources.consent_modal_bottom_sheet_button_back
import org.multipaz.multipaz_compose.generated.resources.consent_modal_bottom_sheet_button_cancel
import org.multipaz.multipaz_compose.generated.resources.consent_modal_bottom_sheet_button_more
import org.multipaz.multipaz_compose.generated.resources.consent_modal_bottom_sheet_button_share
import org.multipaz.multipaz_compose.generated.resources.consent_modal_bottom_sheet_card_art_description
import org.multipaz.multipaz_compose.generated.resources.consent_modal_bottom_sheet_data_element_icon_description
import org.multipaz.multipaz_compose.generated.resources.consent_modal_bottom_sheet_headline_share_with_anonymous_requester
import org.multipaz.multipaz_compose.generated.resources.consent_modal_bottom_sheet_headline_share_with_known_requester
import org.multipaz.multipaz_compose.generated.resources.consent_modal_bottom_sheet_headline_share_with_unknown_requester
import org.multipaz.multipaz_compose.generated.resources.consent_modal_bottom_sheet_info_verifier_in_trust_list
import org.multipaz.multipaz_compose.generated.resources.consent_modal_bottom_sheet_info_verifier_in_trust_list_app
import org.multipaz.multipaz_compose.generated.resources.consent_modal_bottom_sheet_info_verifier_in_trust_list_website
import org.multipaz.multipaz_compose.generated.resources.consent_modal_bottom_sheet_privacy_policy
import org.multipaz.multipaz_compose.generated.resources.consent_modal_bottom_sheet_share_and_stored_by_known_requester
import org.multipaz.multipaz_compose.generated.resources.consent_modal_bottom_sheet_share_and_stored_by_unknown_requester
import org.multipaz.multipaz_compose.generated.resources.consent_modal_bottom_sheet_share_with_known_requester
import org.multipaz.multipaz_compose.generated.resources.consent_modal_bottom_sheet_share_with_unknown_requester
import org.multipaz.multipaz_compose.generated.resources.consent_modal_bottom_sheet_verifier_icon_description
import org.multipaz.multipaz_compose.generated.resources.consent_modal_bottom_sheet_warning_verifier_not_in_trust_list
import org.multipaz.multipaz_compose.generated.resources.consent_modal_bottom_sheet_warning_verifier_not_in_trust_list_anonymous
import org.multipaz.multipaz_compose.generated.resources.consent_modal_bottom_sheet_warning_verifier_not_in_trust_list_app
import org.multipaz.multipaz_compose.generated.resources.consent_modal_bottom_sheet_warning_verifier_not_in_trust_list_website
import org.multipaz.request.MdocRequestedClaim
import org.multipaz.request.Request
import org.multipaz.request.RequestedClaim
import org.multipaz.trustmanagement.TrustPoint
import org.multipaz.util.Logger
import kotlin.math.min

private const val TAG = "ConsentModalBottomSheet"

/**
 * A [ModalBottomSheet] used for obtaining the user's consent when presenting credentials.
 *
 * @param sheetState a [SheetState] for state.
 * @param request the request.
 * @param documentName the name of the document.
 * @param documentDescription a description of the document.
 * @param documentCardArt cart art for the document or `null`.
 * @param trustPoint if the requester is in a trust-list, the [TrustPoint] indicating this
 * @param appName the name of the application or `null` to not show the name.
 * @param appIconPainter the icon for the application or `null to not show the icon.
 * @param onConfirm called when the sheet is dismissed.
 * @param onCancel called when the user presses the "Share" button.
 * @param showCancelAsBack if `true`, the cancel button will say "Back" instead of "Cancel".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsentModalBottomSheet(
    sheetState: SheetState,
    request: Request,
    documentName: String,
    documentDescription: String,
    documentCardArt: ImageBitmap?,
    trustPoint: TrustPoint?,
    appName: String? = null,
    appIconPainter: Painter? = null,
    onConfirm: () -> Unit = {},
    onCancel: () -> Unit = {},
    showCancelAsBack: Boolean = false
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val navController = rememberNavController()
    val appInfo = remember {
        request.requester.appId?.let {
            try {
                getApplicationInfo(it)
            } catch (e: Throwable) {
                Logger.w(TAG, "Error looking up information for appId $it")
                null
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { onCancel() },
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        NavHost(navController = navController, startDestination = "main") {
            composable("main") {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 0.dp)
                ) {
                    if (appName != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            if (appIconPainter != null) {
                                Image(
                                    modifier = Modifier.size(20.dp),
                                    painter = appIconPainter,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                )
                            }
                            Text(
                                text = appName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }

                    RelyingPartySection(
                        request = request,
                        trustPoint = trustPoint,
                        appInfo = appInfo,
                        onShowCertChain = {
                            navController.navigate("readerCertChain")
                        }
                    )

                    DocumentSection(documentName, documentDescription, documentCardArt)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusGroup()
                            .verticalScroll(scrollState)
                            .weight(0.9f, false)
                    ) {
                        RequestSection(
                            request = request,
                            trustPoint = trustPoint,
                            appInfo = appInfo,
                            onViewCertificateClicked = {
                                navController.navigate("readerCertChain")
                            }
                        )
                    }

                    ButtonSection(scope, sheetState, onConfirm, onCancel, showCancelAsBack, scrollState)
                }
            }

            composable("readerCertChain") {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxHeight(0.8f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        contentAlignment = Alignment.CenterStart
                    ) {
                        IconButton(onClick = { navController.navigate("main") }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null
                            )
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Reader Certificate Chain",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        CertificateViewer(certificates = request.requester.certChain!!.certificates)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ButtonSection(
    scope: CoroutineScope,
    sheetState: SheetState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    showCancelAsBack: Boolean,
    scrollState: ScrollState
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        TextButton(onClick = {
            scope.launch {
                sheetState.hide()
                onCancel()
            }
        }) {
            Text(
                text = if (showCancelAsBack) {
                    stringResource(Res.string.consent_modal_bottom_sheet_button_back)
                } else {
                    stringResource(Res.string.consent_modal_bottom_sheet_button_cancel)
                }
            )
        }

        Button(
            onClick = {
                if (!scrollState.canScrollForward) {
                    onConfirm()
                } else {
                    scope.launch {
                        val step = (scrollState.viewportSize * 0.9).toInt()
                        scrollState.animateScrollTo(
                            min(
                                scrollState.value + step,
                                scrollState.maxValue
                            )
                        )
                    }
                }
            }
        ) {
            if (scrollState.canScrollForward) {
                Text(text = stringResource(Res.string.consent_modal_bottom_sheet_button_more))
            } else {
                Text(text = stringResource(Res.string.consent_modal_bottom_sheet_button_share))
            }
        }
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun RelyingPartySection(
    request: Request,
    trustPoint: TrustPoint?,
    appInfo: ApplicationInfo?,
    onShowCertChain: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val (requesterName, requesterBitmap) = if (trustPoint != null) {
            Pair(
                trustPoint.metadata.displayName,
                trustPoint.metadata.displayIcon?.let { remember { it.toByteArray().decodeToImageBitmap() } }
            )
        } else if (request.requester.websiteOrigin != null) {
            Pair(
                request.requester.websiteOrigin!!,
                null
            )
        } else if (appInfo != null) {
            Pair(
                appInfo.name,
                appInfo.icon
            )
        } else {
            Pair(null, null)
        }

        val headlineText = if (requesterName != null) {
            stringResource(
                Res.string.consent_modal_bottom_sheet_headline_share_with_known_requester,
                requesterName
            )
        } else {
            if (trustPoint != null && request.requester.certChain != null) {
                // If we have a trust point without `displayName` use the name in the root certificate.
                stringResource(
                    Res.string.consent_modal_bottom_sheet_headline_share_with_known_requester,
                    trustPoint.certificate.subject.name
                )
            } else {
                if (request.requester.certChain != null) {
                    stringResource(Res.string.consent_modal_bottom_sheet_headline_share_with_unknown_requester)
                } else {
                    stringResource(Res.string.consent_modal_bottom_sheet_headline_share_with_anonymous_requester)
                }
            }
        }

        if (requesterBitmap != null) {
            Icon(
                modifier = Modifier.size(80.dp).padding(bottom = 16.dp)
                    .clickable(enabled = request.requester.certChain != null) { onShowCertChain() },
                bitmap = requesterBitmap,
                contentDescription = stringResource(Res.string.consent_modal_bottom_sheet_verifier_icon_description),
                tint = Color.Unspecified,
            )
        }
        Text(
            modifier = Modifier
                .clickable(enabled = request.requester.certChain != null) { onShowCertChain() },
            text = headlineText,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun DocumentSection(
    documentName: String,
    documentDescription: String,
    documentCardArt: ImageBitmap?,
) {
    Column(
        modifier = Modifier
            .padding(vertical = 2.dp)
            .fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape = RoundedCornerShape(16.dp, 16.dp, 0.dp, 0.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
            horizontalAlignment = Alignment.Start,
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (documentCardArt != null) {
                    Icon(
                        modifier = Modifier.size(50.dp),
                        bitmap = documentCardArt,
                        contentDescription = stringResource(Res.string.consent_modal_bottom_sheet_card_art_description),
                        tint = Color.Unspecified
                    )
                }
                Column(
                    modifier = Modifier.padding(start = 16.dp)
                ) {
                    Text(
                        text = documentName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = documentDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun RequestSection(
    request: Request,
    trustPoint: TrustPoint?,
    appInfo: ApplicationInfo?,
    onViewCertificateClicked: () -> Unit,
) {
    val useColumns = request.requestedClaims.size > 5
    val (storedFields, notStoredFields) = request.requestedClaims.partition {
        it is MdocRequestedClaim && it.intentToRetain == true
    }

    val sections = mutableListOf<@Composable () -> Unit>()

    // See if we should show a privacy policy link
    var privacyPolicyText: String? = if (trustPoint?.metadata?.privacyPolicyUrl != null) {
        stringResource(
            Res.string.consent_modal_bottom_sheet_privacy_policy,
            trustPoint.metadata.displayName ?: "",
            trustPoint.metadata.privacyPolicyUrl!!,
        )
    } else {
        null
    }

    sections.add() {
        if (notStoredFields.size > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = if (trustPoint?.metadata?.displayName != null) {
                        stringResource(
                            Res.string.consent_modal_bottom_sheet_share_with_known_requester,
                            trustPoint.metadata.displayName!!
                        )
                    } else if (request.requester.websiteOrigin != null) {
                        stringResource(
                            Res.string.consent_modal_bottom_sheet_share_with_known_requester,
                            request.requester.websiteOrigin!!
                        )
                    } else if (appInfo != null) {
                        stringResource(
                            Res.string.consent_modal_bottom_sheet_share_with_known_requester,
                            appInfo.name
                        )
                    } else {
                        stringResource(Res.string.consent_modal_bottom_sheet_share_with_unknown_requester)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            DataElementGridView(notStoredFields, useColumns)
        }
        if (storedFields.size > 0) {
            if (notStoredFields.size > 0) {
                HorizontalDivider(modifier = Modifier.padding(8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = if (trustPoint?.metadata?.displayName != null) {
                        stringResource(
                            Res.string.consent_modal_bottom_sheet_share_and_stored_by_known_requester,
                            trustPoint.metadata.displayName!!
                        )
                    } else if (request.requester.websiteOrigin != null) {
                        stringResource(
                            Res.string.consent_modal_bottom_sheet_share_and_stored_by_known_requester,
                            request.requester.websiteOrigin!!
                        )
                    } else if (appInfo != null) {
                        stringResource(
                            Res.string.consent_modal_bottom_sheet_share_and_stored_by_known_requester,
                            appInfo.name
                        )
                    } else {
                        stringResource(Res.string.consent_modal_bottom_sheet_share_and_stored_by_unknown_requester)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            DataElementGridView(storedFields, useColumns)
        }
    }

    if (trustPoint != null) {
        var text = if (request.requester.websiteOrigin != null) {
            stringResource(Res.string.consent_modal_bottom_sheet_info_verifier_in_trust_list_website)
        } else if (request.requester.appId != null) {
            stringResource(Res.string.consent_modal_bottom_sheet_info_verifier_in_trust_list_app)
        } else {
            stringResource(Res.string.consent_modal_bottom_sheet_info_verifier_in_trust_list)
        }
        if (privacyPolicyText != null) {
            text = "$text $privacyPolicyText"
        }
        sections.add {
            Text(
                text = AnnotatedString.fromMarkdown(markdownString = text),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    } else if (privacyPolicyText != null) {
        sections.add {
            Text(
                text = AnnotatedString.fromMarkdown(markdownString = privacyPolicyText),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }


    for (n in sections.indices) {
        val section = sections[n]
        val isLast = (n == sections.size - 1)
        val rounded = 16.dp
        val endRound = if (isLast) rounded else 0.dp
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape = RoundedCornerShape(0.dp, 0.dp, endRound, endRound))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            section()
        }
        if (!isLast) {
            Spacer(modifier = Modifier.height(2.dp))
        }
    }

    Box(
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        if (trustPoint == null) {
            val text = if (request.requester.websiteOrigin != null) {
                stringResource(Res.string.consent_modal_bottom_sheet_warning_verifier_not_in_trust_list_website)
            } else if (request.requester.appId != null) {
                stringResource(Res.string.consent_modal_bottom_sheet_warning_verifier_not_in_trust_list_app)
            } else {
                if (request.requester.certChain != null) {
                    stringResource(Res.string.consent_modal_bottom_sheet_warning_verifier_not_in_trust_list)
                } else {
                    stringResource(Res.string.consent_modal_bottom_sheet_warning_verifier_not_in_trust_list_anonymous)
                }
            }
            WarningCard {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun DataElementGridView(
    claims: List<RequestedClaim>,
    useColumns: Boolean
) {
    if (!useColumns) {
        for (claim in claims) {
            Row(modifier = Modifier.fillMaxWidth()) {
                DataElementView(claim = claim, modifier = Modifier.weight(1.0f))
            }
        }
    } else {
        var n = 0
        while (n <= claims.size - 2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DataElementView(claim = claims[n], modifier = Modifier.weight(1.0f))
                DataElementView(
                    claim = claims[n + 1],
                    modifier = Modifier.weight(1.0f)
                )
            }
            n += 2
        }
        if (n < claims.size) {
            Row(modifier = Modifier.fillMaxWidth()) {
                DataElementView(claim = claims[n], modifier = Modifier.weight(1.0f))
            }
        }
    }
}

/**
 * Individual view for a DataElement.
 */
@Composable
private fun DataElementView(
    modifier: Modifier,
    claim: RequestedClaim,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = modifier.padding(8.dp),
    ) {
        if (claim.attribute?.icon != null) {
            Icon(
                claim.attribute!!.icon!!.getOutlinedImageVector(),
                contentDescription = stringResource(Res.string.consent_modal_bottom_sheet_data_element_icon_description)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = claim.displayName,
            fontWeight = FontWeight.Normal,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

// This only supports links for now, would be nice to have a full support...
//
private fun AnnotatedString.Companion.fromMarkdown(
    markdownString: String,
    linkInteractionListener: LinkInteractionListener? = null
): AnnotatedString {
    val linkRegex = """\[(.*?)\]\((.*?)\)""".toRegex()

    val links = linkRegex.findAll(markdownString).toMutableList()
    links.sortBy { it.range.start }

    return buildAnnotatedString {
        var idx = 0
        for (link in links) {
            if (idx < link.range.start) {
                append(markdownString.substring(idx, link.range.start))
            }
            val linkText = link.groupValues[1]
            val linkUrl = link.groupValues[2]
            val styleStart = length
            append(linkText)
            addLink(
                url = LinkAnnotation.Url(
                    url = linkUrl,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = Color.Blue,
                            textDecoration = TextDecoration.Underline
                        ),
                    ),
                    linkInteractionListener = linkInteractionListener
                ),
                start = styleStart,
                end = length,
            )
            idx = link.range.endInclusive + 1
        }
        if (idx < markdownString.length) {
            append(markdownString.substring(idx, markdownString.length))
        }
    }
}

private val PAGER_INDICATOR_HEIGHT = 30.dp
private val PAGER_INDICATOR_PADDING = 8.dp

@Composable
private fun CertificateViewer(
    modifier: Modifier = Modifier,
    certificates: List<X509Cert>
) {
    check(certificates.isNotEmpty())
    Box(
        modifier = modifier.fillMaxHeight().padding(start = 16.dp)
    ) {
        val listSize = certificates.size
        val pagerState = rememberPagerState(pageCount = { listSize })

        Column(
            modifier = Modifier.then(
                if (listSize > 1)
                    Modifier.padding(bottom = PAGER_INDICATOR_HEIGHT + PAGER_INDICATOR_PADDING)
                else // No pager, no padding.
                    Modifier
            )
        ) {
            HorizontalPager(
                state = pagerState,
            ) { page ->
                val scrollState = rememberScrollState()
                X509CertViewer(
                    modifier = Modifier.verticalScroll(scrollState),
                    certificate = certificates[page]
                )
            }
        }

        if (listSize > 1) { // Don't show pager for single cert on the list.
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

