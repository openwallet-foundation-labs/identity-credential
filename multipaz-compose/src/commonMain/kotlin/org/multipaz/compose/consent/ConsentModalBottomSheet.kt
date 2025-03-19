package org.multipaz.compose.consent

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import org.multipaz.compose.getOutlinedImageVector
import org.multipaz.request.MdocRequestedClaim
import org.multipaz.request.Request
import org.multipaz.request.RequestedClaim
import org.multipaz.trustmanagement.TrustPoint
import multipazproject.multipaz_compose.generated.resources.Res
import multipazproject.multipaz_compose.generated.resources.consent_modal_bottom_sheet_button_cancel
import multipazproject.multipaz_compose.generated.resources.consent_modal_bottom_sheet_button_more
import multipazproject.multipaz_compose.generated.resources.consent_modal_bottom_sheet_button_share
import multipazproject.multipaz_compose.generated.resources.consent_modal_bottom_sheet_card_art_description
import multipazproject.multipaz_compose.generated.resources.consent_modal_bottom_sheet_data_element_icon_description
import multipazproject.multipaz_compose.generated.resources.consent_modal_bottom_sheet_headline_share_with_known_requester
import multipazproject.multipaz_compose.generated.resources.consent_modal_bottom_sheet_headline_share_with_unknown_requester
import multipazproject.multipaz_compose.generated.resources.consent_modal_bottom_sheet_share_and_stored_by_known_requester
import multipazproject.multipaz_compose.generated.resources.consent_modal_bottom_sheet_share_and_stored_by_unknown_requester
import multipazproject.multipaz_compose.generated.resources.consent_modal_bottom_sheet_share_with_known_requester
import multipazproject.multipaz_compose.generated.resources.consent_modal_bottom_sheet_share_with_unknown_requester
import multipazproject.multipaz_compose.generated.resources.consent_modal_bottom_sheet_verifier_icon_description
import multipazproject.multipaz_compose.generated.resources.consent_modal_bottom_sheet_wallet_privacy_policy
import multipazproject.multipaz_compose.generated.resources.consent_modal_bottom_sheet_warning_icon_description
import multipazproject.multipaz_compose.generated.resources.consent_modal_bottom_sheet_warning_verifier_not_in_trust_list
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource
import kotlin.math.min

/**
 * A [ModalBottomSheet] used for obtaining the user's consent when presenting credentials.
 *
 * @param sheetState a [SheetState] for state.
 * @param request the request.
 * @param documentName the name of the document.
 * @param documentDescription a description of the document.
 * @param documentCardArt cart art for the document.
 * @param trustPoint if the requester is in a trust-list, the [TrustPoint] indicating this
 * @param onConfirm called when the sheet is dismissed.
 * @param onCancel called when the user presses the "Share" button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsentModalBottomSheet(
    sheetState: SheetState,
    request: Request,
    documentName: String,
    documentDescription: String,
    documentCardArt: ImageBitmap,
    trustPoint: TrustPoint?,
    onConfirm: () -> Unit = {},
    onCancel: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = { onCancel() },
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            RelyingPartySection(
                request = request,
                trustPoint = trustPoint
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
                    trustPoint = trustPoint
                )
            }

            ButtonSection(scope, sheetState, onConfirm, onCancel, scrollState)
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
            Text(text = stringResource(Res.string.consent_modal_bottom_sheet_button_cancel))
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
    trustPoint: TrustPoint?
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (trustPoint != null) {
            if (trustPoint.displayIcon != null) {
                val rpBitmap = remember {
                    trustPoint.displayIcon!!.decodeToImageBitmap()
                }
                Icon(
                    modifier = Modifier.size(80.dp).padding(bottom = 16.dp),
                    bitmap = rpBitmap,
                    contentDescription = stringResource(Res.string.consent_modal_bottom_sheet_verifier_icon_description),
                    tint = Color.Unspecified
                )
            }
            if (trustPoint.displayName != null) {
                Text(
                    text = stringResource(
                        Res.string.consent_modal_bottom_sheet_headline_share_with_known_requester,
                        trustPoint.displayName!!
                    ),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else if (request.requester.websiteOrigin != null) {
            Text(
                text = stringResource(
                    Res.string.consent_modal_bottom_sheet_headline_share_with_known_requester,
                    request.requester.websiteOrigin!!
                ),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        } else {
            Text(
                text = stringResource(Res.string.consent_modal_bottom_sheet_headline_share_with_unknown_requester),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun DocumentSection(
    documentName: String,
    documentDescription: String,
    documentCardArt: ImageBitmap,
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
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(
                    modifier = Modifier.size(50.dp),
                    bitmap = documentCardArt,
                    contentDescription = stringResource(Res.string.consent_modal_bottom_sheet_card_art_description),
                    tint = Color.Unspecified
                )
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
) {
    val useColumns = request.requestedClaims.size > 5
    val (storedFields, notStoredFields) = request.requestedClaims.partition {
        it is MdocRequestedClaim && it.intentToRetain == true
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            if (notStoredFields.size > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        text = if (trustPoint?.displayName != null) {
                            stringResource(
                                Res.string.consent_modal_bottom_sheet_share_with_known_requester,
                                trustPoint.displayName!!
                            )
                        } else if (request.requester.websiteOrigin != null) {
                            stringResource(
                                Res.string.consent_modal_bottom_sheet_share_with_known_requester,
                                request.requester.websiteOrigin!!
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
                        text = if (trustPoint?.displayName != null) {
                            stringResource(
                                Res.string.consent_modal_bottom_sheet_share_and_stored_by_known_requester,
                                trustPoint.displayName!!
                            )
                        } else if (request.requester.websiteOrigin != null) {
                            stringResource(
                                Res.string.consent_modal_bottom_sheet_share_and_stored_by_known_requester,
                                request.requester.websiteOrigin!!
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
    }
    Spacer(modifier = Modifier.height(2.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(0.dp, 0.dp, 16.dp, 16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // TODO: When we upgrade to a newer version of compose-ui we can use
            //  AnnotatedString.fromHtml() and clicking the links will also work.
            //  See https://issuetracker.google.com/issues/139326648 for details.
            //
            val annotatedLinkString = buildAnnotatedString {
                val str = stringResource(Res.string.consent_modal_bottom_sheet_wallet_privacy_policy)
                val startIndex = 4
                val endIndex = startIndex + 31
                append(str)
                addStyle(
                    style = SpanStyle(
                        color = Color(0xff64B5F6),
                        textDecoration = TextDecoration.Underline
                    ), start = startIndex, end = endIndex
                )
            }
            Text(
                text = annotatedLinkString,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
    if (trustPoint == null) {
        Box(
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            WarningCard(
                stringResource(Res.string.consent_modal_bottom_sheet_warning_verifier_not_in_trust_list)
            )
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

@Composable
private fun WarningCard(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.errorContainer),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(
                modifier = Modifier.padding(end = 16.dp),
                imageVector = Icons.Outlined.Warning,
                contentDescription = stringResource(Res.string.consent_modal_bottom_sheet_warning_icon_description),
                tint = MaterialTheme.colorScheme.onErrorContainer
            )

            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}
