package org.multipaz.openid4vci.credential

import org.multipaz.crypto.EcPublicKey
import org.multipaz.documenttype.knowntypes.DrivingLicense
import org.multipaz.rpc.handler.InvalidRequestException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.multipaz.cbor.DataItem
import org.multipaz.openid4vci.request.wellKnownOpenidCredentialIssuer
import org.multipaz.openid4vci.util.IssuanceState

/**
 * Factory for credentials of a particular type.
 *
 * All credentials that this OpenId4VCI server can issue should be registered here (see
 * this class companion object's `init`). Corresponding entries will appear in the server
 * metadata (see [wellKnownOpenidCredentialIssuer]) and on the server's main page
 * automatically.
 */
internal interface CredentialFactory {
    val offerId: String
    val scope: String
    val format: Openid4VciFormat
    val requireClientAttestation: Boolean get() = true
    val requireKeyAttestation: Boolean get() = true
    val proofSigningAlgorithms: List<String>  // must be empty for keyless credentials
    val cryptographicBindingMethods: List<String>  // must be empty for keyless credentials
    val credentialSigningAlgorithms: List<String>
    val name: String  // human-readable name
    val logo: String?  // relative URL for the image

    /**
     * Creates the credential. [authenticationKey] must be non-null for key-bound
     * credentials and null for keyless ones.
     */
    suspend fun makeCredential(data: DataItem, authenticationKey: EcPublicKey?): String

    companion object {
        val byOfferId: Map<String, CredentialFactory>
        val supportedScopes: Set<String>

        init {
            val makers = mutableListOf(
                CredentialFactoryMdl(),
                CredentialFactoryUtopiaNaturatization(),
                CredentialFactoryUtopiaMovieTicket(),
                CredentialFactoryUtopiaMdl()
            )
            byOfferId = makers.associateBy { it.offerId }
            supportedScopes = makers.map { it.scope }.toSet()
        }

        val DEFAULT_PROOF_SIGNING_ALGORITHMS = listOf("ES256")
        val DEFAULT_CREDENTIAL_SIGNING_ALGORITHMS = listOf("ES256")
    }
}

internal sealed class Openid4VciFormat {
    abstract val id: String

    companion object {
        fun fromJson(json: JsonObject): Openid4VciFormat? {
            return when (val format = json["format"]?.jsonPrimitive?.content) {
                "dc+sd-jwt" -> Openid4VciFormatSdJwt(json["vct"]!!.jsonPrimitive.content)
                "mso_mdoc" -> Openid4VciFormatMdoc(json["doctype"]!!.jsonPrimitive.content)
                null -> null
                else -> throw InvalidRequestException("Unsupported format '$format'")
            }
        }
    }
}

internal data class Openid4VciFormatMdoc(val docType: String) : Openid4VciFormat() {
    override val id: String get() = "mso_mdoc"
}

internal val openId4VciFormatMdl = Openid4VciFormatMdoc(DrivingLicense.MDL_DOCTYPE)

internal data class Openid4VciFormatSdJwt(val vct: String) : Openid4VciFormat() {
    override val id: String get() = "dc+sd-jwt"
}
