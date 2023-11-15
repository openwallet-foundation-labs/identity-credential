package com.android.identity.wallet.support

import com.android.identity.credential.Credential
import com.android.identity.securearea.SecureArea
import com.android.identity.securearea.SoftwareSecureArea
import com.android.identity.storage.EphemeralStorageEngine
import com.android.identity.util.Timestamp
import com.android.identity.wallet.support.softwarekeystore.SoftwareAuthKeyCurveState
import com.android.identity.wallet.composables.state.MdocAuthOption
import com.android.identity.wallet.composables.state.MdocAuthStateOption
import com.android.identity.wallet.util.ProvisioningUtil
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import java.security.PrivateKey
import java.security.cert.X509Certificate

@Parcelize
data class SoftwareKeystoreSecureAreaSupportState(
    override val mDocAuthOption: MdocAuthOption = MdocAuthOption(),
    val softwareAuthKeyCurveState: SoftwareAuthKeyCurveState = SoftwareAuthKeyCurveState(),
    val passphrase: String = "",
    val authKeyCurve: SoftwareAuthKeyCurveState = SoftwareAuthKeyCurveState(),
) : SecureAreaSupportState {

}