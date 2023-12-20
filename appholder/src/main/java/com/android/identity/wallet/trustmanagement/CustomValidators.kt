package com.android.identity.wallet.trustmanagement

import java.security.cert.PKIXCertPathChecker

/**
 * Object used to obtain custom validators based on docType
 */
object CustomValidators {
    fun getByDocType(docType: String): List<PKIXCertPathChecker>
    {
        when (docType){
            "org.iso.18013.5.1.mDL" -> return listOf(CountryValidator())
            else -> return emptyList()
        }
    }
}