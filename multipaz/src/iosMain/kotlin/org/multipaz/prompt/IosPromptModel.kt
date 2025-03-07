package org.multipaz.prompt

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * [PromptModel] for iOS platform.
 */
class IosPromptModel: PromptModel {
    override val passphrasePromptModel = SinglePromptModel<PassphraseRequest, String?>()

    override val promptModelScope: CoroutineScope by lazy {
        CoroutineScope(Dispatchers.Default + this)
    }
}