package org.multipaz.openid4vci.credential

import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.EcPublicKey
import org.multipaz.crypto.X509Cert
import org.multipaz.documenttype.knowntypes.UtopiaMovieTicket
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.backend.Resources
import org.multipaz.sdjwt.Issuer
import org.multipaz.sdjwt.SdJwtVcGenerator
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.multipaz.openid4vci.util.IssuanceState
import kotlin.random.Random

internal class CredentialFactoryUtopiaMovieTicket : CredentialFactory {
    override val offerId: String
        get() = "utopia_movie_ticket"

    override val scope: String
        get() = "utopia_movie_ticket_sd_jwt"

    override val format: Openid4VciFormat
        get() = FORMAT

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
        state: IssuanceState,
        authenticationKey: EcPublicKey?
    ): String {
        check(authenticationKey == null)
        val identityAttributes = buildJsonObject {
            put("ticket_number", "123456789")
            put("seat_id", "G2")
        }

        val sdJwtVcGenerator = SdJwtVcGenerator(
            random = Random,
            payload = identityAttributes,
            vct = UtopiaMovieTicket.MOVIE_TICKET_VCT,
            issuer = Issuer("https://example-issuer.com", Algorithm.ESP256, "key-1")
        )

        val now = Clock.System.now()

        val timeSigned = now
        val validFrom = Instant.parse("2024-04-01T12:00:00Z")
        val validUntil = Instant.parse("2034-04-01T12:00:00Z")

        sdJwtVcGenerator.timeSigned = timeSigned
        sdJwtVcGenerator.timeValidityBegin = validFrom
        sdJwtVcGenerator.timeValidityEnd = validUntil

        val resources = BackendEnvironment.getInterface(Resources::class)!!
        val documentSigningKeyCert =
            X509Cert.fromPem(resources.getStringResource("ds_certificate.pem")!!)
        val documentSigningKey = EcPrivateKey.fromPem(
            resources.getStringResource("ds_private_key.pem")!!,
            documentSigningKeyCert.ecPublicKey)
        val sdJwt = sdJwtVcGenerator.generateSdJwt(documentSigningKey)

        return sdJwt.toString()
    }

    companion object {
        private val FORMAT = Openid4VciFormatSdJwt(UtopiaMovieTicket.MOVIE_TICKET_VCT)
    }
}