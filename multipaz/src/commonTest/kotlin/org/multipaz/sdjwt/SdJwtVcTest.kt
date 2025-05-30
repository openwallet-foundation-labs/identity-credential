package org.multipaz.sdjwt

import org.multipaz.asn1.ASN1Integer
import org.multipaz.credential.CredentialLoader
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.X500Name
import org.multipaz.crypto.X509KeyUsage
import org.multipaz.document.Document
import org.multipaz.document.DocumentStore
import org.multipaz.document.SimpleDocumentMetadata
import org.multipaz.sdjwt.SdJwtVerifiableCredential.AttributeNotDisclosedException
import org.multipaz.sdjwt.credential.KeyBoundSdJwtVcCredential
import org.multipaz.sdjwt.presentation.SdJwtVerifiablePresentation
import org.multipaz.sdjwt.util.JsonWebKey
import org.multipaz.securearea.SecureAreaRepository
import org.multipaz.securearea.software.SoftwareCreateKeySettings
import org.multipaz.securearea.software.SoftwareSecureArea
import org.multipaz.storage.ephemeral.EphemeralStorage
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.multipaz.crypto.X509CertChain
import org.multipaz.sdjwt.vc.JwtHeader
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class SdJwtVcTest {
    private lateinit var storage: EphemeralStorage
    private lateinit var secureAreaRepository: SecureAreaRepository
    private lateinit var credentialLoader: CredentialLoader

    private lateinit var document: Document
    private lateinit var timeValidityBegin: Instant
    private lateinit var timeSigned: Instant
    private lateinit var timeValidityEnd: Instant
    private lateinit var issuerCert: X509Cert
    private lateinit var credential: KeyBoundSdJwtVcCredential

    private suspend fun provisionCredential() {
        storage = EphemeralStorage()
        secureAreaRepository = SecureAreaRepository.build {
            add(SoftwareSecureArea.create(storage))
        }
        credentialLoader = CredentialLoader()
        credentialLoader.addCredentialImplementation(KeyBoundSdJwtVcCredential.CREDENTIAL_TYPE) {
            document -> KeyBoundSdJwtVcCredential(document)
        }

        val documentStore = DocumentStore(
            storage,
            secureAreaRepository,
            credentialLoader,
            SimpleDocumentMetadata::create
        )

        // Create the credential on the holder device...
        document = documentStore.createDocument()

        // Create an authentication key...
        timeSigned = Clock.System.now()
        timeValidityBegin = timeSigned.plus(1.hours)
        timeValidityEnd = timeSigned.plus(10.days)
        credential = KeyBoundSdJwtVcCredential.create(
            document,
            null,
            "domain",
            secureAreaRepository.getImplementation(SoftwareSecureArea.IDENTIFIER)!!,
            "IdentityCredential",
            SoftwareCreateKeySettings.Builder().build()
        )

        // at the issuer, start creating the credential...
        val identityAttributes = buildJsonObject {
            put("name", "Elisa Beckett")
            put("given_name", "Elisa")
            put("family_name", "Becket")
            put("over_18", true)
            put("over_21", true)
            put("address", buildJsonObject {
                put("street_address", "123 Main St")
                put("locality", "Anytown")
                put("region", "Anystate")
                put("country", "US")
            })
        }

        val issuerKey = Crypto.createEcPrivateKey(EcCurve.P256)
        val validFrom = Clock.System.now()
        val validUntil = validFrom + 5.days
        issuerCert = X509Cert.Builder(
            publicKey = issuerKey.publicKey,
            signingKey = issuerKey,
            signatureAlgorithm = issuerKey.curve.defaultSigningAlgorithm,
            serialNumber = ASN1Integer(1L),
            subject = X500Name.fromName("CN=State of Utopia"),
            issuer = X500Name.fromName("CN=State of Utopia"),
            validFrom = validFrom,
            validUntil = validUntil
        )
            .includeSubjectKeyIdentifier()
            .setKeyUsage(setOf(X509KeyUsage.DIGITAL_SIGNATURE))
            .build()

        // Issuer knows that it will use ECDSA with SHA-256.
        // Public keys and cert chains will be at https://example-issuer.com/...
        val sdJwtVcGenerator = SdJwtVcGenerator(
            random = Random(42),
            payload = identityAttributes,
            issuer = Issuer(
                "https://example-issuer.com",
                Algorithm.ESP256,
                "key-1",
                X509CertChain(listOf(issuerCert))
            )
        )

        sdJwtVcGenerator.publicKey = JsonWebKey(credential.getAttestation().publicKey)
        sdJwtVcGenerator.timeSigned = timeSigned
        sdJwtVcGenerator.timeValidityBegin = timeValidityBegin
        sdJwtVcGenerator.timeValidityEnd = timeValidityEnd

        val sdJwt = sdJwtVcGenerator.generateSdJwt(issuerKey)
        // could have also said
        //
        // sdJwt = sdJwtVcGenerator.generateSdJwt {
        //    toBeSigned, issuer ->
        //        // find some way to sign toBeSigned, using the key indicated in
        //        // issuer (which also has the alg in it) and return the signature
        // }
        //
        // for example:
        //
        // sdJwt = sdJwtVcGenerator.generateSdJwt {
        //    toBeSigned, issuer ->
        //       Signature.getSignerFor(issuer.alg).with(issuerKeyPair.private).sign(toBeSigned)
        // }
        //

        credential.certify(
            sdJwt.toString().encodeToByteArray(),
            timeValidityBegin,
            timeValidityEnd
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun testPresentationVerificationEcdsa() = runTest {
        provisionCredential()

        // on the holder device, let's prepare a presentation
        // we'll start by reading back the SD-JWT issued to us for the auth key
        val sdJwt = SdJwtVerifiableCredential.fromString(credential.issuerProvidedData.decodeToString())

        // let's check that all the possible disclosures are still attached to the SD-JWT
        val allDisclosures = sdJwt.disclosures.map { it.key }.toSet()
        assertTrue(allDisclosures.contains("name"))
        assertTrue(allDisclosures.contains("given_name"))
        assertTrue(allDisclosures.contains("family_name"))
        assertTrue(allDisclosures.contains("over_18"))
        assertTrue(allDisclosures.contains("over_21"))
        assertTrue(allDisclosures.contains("address"))

        // now, let's strip most disclosures off, except for a couple:
        val requestedAttributes = setOf("over_18", "name", "not-even-in-the-vc")
        val filteredSdJwt = sdJwt.discloseOnly(requestedAttributes)

        // we presumably received a nonce from the verifier
        val nonce: ByteArray = "some-example-nonce".encodeToByteArray()
        val nonceStr = Base64.UrlSafe.encode(nonce)

        // time to create the presentation (on the holder device) for the verifier:
        val presentationString = filteredSdJwt.createPresentation(
            credential.secureArea,
            credential.alias,
            null,
            nonceStr,
            "https://example-verifier.com"
        ).toString()

        // this would now be transmitted to the verifier, who first has to re-hydrate it into an object:
        val presentation = SdJwtVerifiablePresentation.fromString(presentationString)

        // make sure most of the disclosures are gone:
        assertFailsWith<AttributeNotDisclosedException> {presentation.getAttributeValue("given_name")}
        assertFailsWith<AttributeNotDisclosedException> {presentation.getAttributeValue("family_name")}
        assertFailsWith<AttributeNotDisclosedException> {presentation.getAttributeValue("over_21")}
        assertFailsWith<AttributeNotDisclosedException> {presentation.getAttributeValue("address")}
        assertFailsWith<AttributeNotDisclosedException> {presentation.getAttributeValue("not-even-in-the-vc")}
        assertFailsWith<AttributeNotDisclosedException> {presentation.getAttributeValue("not-requested-in-the-first-place")}

        // on the verifier, check that the key binding can be verified with the
        // key mentioned in the SD-JWT:
        presentation.verifyKeyBinding(
            checkAudience = { "https://example-verifier.com" == it },
            checkNonce = { nonceStr == it },
            checkCreationTime = { it < Clock.System.now() }
        )

        // also on the verifier, check the signature over the SD-JWT from the issuer
        presentation.sdJwtVc.verifyIssuerSignature(issuerCert.ecPublicKey)

        // let's check that the x5c element is parseable, in case that's where the verifier gets it from
        val jwtHeader = JwtHeader.fromString(presentation.sdJwtVc.header)
        assertEquals(issuerCert, jwtHeader.x5c?.certificates?.get(0))

        // at this point, the verifier could read out the attributes they were
        // interested in:
        assertEquals("Elisa Beckett", presentation.sdJwtVc.getAttributeValue("name").jsonPrimitive.content)
        assertEquals(true, presentation.sdJwtVc.getAttributeValue("over_18").jsonPrimitive.boolean)
    }

    @Test
    @Ignore
    fun testParseSection6Example1() {
        // This test checks we can parse the SD-JWT at the end of section 6.1
        //
        //  https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-08.html#name-example-1-sd-jwt
        //

        // TODO: this currently fails - looks like it's because
        //  - we assume that vct is always present in an SD-JWT (it's not present in that example)
        //  - we don't properly handle array entries in disclosures

        val sdJwt = SdJwtVerifiableCredential.fromString(
            "eyJhbGciOiAiRVMyNTYiLCAidHlwIjogImV4YW1wbGUrc2Qtand0In0.eyJfc2QiOiBb" +
                    "IkNyUWU3UzVrcUJBSHQtbk1ZWGdjNmJkdDJTSDVhVFkxc1VfTS1QZ2tqUEkiLCAiSnpZ" +
                    "akg0c3ZsaUgwUjNQeUVNZmVadTZKdDY5dTVxZWhabzdGN0VQWWxTRSIsICJQb3JGYnBL" +
                    "dVZ1Nnh5bUphZ3ZrRnNGWEFiUm9jMkpHbEFVQTJCQTRvN2NJIiwgIlRHZjRvTGJnd2Q1" +
                    "SlFhSHlLVlFaVTlVZEdFMHc1cnREc3JaemZVYW9tTG8iLCAiWFFfM2tQS3QxWHlYN0tB" +
                    "TmtxVlI2eVoyVmE1TnJQSXZQWWJ5TXZSS0JNTSIsICJYekZyendzY002R242Q0pEYzZ2" +
                    "Vks4QmtNbmZHOHZPU0tmcFBJWmRBZmRFIiwgImdiT3NJNEVkcTJ4Mkt3LXc1d1BFemFr" +
                    "b2I5aFYxY1JEMEFUTjNvUUw5Sk0iLCAianN1OXlWdWx3UVFsaEZsTV8zSmx6TWFTRnpn" +
                    "bGhRRzBEcGZheVF3TFVLNCJdLCAiaXNzIjogImh0dHBzOi8vaXNzdWVyLmV4YW1wbGUu" +
                    "Y29tIiwgImlhdCI6IDE2ODMwMDAwMDAsICJleHAiOiAxODgzMDAwMDAwLCAic3ViIjog" +
                    "InVzZXJfNDIiLCAibmF0aW9uYWxpdGllcyI6IFt7Ii4uLiI6ICJwRm5kamtaX1ZDem15" +
                    "VGE2VWpsWm8zZGgta284YUlLUWM5RGxHemhhVllvIn0sIHsiLi4uIjogIjdDZjZKa1B1" +
                    "ZHJ5M2xjYndIZ2VaOGtoQXYxVTFPU2xlclAwVmtCSnJXWjAifV0sICJfc2RfYWxnIjog" +
                    "InNoYS0yNTYiLCAiY25mIjogeyJqd2siOiB7Imt0eSI6ICJFQyIsICJjcnYiOiAiUC0y" +
                    "NTYiLCAieCI6ICJUQ0FFUjE5WnZ1M09IRjRqNFc0dmZTVm9ISVAxSUxpbERsczd2Q2VH" +
                    "ZW1jIiwgInkiOiAiWnhqaVdXYlpNUUdIVldLVlE0aGJTSWlyc1ZmdWVjQ0U2dDRqVDlG" +
                    "MkhaUSJ9fX0.RIOaMsGF0sU1W7vmSHgN6P_70Ziz0c0p1GKgJt2-T23YRDLVulwWMScb" +
                    "xLoIno6Aae3i7KuCjCP3hIlI2sVpKw~WyIyR0xDNDJzS1F2ZUNmR2ZyeU5STjl3IiwgI" +
                    "mdpdmVuX25hbWUiLCAiSm9obiJd~WyJlbHVWNU9nM2dTTklJOEVZbnN4QV9BIiwgImZh" +
                    "bWlseV9uYW1lIiwgIkRvZSJd~WyI2SWo3dE0tYTVpVlBHYm9TNXRtdlZBIiwgImVtYWl" +
                    "sIiwgImpvaG5kb2VAZXhhbXBsZS5jb20iXQ~WyJlSThaV205UW5LUHBOUGVOZW5IZGhR" +
                    "IiwgInBob25lX251bWJlciIsICIrMS0yMDItNTU1LTAxMDEiXQ~WyJRZ19PNjR6cUF4Z" +
                    "TQxMmExMDhpcm9BIiwgInBob25lX251bWJlcl92ZXJpZmllZCIsIHRydWVd~WyJBSngt" +
                    "MDk1VlBycFR0TjRRTU9xUk9BIiwgImFkZHJlc3MiLCB7InN0cmVldF9hZGRyZXNzIjog" +
                    "IjEyMyBNYWluIFN0IiwgImxvY2FsaXR5IjogIkFueXRvd24iLCAicmVnaW9uIjogIkFu" +
                    "eXN0YXRlIiwgImNvdW50cnkiOiAiVVMifV0~WyJQYzMzSk0yTGNoY1VfbEhnZ3ZfdWZR" +
                    "IiwgImJpcnRoZGF0ZSIsICIxOTQwLTAxLTAxIl0~WyJHMDJOU3JRZmpGWFE3SW8wOXN5" +
                    "YWpBIiwgInVwZGF0ZWRfYXQiLCAxNTcwMDAwMDAwXQ~WyJsa2x4RjVqTVlsR1RQVW92T" +
                    "U5JdkNBIiwgIlVTIl0~WyJuUHVvUW5rUkZxM0JJZUFtN0FuWEZBIiwgIkRFIl0~")

    }
}
