package org.multipaz.openid4vci.credential

import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.EcPublicKey
import org.multipaz.crypto.X509Cert
import org.multipaz.documenttype.knowntypes.EUPersonalID
import org.multipaz.documenttype.knowntypes.UtopiaNaturalization
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.backend.Resources
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.multipaz.cbor.DataItem
import org.multipaz.crypto.X509CertChain
import org.multipaz.sdjwt.SdJwt

internal class CredentialFactoryUtopiaNaturatization : CredentialFactory {
    override val offerId: String
        get() = "utopia_naturalization"

    override val scope: String
        get() = "utopia_naturalization_sd_jwt"

    override val format: Openid4VciFormat
        get() = FORMAT

    override val requireClientAttestation: Boolean get() = false

    override val requireKeyAttestation: Boolean get() = false

    override val proofSigningAlgorithms: List<String>
        get() = CredentialFactory.DEFAULT_PROOF_SIGNING_ALGORITHMS

    override val cryptographicBindingMethods: List<String>
        get() = listOf("jwk")

    override val credentialSigningAlgorithms: List<String>
        get() = CredentialFactory.DEFAULT_CREDENTIAL_SIGNING_ALGORITHMS

    override val name: String
        get() = "Utopia Naturalization Certificate"

    override val logo: String
        get() = "naturalization.png"

    override suspend fun makeCredential(
        data: DataItem,
        authenticationKey: EcPublicKey?
    ): String {
        check(authenticationKey != null)
        val coreData = data["core"]
        val identityAttributes = buildJsonObject {
            put("given_name", coreData["given_name"].asTstr)
            put("family_name", coreData["family_name"].asTstr)
            put("birth_date", coreData["birth_date"].asDateString.toString())
            put("naturalization_date", "2024-05-01")  // Utopia Naturalization Day (aka April Fools)
        }

        val now = Clock.System.now()

        val timeSigned = now
        val validFrom = Instant.parse("2024-04-01T12:00:00Z")
        val validUntil = Instant.parse("2034-04-01T12:00:00Z")

        val resources = BackendEnvironment.getInterface(Resources::class)!!
        val documentSigningKeyCert =
            X509Cert.fromPem(resources.getStringResource("ds_certificate.pem")!!)
        val documentSigningKey = EcPrivateKey.fromPem(
            resources.getStringResource("ds_private_key.pem")!!,
            documentSigningKeyCert.ecPublicKey)

        val sdJwt = SdJwt.create(
            issuerKey = documentSigningKey,
            issuerAlgorithm = documentSigningKey.curve.defaultSigningAlgorithmFullySpecified,
            issuerCertChain = X509CertChain(listOf(documentSigningKeyCert)),
            kbKey = authenticationKey,
            claims = identityAttributes,
            nonSdClaims = buildJsonObject {
                put("iss", "https://example-issuer.com")
                put("vct", UtopiaNaturalization.VCT)
                put("iat", timeSigned.epochSeconds)
                put("nbf", validFrom.epochSeconds)
                put("exp", validUntil.epochSeconds)
            }
        )
        return sdJwt.compactSerialization
    }

    companion object {
        private val FORMAT = Openid4VciFormatSdJwt(UtopiaNaturalization.VCT)
    }
}