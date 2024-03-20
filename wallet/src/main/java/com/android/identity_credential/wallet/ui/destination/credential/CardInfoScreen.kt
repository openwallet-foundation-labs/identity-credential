package com.android.identity_credential.wallet.ui.destination.credential

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.identity.util.Logger
import com.android.identity_credential.wallet.CardViewModel
import com.android.identity_credential.wallet.R
import com.android.identity_credential.wallet.SettingsModel
import com.android.identity_credential.wallet.navigation.WalletDestination
import com.android.identity_credential.wallet.ui.KeyValuePairText
import com.android.identity_credential.wallet.ui.ScreenWithAppBarAndBackButton
import com.android.identity_credential.wallet.ui.durationFromNowText
import java.util.Locale


private const val TAG = "CardInfoScreen"

@Composable
fun CardInfoScreen(
    cardId: String,
    cardViewModel: CardViewModel,
    settingsModel: SettingsModel,
    onNavigate: (String) -> Unit,
) {
    val card = cardViewModel.getCard(cardId)
    if (card == null) {
        Logger.w(TAG, "No card with id $cardId")
        onNavigate(WalletDestination.Main.route)
        return
    }


    var showRequestUpdateDialog by remember { mutableStateOf(false) }
    if (showRequestUpdateDialog) {
        val remoteDeletionCheckedState = remember { mutableStateOf(false) }
        val notifyApplicationCheckedState = remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showRequestUpdateDialog = false },
            title = { Text(text = stringResource(R.string.card_info_screen_request_update_title)) },
            text = {
                Column() {
                    Text(stringResource(R.string.card_info_screen_request_update_message))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = remoteDeletionCheckedState.value, onCheckedChange = {
                            remoteDeletionCheckedState.value = it
                        })
                        Text(stringResource(R.string.card_info_screen_request_update_remote_deletion_checkbox_string))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = notifyApplicationCheckedState.value, onCheckedChange = {
                            notifyApplicationCheckedState.value = it
                        })
                        Text(stringResource(R.string.card_info_screen_request_update_notify_application_checkbox_string))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRequestUpdateDialog = false
                        cardViewModel.developerModeRequestUpdate(
                            card,
                            remoteDeletionCheckedState.value,
                            notifyApplicationCheckedState.value)
                    }) {
                    Text(stringResource(R.string.card_info_screen_request_update_confirm_button))
                }
            },
            dismissButton = {
                Button(
                    onClick = { showRequestUpdateDialog = false }) {
                    Text(stringResource(R.string.card_info_screen_request_update_dismiss_button))
                }
            }
        )
    }

    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }
    if (showDeleteConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmationDialog = false },
            title = { Text(text = stringResource(R.string.card_info_screen_confirm_deletion_title)) },
            text = {
                Text(stringResource(R.string.card_info_screen_confirm_deletion_message))
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmationDialog = false
                        cardViewModel.deleteCard(card)
                        onNavigate(WalletDestination.PopBackStack.route)
                    }) {
                    Text(stringResource(R.string.card_info_screen_confirm_deletion_confirm_button))
                }
            },
            dismissButton = {
                Button(
                    onClick = { showDeleteConfirmationDialog = false }) {
                    Text(stringResource(R.string.card_info_screen_confirm_deletion_dismiss_button))
                }
            }
        )
    }

    var showMenu by remember { mutableStateOf(false) }
    ScreenWithAppBarAndBackButton(
        title = stringResource(R.string.card_info_screen_title),
        onBackButtonClick = { onNavigate(WalletDestination.PopBackStack.route) },
        actions = {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.Menu, contentDescription = null)
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.card_info_screen_menu_item_check_for_update)) },
                    leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                    onClick = {
                        cardViewModel.refreshCard(card)
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.card_info_screen_menu_item_show_data)) },
                    leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                    onClick = {
                        onNavigate(WalletDestination.CardInfo.getRouteWithArguments(
                            listOf(
                                Pair(WalletDestination.CardInfo.Argument.CARD_ID, card.id),
                                Pair(WalletDestination.CardInfo.Argument.SECTION, "details")
                            )
                        ))
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.card_info_screen_menu_item_delete)) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        showDeleteConfirmationDialog = true
                    }
                )
                if (settingsModel.developerModeEnabled.value == true) {
                    Divider()
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.card_info_screen_menu_item_show_keys)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.experiment_icon),
                                contentDescription = null
                            )
                        },
                        onClick = {
                            onNavigate(
                                WalletDestination.CardInfo.getRouteWithArguments(
                                    listOf(
                                        Pair(WalletDestination.CardInfo.Argument.CARD_ID, card.id),
                                        Pair(WalletDestination.CardInfo.Argument.SECTION, "keys")
                                    )
                                )
                            )
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.card_info_screen_menu_item_request_update)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.experiment_icon),
                                contentDescription = null
                            )
                        },
                        onClick = {
                            showMenu = false
                            showRequestUpdateDialog = true
                        }
                    )
                }
            }
        }
    ) {

        Column(Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Image(
                    bitmap = card.cardArtwork.asImageBitmap(),
                    contentDescription = stringResource(R.string.accessibility_artwork_for, card.name),
                    modifier = Modifier.height(200.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = card.typeName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(8.dp)
                )
            }
            Spacer(modifier = Modifier.weight(0.5f))
            KeyValuePairText(stringResource(R.string.card_info_screen_data_name), card.name)
            KeyValuePairText(stringResource(R.string.card_info_screen_data_issuer), card.issuerName)
            KeyValuePairText(stringResource(R.string.card_info_screen_data_status), card.status)
            KeyValuePairText(
                stringResource(R.string.card_info_screen_data_last_update_check),
                durationFromNowText(card.lastRefresh).replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(
                        Locale.US
                    ) else it.toString()
                }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Image(
                    bitmap = card.issuerLogo.asImageBitmap(),
                    contentDescription = stringResource(R.string.accessibility_artwork_for, card.issuerName),
                    modifier = Modifier.height(150.dp)
                )
            }
        }
    }
}