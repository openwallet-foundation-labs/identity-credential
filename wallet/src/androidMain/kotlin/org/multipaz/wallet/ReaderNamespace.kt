package org.multipaz_credential.wallet

data class ReaderNamespace(
    val name: String,
    val dataElements: Map<String, ReaderDataElement>
)
