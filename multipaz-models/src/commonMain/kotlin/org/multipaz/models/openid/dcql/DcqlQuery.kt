package org.multipaz.models.openid.dcql

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.multipaz.claim.Claim
import org.multipaz.claim.findMatchingClaim
import org.multipaz.credential.Credential
import org.multipaz.crypto.EcCurve
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.mdoc.credential.MdocCredential
import org.multipaz.models.presentment.PresentmentSource
import org.multipaz.request.JsonRequestedClaim
import org.multipaz.request.MdocRequestedClaim
import org.multipaz.request.RequestedClaim
import org.multipaz.sdjwt.credential.SdJwtVcCredential
import org.multipaz.util.Logger

/**
 * DCQL top-level query.
 *
 * Use [Companion.fromJson] to construct an instance and use [execute] to
 * select credentials which satisfy the query.
 *
 * Reference: OpenID4VP 1.0 Section 6.
 *
 * @property credentialQueries list of Credential Queries.
 * @property credentialSetQueries list of Credential Set Queries.
 */
data class DcqlQuery(
    val credentialQueries: List<DcqlCredentialQuery>,
    val credentialSetQueries: List<DcqlCredentialSetQuery>
) {
    /**
     * Executes the query,
     *
     * If a [DocumentTypeRepository] is passed, it will be used to look up the document type
     * and if a type is found, it'll be used to populate the the [Claim.attribute] field of
     * the resulting claims.
     *
     * @param presentmentSource the [PresentmentSource] to use as a source of truth for presentment.
     * @param keyAgreementPossible if non-empty, a credential using Key Agreement may be returned provided
     *   its private key is one of the given curves.
     */
    suspend fun execute(
        presentmentSource: PresentmentSource,
        keyAgreementPossible: List<EcCurve> = emptyList()
    ): List<CredentialResponse> {
        val result = mutableListOf<CredentialResponse>()
        for (credentialQuery in credentialQueries) {
            val credsSatisfyingMeta = when (credentialQuery.format) {
                "mso_mdoc", "mso_mdoc_zk" -> {
                    val ret = mutableListOf<Credential>()
                    for (documentId in presentmentSource.documentStore.listDocuments()) {
                        val document = presentmentSource.documentStore.lookupDocument(documentId) ?: continue
                        document.getCertifiedCredentials().find {
                            it is MdocCredential && it.docType == credentialQuery.mdocDocType
                        }?.let { ret.add(it) }
                    }
                    ret
                }

                "dc+sd-jwt" -> {
                    val ret = mutableListOf<Credential>()
                    for (documentId in presentmentSource.documentStore.listDocuments()) {
                        val document = presentmentSource.documentStore.lookupDocument(documentId) ?: continue
                        document.getCertifiedCredentials().find {
                            it is SdJwtVcCredential && credentialQuery.vctValues!!.contains(it.vct)
                        }?.let { ret.add(it) }
                    }
                    ret
                }

                else -> emptyList()
            }

            val matches = mutableListOf<CredentialResponseMatch>()
            for (cred in credsSatisfyingMeta) {
                val claimsInCredential = cred.getClaims(documentTypeRepository = presentmentSource.documentTypeRepository)
                if (credentialQuery.claimSets.isEmpty()) {
                    var didNotMatch = false
                    val matchingClaimValues = mutableListOf<Claim>()
                    for (requestedClaim in credentialQuery.claims) {
                        val matchingCredentialClaimValue = claimsInCredential.findMatchingClaim(requestedClaim)
                        if (matchingCredentialClaimValue != null) {
                            matchingClaimValues.add(matchingCredentialClaimValue)
                        } else {
                            Logger.w(TAG, "Error resolving requested claim ${requestedClaim}")
                            didNotMatch = true
                            break
                        }
                    }
                    if (!didNotMatch) {
                        // All claims matched, we have a candidate
                        matches.add(
                            CredentialResponseMatch(
                                credential = presentmentSource.selectCredential(
                                    document = cred.document,
                                    requestedClaims = credentialQuery.claims,
                                    keyAgreementPossible = keyAgreementPossible
                                )!!,
                                claims = matchingClaimValues
                            )
                        )
                    }
                } else {
                    // Go through all the claim sets, one at a time, pick the first to match
                    for (claimSet in credentialQuery.claimSets) {
                        var didNotMatch = false
                        val matchingClaimValues = mutableListOf<Claim>()
                        for (claimId in claimSet.claimIdentifiers) {
                            val requestedClaim = credentialQuery.claimIdToClaim[claimId]
                            if (requestedClaim == null) {
                                didNotMatch = true
                                break
                            }
                            val credentialClaimValue = claimsInCredential.findMatchingClaim(requestedClaim)
                            if (credentialClaimValue != null) {
                                matchingClaimValues.add(credentialClaimValue)
                            } else {
                                didNotMatch = true
                                break
                            }
                        }
                        if (!didNotMatch) {
                            // All claims matched, we have a candidate
                            matches.add(
                                CredentialResponseMatch(
                                    credential = presentmentSource.selectCredential(
                                        document = cred.document,
                                        requestedClaims = credentialQuery.claims,
                                        keyAgreementPossible = keyAgreementPossible
                                    )!!,
                                    claims = matchingClaimValues
                                )
                            )
                            break
                        }
                    }
                }
            }
            result.add(
                CredentialResponse(
                    credentialQuery = credentialQuery,
                    credentialSetQuery = null,
                    matches = matches
                )
            )
        }

        // From 6.3.1.2. Selecting Credentials:
        //
        //   If credential_sets is not provided, the Verifier requests presentations for
        //   all Credentials in credentials to be returned.
        //
        if (credentialSetQueries.isEmpty()) {
            // So, really simple, bail unless we have at least one match per requested credential
            for (response in result) {
                if (response.matches.isEmpty()) {
                    throw DcqlCredentialQueryException(
                        "No matches for credential query with id ${response.credentialQuery.id}"
                    )
                }
            }
            return result
        }

        // From 6.3.1.2. Selecting Credentials:
        //
        //   Otherwise, the Verifier requests presentations of Credentials to be returned satisfying
        //
        //     - all of the Credential Set Queries in the credential_sets array where the
        //       required attribute is true or omitted, and
        //     - optionally, any of the other Credential Set Queries.
        //
        val csqRet = mutableListOf<CredentialResponse>()
        for (csq in credentialSetQueries) {
            // In this case, simply go through all the matches produced above and pick the
            // credentials from the highest preferred option. If none of them work, bail only
            // if the credential set was required.
            //
            var satisfiedCsq = false
            for (option in csq.options) {
                if (option.isSatisfied(result)) {
                    for (credentialId in option.credentialIds) {
                        val responseMatched = result.find { it.credentialQuery.id == credentialId }!!
                        csqRet.add(
                            CredentialResponse(
                                credentialQuery = responseMatched.credentialQuery,
                                credentialSetQuery = csq,
                                matches = responseMatched.matches
                            )
                        )
                    }
                    satisfiedCsq = true
                    break
                }
            }
            if (!satisfiedCsq && csq.required) {
                throw DcqlCredentialQueryException(
                    "No credentials match required credential_set query"
                )
            }
        }
        return csqRet
    }

    companion object {
        private const val TAG = "DcqlQuery"

        /**
         * Parses DCQL according to OpenID4VP.
         *
         * Reference: OpenID4VP 1.0 Section 6.
         *
         * @param json a [JsonObject] with the DCQL.
         * @return a [DcqlQuery] object
         */
        fun fromJson(json: JsonObject): DcqlQuery {
            val dcqlCredentialQueries = mutableListOf<DcqlCredentialQuery>()
            val dcqlCredentialSetQueries = mutableListOf<DcqlCredentialSetQuery>()

            val credentials = json["credentials"]!!.jsonArray
            for (credential in credentials) {
                val c = credential.jsonObject
                val id = c["id"]!!.jsonPrimitive.content
                val format = c["format"]!!.jsonPrimitive.content
                val meta = c["meta"]!!.jsonObject
                var mdocDocType: String? = null
                var vctValues: List<String>? = null
                when (format) {
                    "mso_mdoc", "mso_mdoc_zk" -> {
                        mdocDocType = meta["doctype_value"]!!.jsonPrimitive.content
                    }

                    "dc+sd-jwt" -> {
                        vctValues = meta["vct_values"]!!.jsonArray.map { it.jsonPrimitive.content }
                    }
                }

                val dcqlClaims = mutableListOf<RequestedClaim>()
                val dcqlClaimIdToClaim = mutableMapOf<String, RequestedClaim>()
                val dcqlClaimSets = mutableListOf<DcqlClaimSet>()

                val claims = c["claims"]!!.jsonArray
                check(claims.size > 0)
                for (claim in claims) {
                    val cl = claim.jsonObject
                    val claimId = cl["id"]?.jsonPrimitive?.content
                    val path = cl["path"]!!.jsonArray
                    val values = cl["values"]?.jsonArray
                    val mdocIntentToRetain = cl["intent_to_retain"]?.jsonPrimitive?.boolean
                    val requestedClaim = if (mdocDocType != null) {
                        require(path.size == 2)
                        MdocRequestedClaim(
                            id = claimId,
                            namespaceName = path[0].jsonPrimitive.content,
                            dataElementName = path[1].jsonPrimitive.content,
                            intentToRetain = mdocIntentToRetain ?: false,
                            values = values
                        )
                    } else {
                        JsonRequestedClaim(
                            id = claimId,
                            claimPath = path,
                            values = values
                        )
                    }
                    dcqlClaims.add(requestedClaim)
                    if (claimId != null) {
                        dcqlClaimIdToClaim.put(claimId, requestedClaim)
                    }
                }

                val claimSets = c["claim_sets"]?.jsonArray
                if (claimSets != null) {
                    for (claimSet in claimSets) {
                        val cs = claimSet.jsonArray
                        dcqlClaimSets.add(
                            DcqlClaimSet(
                                claimIdentifiers = cs.map { it.jsonPrimitive.content }
                            )
                        )
                    }
                }

                dcqlCredentialQueries.add(
                    DcqlCredentialQuery(
                        id = id,
                        format = format,
                        meta = meta,
                        mdocDocType = mdocDocType,
                        vctValues = vctValues,
                        claims = dcqlClaims,
                        claimSets = dcqlClaimSets,
                        claimIdToClaim = dcqlClaimIdToClaim
                    )
                )
            }

            val credentialSets = json["credential_sets"]?.jsonArray
            if (credentialSets != null) {
                for (credentialSet in credentialSets) {
                    val s = credentialSet.jsonObject
                    val required = s["required"]?.jsonPrimitive?.boolean ?: true

                    val credentialSetOptions = mutableListOf<DcqlCredentialSetOption>()

                    val options = s["options"]!!.jsonArray
                    for (option in options) {
                        credentialSetOptions.add(
                            DcqlCredentialSetOption(
                                credentialIds = option.jsonArray.map { it.jsonPrimitive.content }
                            )
                        )
                    }

                    dcqlCredentialSetQueries.add(
                        DcqlCredentialSetQuery(
                            required = required,
                            options = credentialSetOptions
                        )
                    )
                }
            }

            return DcqlQuery(
                credentialQueries = dcqlCredentialQueries,
                credentialSetQueries = dcqlCredentialSetQueries
            )
        }
    }
}
