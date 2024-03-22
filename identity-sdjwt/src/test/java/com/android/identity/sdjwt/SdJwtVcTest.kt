package com.android.identity.sdjwt

import com.android.identity.credential.AuthenticationKey
import com.android.identity.credential.Credential
import com.android.identity.credential.CredentialStore
import com.android.identity.credential.PendingAuthenticationKey
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.Certificate
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcCurve
import com.android.identity.sdjwt.SdJwtVerifiableCredential.AttributeNotDisclosedException
import com.android.identity.sdjwt.presentation.SdJwtVerifiablePresentation
import com.android.identity.sdjwt.util.JsonWebKey
import com.android.identity.securearea.KeyPurpose
import com.android.identity.securearea.SecureAreaRepository
import com.android.identity.securearea.software.SoftwareCreateKeySettings
import com.android.identity.securearea.software.SoftwareSecureArea
import com.android.identity.storage.EphemeralStorageEngine
import com.android.identity.util.Timestamp
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.Security
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class SdJwtVcTest {

    private lateinit var secureArea: SoftwareSecureArea
    private lateinit var secureAreaRepository: SecureAreaRepository
    private lateinit var storageEngine: EphemeralStorageEngine

    private lateinit var credential: Credential
    private lateinit var timeValidityBegin: Instant
    private lateinit var timeSigned: Instant
    private lateinit var timeValidityEnd: Instant
    private lateinit var issuerCert: Certificate
    private lateinit var authKey: AuthenticationKey

    @Before
    fun setup() {
        Security.insertProviderAt(BouncyCastleProvider(), 1)
        storageEngine = EphemeralStorageEngine()
        secureAreaRepository = SecureAreaRepository()
        secureArea = SoftwareSecureArea(storageEngine)
        secureAreaRepository.addImplementation(secureArea)
        provisionCredential()
    }

    private fun provisionCredential() {
        val credentialStore = CredentialStore(
            storageEngine,
            secureAreaRepository
        )

        // Create the credential on the holder device...
        credential = credentialStore.createCredential(
            "testCredential",
        )

        // Create an authentication key...
        timeSigned = Clock.System.now()
        timeValidityBegin = timeSigned.plus(1.hours)
        timeValidityEnd = timeSigned.plus(10.days)
        val pendingAuthKey: PendingAuthenticationKey = credential.createPendingAuthenticationKey(
            "domain",
            secureArea,
            SoftwareCreateKeySettings.Builder(ByteArray(0))
                .setKeyPurposes(setOf(KeyPurpose.SIGN, KeyPurpose.AGREE_KEY))
                .build(),
            null
        )

        // at the issuer, start creating the credential...
        val identityAttributes = buildJsonObject {
            put("name", "Elisa Beckett")
            put("given_name", "Elisa")
            put("family_name", "Becket")
            put("over_18", true)
            put("over_21", true)
            put("address", buildJsonObject {
                put("street_address",  "123 Main St")
                put("locality", "Anytown")
                put("region",  "Anystate")
                put("country", "US")
            })
        }

        val issuerKey = Crypto.createEcPrivateKey(EcCurve.P256)
        val validFrom = Clock.System.now()
        val validUntil = Instant.fromEpochMilliseconds(
            validFrom.toEpochMilliseconds() + 5L * 365 * 24 * 60 * 60 * 1000
        )
        issuerCert = Crypto.createX509v3Certificate(
            issuerKey.publicKey,
            issuerKey,
            null,
            Algorithm.ES256,
            "1",
            "CN=State Of Utopia",
            "CN=State Of Utopia",
            validFrom,
            validUntil, setOf(), listOf()
        )


        // Issuer knows that it will use ECDSA with SHA-256.
        // Public keys and cert chains will be at https://example-issuer.com/...
        val sdJwtVcGenerator = SdJwtVcGenerator(
            random = Random(42),
            payload = identityAttributes,
            issuer = Issuer("https://example-issuer.com", Algorithm.ES256, "key-1")
        )

        sdJwtVcGenerator.publicKey = JsonWebKey(pendingAuthKey.attestation.certificates[0].publicKey)
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

        authKey = pendingAuthKey.certify(
            sdJwt.toString().toByteArray(),
            Timestamp.ofEpochMilli(timeValidityBegin.toEpochMilliseconds()),
            Timestamp.ofEpochMilli(timeValidityEnd.toEpochMilliseconds())
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun testPresentationVerificationEcdsa() {

        // on the holder device, let's prepare a presentation
        // we'll start by reading back the SD-JWT issued to us for the auth key
        val sdJwt = SdJwtVerifiableCredential.fromString(
            String(authKey.issuerProvidedData, Charsets.US_ASCII))

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
        val nonce: ByteArray = "some-example-nonce".toByteArray()
        val nonceStr = Base64.UrlSafe.encode(nonce)

        // time to create the presentation (on the holder device) for the verifier:
        val presentationString = filteredSdJwt.createPresentation(
            authKey.secureArea,
            authKey.alias,
            null,
            Algorithm.ES256,
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
        presentation.sdJwtVc.verifyIssuerSignature(issuerCert.publicKey)

        // at this point, the verifier could read out the attributes they were
        // interested in:
        assertEquals("Elisa Beckett", presentation.sdJwtVc.getAttributeValue("name").jsonPrimitive.content)
        assertEquals(true, presentation.sdJwtVc.getAttributeValue("over_18").jsonPrimitive.boolean)
    }

    @Test
    fun testVector() {
        // https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-08.html#name-example-1-sd-jwt

    }
}
