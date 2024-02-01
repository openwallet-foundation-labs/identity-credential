package com.android.identity.wallet.support

import com.android.identity.wallet.support.softwarekeystore.SoftwareAuthKeyCurveState
import com.android.identity.wallet.composables.state.MdocAuthOption
import kotlinx.parcelize.Parcelize

@Parcelize
data class SoftwareKeystoreSecureAreaSupportState(
    override val mDocAuthOption: MdocAuthOption = MdocAuthOption(),
    val softwareAuthKeyCurveState: SoftwareAuthKeyCurveState = SoftwareAuthKeyCurveState(),
    val passphrase: String = "",
    val authKeyCurve: SoftwareAuthKeyCurveState = SoftwareAuthKeyCurveState(),
) : SecureAreaSupportState {

}