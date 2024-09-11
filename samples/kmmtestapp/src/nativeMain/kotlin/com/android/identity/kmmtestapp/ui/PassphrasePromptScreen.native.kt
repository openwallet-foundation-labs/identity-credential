package com.android.identity.kmmtestapp.ui

import com.android.identity.securearea.PassphraseConstraints

/**
 * Here for correctness but only Android needs suspend functions whereas iOS composes the
 * prompt within a KMM Dialog.
 */
actual suspend fun showPassphrasePrompt(
    constraints: PassphraseConstraints,
    title: String,
    content: String
): String? = "NOT IN USE ON iOS"