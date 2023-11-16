package com.android.identity.wallet.support

import com.android.identity.credential.Credential
import com.android.identity.securearea.SecureArea
import com.android.identity.wallet.composables.state.MdocAuthOption
import kotlinx.parcelize.Parcelize

@Parcelize
class SecureAreaSupportStateNull : SecureAreaSupportState {

    override val mDocAuthOption: MdocAuthOption
        get() = MdocAuthOption()
}