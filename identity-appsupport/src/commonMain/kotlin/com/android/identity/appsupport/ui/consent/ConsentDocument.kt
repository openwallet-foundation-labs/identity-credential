package com.android.identity.appsupport.ui.consent

import kotlinx.io.bytestring.ByteString

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
    val cardArt: ByteString
)