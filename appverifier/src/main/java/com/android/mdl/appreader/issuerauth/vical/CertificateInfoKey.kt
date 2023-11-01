package com.android.mdl.appreader.issuerauth.vical

/**
 * A simple interface for the required and optional key values for the CertificateInfo key / value map.
 *
 * @author UL TS BV
 */
interface CertificateInfoKey : Key {
    companion object {
        val ALL: Map<String, CertificateInfoKey> = HashMap()
        fun forKeyName(keyName: String?): CertificateInfoKey? {
            for (requiredKey in RequiredCertificateInfoKey.values()) {
                if (requiredKey.keyName() == keyName) {
                    return requiredKey
                }
            }
            for (optionalKey in RequiredCertificateInfoKey.values()) {
                if (optionalKey.keyName() == keyName) {
                    return optionalKey
                }
            }
            return null
        }
    }
}