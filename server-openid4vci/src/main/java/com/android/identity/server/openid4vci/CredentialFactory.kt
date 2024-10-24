package com.android.identity.server.openid4vci

import com.android.identity.crypto.EcPublicKey
import com.android.identity.documenttype.knowntypes.DrivingLicense
import com.android.identity.flow.handler.InvalidRequestException
import com.android.identity.flow.server.FlowEnvironment
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

internal interface CredentialFactory {
    val offerId: String
    val scope: String
    val format: Openid4VciFormat
    val proofSigningAlgorithms: List<String>
    val cryptographicBindingMethods: List<String>
    val credentialSigningAlgorithms: List<String>
    val name: String  // human-readable name
    val logo: String?  // relative URL for the image

    suspend fun makeCredential(environment: FlowEnvironment, state: IssuanceState,
                       authenticationKey: EcPublicKey): String

    companion object {
        val byOfferId: Map<String, CredentialFactory>
        val supportedScopes: Set<String>

        init {
            val makers = mutableListOf(
                CredentialFactoryMdl(),
                CredentialFactorySdJwtSample()
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
        fun fromJson(json: JsonObject): Openid4VciFormat {
            return when (val format = json["format"]?.jsonPrimitive?.content) {
                "vc+sd-jwt" -> Openid4VciFormatSdJwt(json["vct"]!!.jsonPrimitive.content)
                "mso_mdoc" -> Openid4VciFormatMdoc(json["doctype"]!!.jsonPrimitive.content)
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
    override val id: String get() = "vc+sd-jwt"
}
