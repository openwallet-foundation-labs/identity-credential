package com.android.identity_credential.wallet.ui.prompt.consent

import com.android.identity.documenttype.DocumentAttribute

/**
 * Base class used for representing items in the consent prompt.
 *
 * @param displayName the text to display in consent prompt for the requested field.
 * @param attribute a [DocumentAttribute], if the data element is well-known.
 */
open class ConsentField(
    open val displayName: String,
    open val attribute: DocumentAttribute?
)