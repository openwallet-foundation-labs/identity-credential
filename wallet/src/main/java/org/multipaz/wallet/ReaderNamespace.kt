package org.multipaz.wallet

data class ReaderNamespace(
    val name: String,
    val dataElements: Map<String, ReaderDataElement>
)
