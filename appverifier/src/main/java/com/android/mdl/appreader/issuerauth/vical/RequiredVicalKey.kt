package com.android.mdl.appreader.issuerauth.vical

import co.nstant.`in`.cbor.model.UnicodeString

enum class RequiredVicalKey : Key {
    VERSION, VICAL_PROVIDER, DATE, CERTIFICATE_INFOS;

    // required as not compiled with support for default methods in interfaces
    override fun keyName(): String {
        return super.keyName()
    }

    override fun getUnicodeString(): UnicodeString {
        return super.getUnicodeString()
    }
}