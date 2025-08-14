package org.multipaz.presentment

import org.multipaz.document.Document

interface PresentmentData {
    val credentialSets: List<PresentmentCredentialSet>

    fun consolidateSingleMemberOptions(): PresentmentData

    /**
     * Picks the first/default options for a given [PresentmentData].
     *
     * This is useful for wallets which wish to skip the consent prompt.
     *
     * @return a [PresentmentSelection].
     */
    fun pickDefault(preSelectedDocuments: List<Document>): PresentmentSelection
}
