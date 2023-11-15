package com.android.identity.wallet.support

import android.os.Parcelable
import com.android.identity.credential.Credential
import com.android.identity.securearea.SecureArea
import com.android.identity.wallet.composables.state.MdocAuthOption

interface SecureAreaSupportState : Parcelable {

    val mDocAuthOption: MdocAuthOption

    fun createKeystoreSettings(validityInDays: Int): SecureArea.CreateKeySettings

    fun createKeystoreSettingForCredential(
        mDocAuthOption: String,
        credential: Credential
    ): SecureArea.CreateKeySettings
}