package org.multipaz.presentment

import org.multipaz.document.Document

/**
 * A set of credentials that can be presented together.
 *
 * @property optional whether the credential set is optional
 * @property options the elements in the credential set.
 */
interface PresentmentCredentialSet {
    val optional: Boolean
    val options: List<PresentmentCredentialSetOption>
}

/*
/**
 * Picks a [PresentmentCredentialSet] matching a list of documents.
 *
 * @param preSelection a list of documents, preselected by the user in e.g. the Android Credential Manager picker.
 * @return the [PresentmentCredentialSet] which [preSelection] matched or `null` if not found.
 */
fun List<PresentmentCredentialSet>.select(preSelection: List<Document>): PresentmentCredentialSet? {
    /*
    forEach { credentialPresentmentSet ->
        if (credentialPresentmentSet.options.size == preSelection.size) {
            val elements = mutableListOf<PresentmentCredentialSetElement>()
            credentialPresentmentSet.elements.forEachIndexed { n, element ->
                val document = preSelection[n]
                val match = element.matches.firstOrNull { it.credential.document == document }
                if (match == null) {
                    return@forEach
                }
                elements.add(PresentmentCredentialSetElement(
                    matches = listOf(match)
                ))
            }
            return PresentmentCredentialSet(
                elements = elements,
                dcqlResponsePath = credentialPresentmentSet.dcqlResponsePath
            )
        }
    }
    return null

     */

    /*
    TODO
    val preSelectionAsSet = preSelection.toSet()
    forEach { credentialPresentmentSet ->
        val documentsForPresentmentSet = credentialPresentmentSet.credentials.map { it.credential.document }.toSet()
        if (documentsForPresentmentSet == preSelectionAsSet) {
            return credentialPresentmentSet
        }
    }
     */
    return null

}

 */