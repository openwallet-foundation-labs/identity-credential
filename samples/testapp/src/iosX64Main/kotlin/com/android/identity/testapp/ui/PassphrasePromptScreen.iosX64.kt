package com.android.identity.testapp.ui

import com.android.identity.securearea.PassphraseConstraints


actual suspend fun showPassphrasePrompt(
    constraints: PassphraseConstraints,
    title: String,
    content: String
): String? = "WIP"