package com.android.identity.securearea

/**
 * Thrown if no [PassphrasePromptView] has registered with [PassphrasePromptModel].
 */
class PassphrasePromptViewNotAvailableException(message: String): Exception(message)