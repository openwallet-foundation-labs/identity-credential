package com.android.identity.wallet.support

import android.os.Parcelable
import com.android.identity.wallet.composables.state.MdocAuthOption

interface SecureAreaSupportState : Parcelable {
    val mDocAuthOption: MdocAuthOption
}