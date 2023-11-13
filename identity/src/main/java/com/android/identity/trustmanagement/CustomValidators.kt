package com.android.identity.trustmanagement

import java.security.cert.PKIXCertPathChecker

object CustomValidators {
    fun getByDocType(mdocType: String): List<PKIXCertPathChecker>
    {
        when (mdocType){
            "org.iso.18013.5.1.mDL" -> return listOf(CountryValidator())
            else -> return emptyList()
        }
    }
}