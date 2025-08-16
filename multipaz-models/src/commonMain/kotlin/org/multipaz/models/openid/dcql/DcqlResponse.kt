package org.multipaz.models.openid.dcql

import org.multipaz.document.Document
import org.multipaz.presentment.PresentmentCredentialSetOptionMemberMatch
import org.multipaz.presentment.PresentmentData
import org.multipaz.presentment.PresentmentSelection
import org.multipaz.util.Logger
import org.multipaz.util.generateAllPaths

private const val TAG = "DcqlResponse"

/**
 * The result of executing a DCQL query against available credentials.
 *
 * The response consists of the credential sets. Queries not declaring any `credential_sets`
 * parameter in the DCQL query are equivalent to queries with a single non-optional credential
 * set per credential query, and are treated as such.
 *
 * The application must return all credential sets except if they are marked as optional.
 *
 * @property credentialSets the credential sets which can be returned.
 */
data class DcqlResponse(
    override val credentialSets: List<DcqlResponseCredentialSet>
): PresentmentData {

    override fun consolidateSingleMemberOptions(): PresentmentData {
        val ret = mutableListOf<DcqlResponseCredentialSet>()
        credentialSets.forEach { credentialSet ->
            ret.add(credentialSet.consolidateSingleMemberOptions())
        }
        return DcqlResponse(ret)
    }

    override fun pickDefault(preSelectedDocuments: List<Document>): PresentmentSelection {
        if (preSelectedDocuments.isNotEmpty()) {
            pickFromPreselectedDocuments(preSelectedDocuments)?.let {
                return it
            }
        }

        val matches = mutableListOf<PresentmentCredentialSetOptionMemberMatch>()
        credentialSets.forEach { credentialSet ->
            val option = credentialSet.options[0]
            option.members.forEach { member ->
                matches.add(member.matches[0])
            }
        }
        return PresentmentSelection(matches = matches)
    }

    private fun pickFromPreselectedDocuments(preselectedDocuments: List<Document>): PresentmentSelection? {
        val credentialSetsMaxPath = mutableListOf<Int>()
        credentialSets.forEachIndexed { n, credentialSet ->
            // If a credentialSet is optional, it's an extra combination we tag at the end
            credentialSetsMaxPath.add(credentialSet.options.size + (if (credentialSet.optional) 1 else 0))
        }

        val combinations = mutableListOf<Combination>()
        for (path in credentialSetsMaxPath.generateAllPaths()) {
            val elements = mutableListOf<CombinationElement>()
            credentialSets.forEachIndexed { credentialSetNum, credentialSet ->
                val omitCredentialSet = (path[credentialSetNum] == credentialSet.options.size)
                if (omitCredentialSet) {
                    check(credentialSet.optional)
                } else {
                    val option = credentialSet.options[path[credentialSetNum]]
                    for (member in option.members) {
                        elements.add(CombinationElement(matches = member.matches))
                    }
                }
            }
            combinations.add(Combination(elements = elements))
        }

        val setOfPreselectedDocuments = preselectedDocuments.toSet()
        combinations.forEach { combination ->
            if (combination.elements.size == preselectedDocuments.size) {
                val chosenMatches = mutableListOf<PresentmentCredentialSetOptionMemberMatch>()
                combination.elements.forEachIndexed { n, element ->
                    val match = element.matches.find { setOfPreselectedDocuments.contains(it.credential.document) }
                    if (match == null) {
                        return@forEach
                    }
                    chosenMatches.add(match)
                }
                return PresentmentSelection(chosenMatches)
            }
        }
        Logger.w(TAG, "Error picking combination for pre-selected documents")
        return null
    }
}

private data class CombinationElement(
    val matches: List<PresentmentCredentialSetOptionMemberMatch>
)

private data class Combination(
    val elements: List<CombinationElement>
)
