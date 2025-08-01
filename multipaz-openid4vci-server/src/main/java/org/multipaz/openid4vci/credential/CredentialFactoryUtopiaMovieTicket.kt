package org.multipaz.openid4vci.credential

import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.EcPublicKey
import org.multipaz.crypto.X509Cert
import org.multipaz.documenttype.knowntypes.UtopiaMovieTicket
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.backend.Resources
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.multipaz.cbor.DataItem
import org.multipaz.crypto.X509CertChain
import org.multipaz.sdjwt.SdJwt

internal class CredentialFactoryUtopiaMovieTicket : CredentialFactory {
    override val offerId: String
        get() = "utopia_movie_ticket"

    override val scope: String
        get() = "utopia_movie_ticket_sd_jwt"

    override val format: Openid4VciFormat
        get() = FORMAT

    override val requireClientAttestation: Boolean get() = false

    override val requireKeyAttestation: Boolean get() = false

    override val proofSigningAlgorithms: List<String>
        get() = listOf()  // keyless

    override val cryptographicBindingMethods: List<String>
        get() = listOf()  // keyless

    override val credentialSigningAlgorithms: List<String>
        get() = CredentialFactory.DEFAULT_CREDENTIAL_SIGNING_ALGORITHMS

    override val name: String
        get() = "Utopia Movie Ticket"

    override val logo: String
        get() = "movie_ticket.png"

    override suspend fun makeCredential(
        data: DataItem,
        authenticationKey: EcPublicKey?
    ): String {
        check(authenticationKey == null)
        val identityAttributes = buildJsonObject {
            put("ticket_number", "123456789")
            put("seat_id", "G2")
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
            kbKey = null,
            claims = identityAttributes,
            nonSdClaims = buildJsonObject {
                put("iss", "https://example-issuer.com")
                put("vct", UtopiaMovieTicket.MOVIE_TICKET_VCT)
                put("iat", timeSigned.epochSeconds)
                put("nbf", validFrom.epochSeconds)
                put("exp", validUntil.epochSeconds)
            }
        )
        return sdJwt.compactSerialization
    }

    companion object {
        private val FORMAT = Openid4VciFormatSdJwt(UtopiaMovieTicket.MOVIE_TICKET_VCT)
    }
}