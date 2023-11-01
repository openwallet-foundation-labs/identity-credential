package com.android.mdl.appreader.issuerauth.vical

import java.util.EnumSet

enum class OptionalCertificateInfoKey : Key, CertificateInfoKey {
    CERTIFICATE_PROFILE, ISSUING_AUTHORITY, ISSUING_COUNTRY, STATE_OR_PROVINCE_NAME, ISSUER, SUBJECT, NOT_BEFORE, NOT_AFTER, EXTENSIONS;

    companion object {
        val certificateBasedFields: Set<OptionalCertificateInfoKey> = EnumSet.of(
            CERTIFICATE_PROFILE,
            ISSUING_COUNTRY,
            STATE_OR_PROVINCE_NAME,
            ISSUER,
            SUBJECT,
            NOT_BEFORE,
            NOT_AFTER
        )
    }
}