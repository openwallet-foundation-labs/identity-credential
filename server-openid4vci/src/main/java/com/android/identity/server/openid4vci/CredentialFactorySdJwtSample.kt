package com.android.identity.server.openid4vci

import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.EcPrivateKey
import com.android.identity.crypto.EcPublicKey
import com.android.identity.crypto.X509Cert
import com.android.identity.documenttype.knowntypes.EUPersonalID
import com.android.identity.flow.server.FlowEnvironment
import com.android.identity.flow.server.Resources
import com.android.identity.sdjwt.Issuer
import com.android.identity.sdjwt.SdJwtVcGenerator
import com.android.identity.sdjwt.util.JsonWebKey
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.random.Random
import kotlin.time.Duration.Companion.days

internal class CredentialFactorySdJwtSample : CredentialFactory {
    override val offerId: String
        get() = "sample"

    override val scope: String
        get() = "sample_sd_jwt"

    override val format: Openid4VciFormat
        get() = FORMAT

    override val proofSigningAlgorithms: List<String>
        get() = CredentialFactory.DEFAULT_PROOF_SIGNING_ALGORITHMS

    override val cryptographicBindingMethods: List<String>
        get() = listOf("jwk")

    override val credentialSigningAlgorithms: List<String>
        get() = CredentialFactory.DEFAULT_CREDENTIAL_SIGNING_ALGORITHMS

    override val name: String
        get() = "Example EAA (SD-JWT)"

    override val logo: String
        get() = "card-generic.png"

    override suspend fun makeCredential(
        environment: FlowEnvironment,
        state: IssuanceState,
        authenticationKey: EcPublicKey
    ): String {
        val data = state.credentialData!!
        val identityAttributes = buildJsonObject {
            put("given_name", JsonPrimitive(data.getDataElementString(EUPersonalID.EUPID_NAMESPACE, "given_name")))
            put("family_name", JsonPrimitive(data.getDataElementString(EUPersonalID.EUPID_NAMESPACE, "family_name")))
        }

        val sdJwtVcGenerator = SdJwtVcGenerator(
            random = Random,
            payload = identityAttributes,
            docType = EUPersonalID.EUPID_VCT,
            issuer = Issuer("https://example-issuer.com", Algorithm.ES256, "key-1")
        )

        val now = Clock.System.now()

        val timeSigned = now
        val validFrom = now
        val validUntil = validFrom + 30.days

        sdJwtVcGenerator.publicKey = JsonWebKey(authenticationKey)
        sdJwtVcGenerator.timeSigned = timeSigned
        sdJwtVcGenerator.timeValidityBegin = validFrom
        sdJwtVcGenerator.timeValidityEnd = validUntil

        val resources = environment.getInterface(Resources::class)!!
        val documentSigningKeyCert =
            X509Cert.fromPem(resources.getStringResource("ds_certificate.pem")!!)
        val documentSigningKey = EcPrivateKey.fromPem(
            resources.getStringResource("ds_private_key.pem")!!,
            documentSigningKeyCert.ecPublicKey)
        val sdJwt = sdJwtVcGenerator.generateSdJwt(documentSigningKey)

        return sdJwt.toString()
    }

    companion object {
        val FORMAT = Openid4VciFormatSdJwt("example")
    }
}