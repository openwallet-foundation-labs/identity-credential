package org.multipaz.wallet.ui.prompt.consent

/**
 * Details with the document that is being presented in the consent dialog.
 *
 * @property name the name of the document e.g. "Erika's Driving License"
 * @property description the description e.g. "Driving License" or "Government-Issued ID"
 * @property cardArt the card art for the document
 */
data class ConsentDocument(
    val name: String,
    val description: String,
    val cardArt: ByteArray
)