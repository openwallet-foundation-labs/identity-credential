package com.android.identity_credential.wallet.navigation

import android.os.Build
import android.os.Bundle
import androidx.navigation.NavType
import com.android.identity_credential.wallet.util.ParcelableCredentialRequest
import com.google.gson.Gson

/*
    ////////////////////////////////

    NavTypeParameters file contains custom type parameters used in Navigation

    ////////////////////////////////
 */

/**
 * CredentialRequestParamType defines passing a CredentialRequest parameter by parcelizing it.
 */
class CredentialRequestParamType : NavType<ParcelableCredentialRequest>(isNullableAllowed = false) {
    override fun get(bundle: Bundle, key: String): ParcelableCredentialRequest? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            return bundle.getParcelable(key, ParcelableCredentialRequest::class.java)

        return bundle.getParcelable(key)
    }

    override fun parseValue(value: String): ParcelableCredentialRequest {
        return Gson().fromJson(value, ParcelableCredentialRequest::class.java)
    }

    override fun put(bundle: Bundle, key: String, value: ParcelableCredentialRequest) {
        bundle.putParcelable(key, value)
    }

}