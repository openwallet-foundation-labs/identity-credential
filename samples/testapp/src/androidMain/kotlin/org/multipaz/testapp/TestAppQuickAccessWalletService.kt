package org.multipaz.testapp

import android.os.Build
import android.service.quickaccesswallet.GetWalletCardsCallback
import android.service.quickaccesswallet.GetWalletCardsRequest
import android.service.quickaccesswallet.GetWalletCardsResponse
import android.service.quickaccesswallet.QuickAccessWalletService
import android.service.quickaccesswallet.SelectWalletCardRequest
import android.service.quickaccesswallet.WalletCard
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.R)
class TestAppQuickAccessWalletService: QuickAccessWalletService() {
    override fun onWalletCardsRequested(
        request: GetWalletCardsRequest,
        callback: GetWalletCardsCallback
    ) {
        val response = GetWalletCardsResponse(
            listOf<WalletCard>(),
            0
        )
        callback.onSuccess(response)
    }

    override fun onWalletCardSelected(p0: SelectWalletCardRequest) {
    }

    override fun onWalletDismissed() {
    }
}
