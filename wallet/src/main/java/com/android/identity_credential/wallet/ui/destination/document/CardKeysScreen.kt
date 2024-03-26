package com.android.identity_credential.wallet.ui.destination.document

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.identity.util.Logger
import com.android.identity_credential.wallet.CardKeyInfo
import com.android.identity_credential.wallet.CardViewModel
import com.android.identity_credential.wallet.R
import com.android.identity_credential.wallet.navigation.WalletDestination
import com.android.identity_credential.wallet.ui.KeyValuePairText
import com.android.identity_credential.wallet.ui.ScreenWithAppBarAndBackButton
import com.android.identity_credential.wallet.util.asFormattedDateTimeInCurrentTimezone


private const val TAG = "CardKeysScreen"

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CardKeysScreen(
    cardId: String,
    cardViewModel: CardViewModel,
    onNavigate: (String) -> Unit
) {
    val card = cardViewModel.getCard(cardId)
    if (card == null) {
        Logger.w(TAG, "No card with id $cardId")
        onNavigate(WalletDestination.Main.route)
        return
    }


    Box(
        modifier = Modifier.fillMaxHeight()
    ) {

        val pagerState = rememberPagerState(pageCount = { card.keyInfos.size })

        ScreenWithAppBarAndBackButton(
            title = stringResource(R.string.card_keys_screen_title),
            onBackButtonClick = { onNavigate(WalletDestination.PopBackStack.route) }
        ) {

            Column {
                HorizontalPager(
                    state = pagerState,
                ) { page ->
                    CardKeyInfo(card.keyInfos[page], page, card.keyInfos.size)
                }
            }

        }

        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .wrapContentHeight()
                .fillMaxWidth()
                .height(30.dp)
                .padding(8.dp),
        ) {
            repeat(pagerState.pageCount) { iteration ->
                val color =
                    if (pagerState.currentPage == iteration) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondary
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


@Composable
private fun CardKeyInfo(cardKeyInfo: CardKeyInfo,
                        keyIndex: Int,
                        numKeys: Int) {
    Column(Modifier.padding(8.dp)) {
        KeyValuePairText("Key Number ", "${keyIndex + 1} of ${numKeys}")
        KeyValuePairText("Description", cardKeyInfo.description)
        for ((key, value) in cardKeyInfo.details) {
            KeyValuePairText(key, value)
        }
        KeyValuePairText("Usage Count", "${cardKeyInfo.usageCount}")
        KeyValuePairText("Signed At", cardKeyInfo.signedAt.asFormattedDateTimeInCurrentTimezone)
        KeyValuePairText("Valid From", cardKeyInfo.validFrom.asFormattedDateTimeInCurrentTimezone)
        KeyValuePairText("Valid Until", cardKeyInfo.validUntil.asFormattedDateTimeInCurrentTimezone)
        KeyValuePairText("Expected Update",
            cardKeyInfo?.expectedUpdate?.asFormattedDateTimeInCurrentTimezone ?: "Not Set"
        )
    }
}
