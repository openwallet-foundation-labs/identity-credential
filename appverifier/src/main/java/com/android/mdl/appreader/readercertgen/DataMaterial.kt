package com.android.mdl.appreader.readercertgen

import java.util.Optional

interface DataMaterial {
    fun subjectDN(): String
    fun issuerDN(): String
    fun issuerAlternativeName(): Optional<String>
}