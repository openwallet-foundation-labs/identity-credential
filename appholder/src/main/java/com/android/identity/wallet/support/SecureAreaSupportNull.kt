package com.android.identity.wallet.support

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.android.identity.mdoc.credential.MdocCredential
import com.android.identity.securearea.CreateKeySettings
import com.android.identity.securearea.KeyUnlockData
import com.android.identity.wallet.selfsigned.OutlinedContainerVertical
import kotlinx.datetime.Instant

class SecureAreaSupportNull : SecureAreaSupport {

    private val state = SecureAreaSupportStateNull()

    @Composable
    override fun SecureAreaAuthUi(onUiStateUpdated: (newState: SecureAreaSupportState) -> Unit) {
        val compositionState by remember { mutableStateOf(state) }
        LaunchedEffect(key1 = compositionState) {
            onUiStateUpdated(compositionState)
        }
        OutlinedContainerVertical(
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                text = "The selected Secure Area lacks dedicated support so no additional options are available. " +
                        "Please add a SecureAreaSupport-derived class.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    override fun Fragment.unlockKey(
        credential: MdocCredential,
        onKeyUnlocked: (unlockData: KeyUnlockData?) -> Unit,
        onUnlockFailure: (wasCancelled: Boolean) -> Unit
    ) {
        throw IllegalStateException("No implementation")
    }

    override fun getSecureAreaSupportState(): SecureAreaSupportState = state

    override fun createAuthKeySettingsConfiguration(secureAreaSupportState: SecureAreaSupportState): ByteArray =
        ByteArray(0)

    override fun createAuthKeySettingsFromConfiguration(
        encodedConfiguration: ByteArray,
        challenge: ByteArray,
        validFrom: Instant,
        validUntil: Instant
    ): CreateKeySettings = CreateKeySettings()
}
