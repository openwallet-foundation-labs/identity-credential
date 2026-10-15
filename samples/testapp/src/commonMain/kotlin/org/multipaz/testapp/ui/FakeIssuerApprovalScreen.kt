package org.multipaz.testapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString
import org.multipaz.compose.decodeImage
import org.multipaz.facematch.FacePose

/**
 * Screen showing simulated credential provisioning and asking the user for approval
 * to send the verified captured portraits to the issuer.
 *
 * @param capturedImages map from [FacePose] to captured portrait image bytes.
 * @param onApprove invoked when the user approves sending the portraits to the issuer.
 * @param onCancel invoked when the user cancels or rejects sending the portraits.
 */
@Composable
fun FakeIssuerApprovalScreen(
    capturedImages: Map<FacePose, ByteString>,
    onApprove: () -> Unit,
    onCancel: () -> Unit
) {
    val entries = remember(capturedImages) { capturedImages.entries.toList() }
    val decodedBitmaps: List<Pair<FacePose, ImageBitmap?>> = remember(entries) {
        entries.map { (pose, bytes) ->
            val bmp = try {
                decodeImage(bytes.toByteArray())
            } catch (_: Throwable) {
                null
            }
            pose to bmp
        }
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var currentIndex by remember { mutableIntStateOf(0) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "Liveness Verified",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )
            }
        }

        item {
            if (decodedBitmaps.isNotEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LazyRow(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        itemsIndexed(decodedBitmaps) { index, (pose, imageBitmap) ->
                            val cornerRadius = 20.dp
                            val isSelected = index == currentIndex
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (imageBitmap != null) {
                                    Image(
                                        bitmap = imageBitmap,
                                        contentDescription = "Captured Portrait (${pose.displayLabel()})",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(width = 180.dp, height = 230.dp)
                                            .clip(RoundedCornerShape(cornerRadius))
                                            .border(
                                                width = if (isSelected) 3.dp else 1.dp,
                                                color = if (isSelected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.outlineVariant
                                                },
                                                shape = RoundedCornerShape(cornerRadius)
                                            )
                                    )
                                } else {
                                    Card(
                                        modifier = Modifier.size(width = 180.dp, height = 230.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = "Decode Failed",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "${pose.displayLabel()} (${index + 1}/${decodedBitmaps.size})",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    if (decodedBitmaps.size > 1) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    val target = (currentIndex - 1).coerceAtLeast(0)
                                    currentIndex = target
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(target)
                                    }
                                },
                                enabled = currentIndex > 0
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "Previous pose"
                                )
                            }
                            Text(
                                text = "${decodedBitmaps[currentIndex].first.displayLabel()} (${currentIndex + 1} of ${decodedBitmaps.size})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            IconButton(
                                onClick = {
                                    val target = (currentIndex + 1).coerceAtMost(decodedBitmaps.lastIndex)
                                    currentIndex = target
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(target)
                                    }
                                },
                                enabled = currentIndex < decodedBitmaps.lastIndex
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Next pose"
                                )
                            }
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier.size(width = 180.dp, height = 230.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "No Portrait Available",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Simulated Provisioning Request",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    IssuerDetailRow(label = "Issuer", value = "Utopia Department of Motor Vehicles")
                    IssuerDetailRow(label = "Credential", value = "Mobile Driving Licence (mDL)")
                    IssuerDetailRow(
                        label = "Captured Poses",
                        value = entries.joinToString(", ") { it.key.displayLabel() }.ifEmpty { "None" }
                    )
                    val totalSizeKb = entries.sumOf { it.value.size } / 1024
                    if (entries.isNotEmpty()) {
                        IssuerDetailRow(label = "Total Payload Size", value = "$totalSizeKb KB (${entries.size} frame(s))")
                    }
                }
            }
        }

        item {
            Text(
                text = "Simulating provisioning. This is what a wallet app could send to the issuer after user approval.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onApprove,
                    enabled = capturedImages.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Approve and Send to Issuer")
                }

                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun IssuerDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
