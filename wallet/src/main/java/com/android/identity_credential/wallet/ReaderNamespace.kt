package com.android.identity_credential.wallet

data class ReaderNamespace(
    val name: String,
    val dataElements: Map<String, ReaderDataElement>
)
