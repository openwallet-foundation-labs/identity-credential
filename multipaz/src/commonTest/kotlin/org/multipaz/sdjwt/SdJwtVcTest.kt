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
import org.multipaz.util.Logger
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

    @Test
    fun testParseExampleA3() {
        val issuedSdjwt = """
eyJhbGciOiAiRVMyNTYiLCAidHlwIjogImRjK3NkLWp3dCJ9.eyJfc2QiOiBbIjBIWm1
uU0lQejMzN2tTV2U3QzM0bC0tODhnekppLWVCSjJWel9ISndBVGciLCAiMUNybjAzV21
VZVJXcDR6d1B2dkNLWGw5WmFRcC1jZFFWX2dIZGFHU1dvdyIsICIycjAwOWR6dkh1VnJ
XclJYVDVrSk1tSG5xRUhIbldlME1MVlp3OFBBVEI4IiwgIjZaTklTRHN0NjJ5bWxyT0F
rYWRqZEQ1WnVsVDVBMjk5Sjc4U0xoTV9fT3MiLCAiNzhqZzc3LUdZQmVYOElRZm9FTFB
5TDBEWVBkbWZabzBKZ1ZpVjBfbEtDTSIsICI5MENUOEFhQlBibjVYOG5SWGtlc2p1MWk
wQnFoV3FaM3dxRDRqRi1xREdrIiwgIkkwMGZjRlVvRFhDdWNwNXl5MnVqcVBzc0RWR2F
XTmlVbGlOel9hd0QwZ2MiLCAiS2pBWGdBQTlONVdIRUR0UkloNHU1TW4xWnNXaXhoaFd
BaVgtQTRRaXdnQSIsICJMYWk2SVU2ZDdHUWFnWFI3QXZHVHJuWGdTbGQzejhFSWdfZnY
zZk9aMVdnIiwgIkxlemphYlJxaVpPWHpFWW1WWmY4Uk1pOXhBa2QzX00xTFo4VTdFNHM
zdTQiLCAiUlR6M3FUbUZOSGJwV3JyT01aUzQxRjQ3NGtGcVJ2M3ZJUHF0aDZQVWhsTSI
sICJXMTRYSGJVZmZ6dVc0SUZNanBTVGIxbWVsV3hVV2Y0Tl9vMmxka2tJcWM4IiwgIld
UcEk3UmNNM2d4WnJ1UnBYemV6U2JrYk9yOTNQVkZ2V3g4d29KM2oxY0UiLCAiX29oSlZ
JUUlCc1U0dXBkTlM0X3c0S2IxTUhxSjBMOXFMR3NoV3E2SlhRcyIsICJ5NTBjemMwSVN
DaHlfYnNiYTFkTW9VdUFPUTVBTW1PU2ZHb0VlODF2MUZVIl0sICJpc3MiOiAiaHR0cHM
6Ly9waWQtaXNzdWVyLmJ1bmQuZGUuZXhhbXBsZSIsICJpYXQiOiAxNjgzMDAwMDAwLCA
iZXhwIjogMTg4MzAwMDAwMCwgInZjdCI6ICJ1cm46ZXVkaTpwaWQ6ZGU6MSIsICJfc2R
fYWxnIjogInNoYS0yNTYiLCAiY25mIjogeyJqd2siOiB7Imt0eSI6ICJFQyIsICJjcnY
iOiAiUC0yNTYiLCAieCI6ICJUQ0FFUjE5WnZ1M09IRjRqNFc0dmZTVm9ISVAxSUxpbER
sczd2Q2VHZW1jIiwgInkiOiAiWnhqaVdXYlpNUUdIVldLVlE0aGJTSWlyc1ZmdWVjQ0U
2dDRqVDlGMkhaUSJ9fX0.jZdt97QHNbJlmVF-2B1ZDc0HbkZW8QkO2aId2dL3LaZMSlF
3axe8V8lXH4eC3F8WI0-_Zb7SpPTnhaXQoP9AVQ~WyIyR0xDNDJzS1F2ZUNmR2ZyeU5S
Tjl3IiwgImdpdmVuX25hbWUiLCAiRXJpa2EiXQ~WyJlbHVWNU9nM2dTTklJOEVZbnN4Q
V9BIiwgImZhbWlseV9uYW1lIiwgIk11c3Rlcm1hbm4iXQ~WyI2SWo3dE0tYTVpVlBHYm
9TNXRtdlZBIiwgImJpcnRoZGF0ZSIsICIxOTYzLTA4LTEyIl0~WyJlSThaV205UW5LUH
BOUGVOZW5IZGhRIiwgInN0cmVldF9hZGRyZXNzIiwgIkhlaWRlc3RyYVx1MDBkZmUgMT
ciXQ~WyJRZ19PNjR6cUF4ZTQxMmExMDhpcm9BIiwgImxvY2FsaXR5IiwgIktcdTAwZjZ
sbiJd~WyJBSngtMDk1VlBycFR0TjRRTU9xUk9BIiwgInBvc3RhbF9jb2RlIiwgIjUxMT
Q3Il0~WyJQYzMzSk0yTGNoY1VfbEhnZ3ZfdWZRIiwgImNvdW50cnkiLCAiREUiXQ~WyJ
HMDJOU3JRZmpGWFE3SW8wOXN5YWpBIiwgImFkZHJlc3MiLCB7Il9zZCI6IFsiQUxaRVJ
zU241V05pRVhkQ2tzVzhJNXFRdzNfTnBBblJxcFNBWkR1ZGd3OCIsICJEX19XX3VZY3Z
SejN0dlVuSUp2QkRIaVRjN0NfX3FIZDB4Tkt3SXNfdzlrIiwgImVCcENYVTFKNWRoSDJ
nNHQ4UVlOVzVFeFM5QXhVVmJsVW9kb0xZb1BobzAiLCAieE9QeTktZ0pBTEs2VWJXS0Z
MUjg1Y09CeVVEM0FiTndGZzNJM1lmUUVfSSJdfV0~WyJsa2x4RjVqTVlsR1RQVW92TU5
JdkNBIiwgIm5hdGlvbmFsaXRpZXMiLCBbIkRFIl1d~WyJuUHVvUW5rUkZxM0JJZUFtN0
FuWEZBIiwgInNleCIsIDJd~WyI1YlBzMUlxdVpOYTBoa2FGenp6Wk53IiwgImJpcnRoX
2ZhbWlseV9uYW1lIiwgIkdhYmxlciJd~WyI1YTJXMF9OcmxFWnpmcW1rXzdQcS13Iiwg
ImxvY2FsaXR5IiwgIkJlcmxpbiJd~WyJ5MXNWVTV3ZGZKYWhWZGd3UGdTN1JRIiwgImN
vdW50cnkiLCAiREUiXQ~WyJIYlE0WDhzclZXM1FEeG5JSmRxeU9BIiwgInBsYWNlX29m
X2JpcnRoIiwgeyJfc2QiOiBbIktVVmlhYUxuWTVqU01MOTBHMjlPT0xFTlBiYlhmaFNq
U1BNalphR2t4QUUiLCAiWWJzVDBTNzZWcVhDVnNkMWpVU2x3S1BEZ21BTGVCMXVaY2xG
SFhmLVVTUSJdfV0~WyJDOUdTb3VqdmlKcXVFZ1lmb2pDYjFBIiwgIjEyIiwgdHJ1ZV0~
WyJreDVrRjE3Vi14MEptd1V4OXZndnR3IiwgIjE0IiwgdHJ1ZV0~WyJIM28xdXN3UDc2
MEZpMnllR2RWQ0VRIiwgIjE2IiwgdHJ1ZV0~WyJPQktsVFZsdkxnLUFkd3FZR2JQOFpB
IiwgIjE4IiwgdHJ1ZV0~WyJNMEpiNTd0NDF1YnJrU3V5ckRUM3hBIiwgIjIxIiwgdHJ1
ZV0~WyJEc210S05ncFY0ZEFIcGpyY2Fvc0F3IiwgIjY1IiwgZmFsc2Vd~WyJlSzVvNXB
IZmd1cFBwbHRqMXFoQUp3IiwgImFnZV9lcXVhbF9vcl9vdmVyIiwgeyJfc2QiOiBbIjF
0RWl5elBSWU9Lc2Y3U3NZR01nUFpLc09UMWxRWlJ4SFhBMHI1X0J3a2siLCAiQ1ZLbmx
5NVA5MHlKczNFd3R4UWlPdFVjemFYQ1lOQTRJY3pSYW9ock1EZyIsICJhNDQtZzJHcjh
fM0FtSncyWFo4a0kxeTBRel96ZTlpT2NXMlczUkxwWEdnIiwgImdrdnkwRnV2QkJ2ajB
oczJaTnd4Y3FPbGY4bXUyLWtDRTctTmIyUXh1QlUiLCAiaHJZNEhubUY1YjVKd0M5ZVR
6YUZDVWNlSVFBYUlkaHJxVVhRTkNXYmZaSSIsICJ5NlNGclZGUnlxNTBJYlJKdmlUWnF
xalFXejB0TGl1Q21NZU8wS3FhekdJIl19XQ~WyJqN0FEZGIwVVZiMExpMGNpUGNQMGV3
IiwgImFnZV9pbl95ZWFycyIsIDYyXQ~WyJXcHhKckZ1WDh1U2kycDRodDA5anZ3IiwgI
mFnZV9iaXJ0aF95ZWFyIiwgMTk2M10~WyJhdFNtRkFDWU1iSlZLRDA1bzNKZ3RRIiwgI
mlzc3VhbmNlX2RhdGUiLCAiMjAyMC0wMy0xMSJd~WyI0S3lSMzJvSVp0LXprV3ZGcWJV
TEtnIiwgImV4cGlyeV9kYXRlIiwgIjIwMzAtMDMtMTIiXQ~WyJjaEJDc3loeWgtSjg2S
S1hd1FEaUNRIiwgImlzc3VpbmdfYXV0aG9yaXR5IiwgIkRFIl0~WyJmbE5QMW5jTXo5T
GctYzlxTUl6XzlnIiwgImlzc3VpbmdfY291bnRyeSIsICJERSJd~
        """.replace("\\s".toRegex(), "")

        val sdjwt = SdJwtVerifiableCredential.fromString(issuedSdjwt)
        for (d in sdjwt.disclosures) {
            println("${d.key}: ${d.value}")
        }
    }

    @Test
    fun bdrTest() {
        val sdjwtStr =
            """
eyJ4NWMiOlsiTUlJQzR6Q0NBb21nQXdJQkFnSUJEakFLQmdncWhrak9QUVFEQWpCak1Rc3dDUVlEVlFRR0V3SkVSVEVQTUEwR0ExVUVCd3dHUW1WeWJHbHVNUjB3R3dZRFZRUUtEQlJDZFc1a1pYTmtjblZqYTJWeVpXa2dSMjFpU0RFS01BZ0dBMVVFQ3d3QlNURVlNQllHQTFVRUF3d1BTVVIxYm1sdmJpQlVaWE4wSUVOQk1CNFhEVEkxTURFeU56RTFOVGswTWxvWERUSTJNRE13TXpFMU5UazBNbG93VGpFTE1Ba0dBMVVFQmhNQ1JFVXhIVEFiQmdOVkJBb01GRUoxYm1SbGMyUnlkV05yWlhKbGFTQkhiV0pJTVFvd0NBWURWUVFMREFGSk1SUXdFZ1lEVlFRRERBdFVaWE4wSUVsemMzVmxjakJaTUJNR0J5cUdTTTQ5QWdFR0NDcUdTTTQ5QXdFSEEwSUFCR2NFN0pPVU92VUwwYmZ0eXdzSEc0UWJKcDZ3c0ZhZ3E4NUpScmlXbUxzS1pjc1haS0I0QU52cW1YcUxocjJYN0JnS2ExOERCSEw3bllTMk9ONXlHdVdqZ2dGQk1JSUJQVEFkQmdOVkhRNEVGZ1FVNlR3dDV5eWNmMWpLOE1GQlpRbU5JbWR2Y3M0d0RBWURWUjBUQVFIL0JBSXdBREFPQmdOVkhROEJBZjhFQkFNQ0I0QXdnZHdHQTFVZEVRU0IxRENCMFlJVFpHVnRieTVpWkhJdGNISnZkRzkwZVhCbGM0WTBhSFIwY0hNNkx5OWtaVzF2TG1Ka2NpMXdjbTkwYjNSNWNHVnpMMmx6YzNWbGNpOTFibWwyWlhKemFYUjVibVYwZDI5eWE0WW1hSFIwY0hNNkx5OWtaVzF2TG1Ka2NpMXdjbTkwYjNSNWNHVnpMMmx6YzNWbGNpOXdhV1NHTEdoMGRIQnpPaTh2WkdWdGJ5NWlaSEl0Y0hKdmRHOTBlWEJsY3k5cGMzTjFaWEl2WW5WdVpHVnpZVzEwaGk1b2RIUndjem92TDJSbGJXOHVZbVJ5TFhCeWIzUnZkSGx3WlhNdmFYTnpkV1Z5TDJGeVltVnBkR2RsWW1WeU1COEdBMVVkSXdRWU1CYUFGRStXNno3YWpUdW1leCtZY0Zib05yVmVDMnRSTUFvR0NDcUdTTTQ5QkFNQ0EwZ0FNRVVDSUFHVDE4RTRRdThhT012MWI5V1dYMmlNM2drWlRSck14MlB4RzRxYTREeWhBaUVBeFVTTmUrdVNzTkJCSXh3b2I2K0RKMnVjN21USnp5aGlJQ2ZaR0J4MjNPOD0iLCJNSUlDTFRDQ0FkU2dBd0lCQWdJVU1ZVUhoR0Q5aFUvYzBFbzZtVzhyamplSit0MHdDZ1lJS29aSXpqMEVBd0l3WXpFTE1Ba0dBMVVFQmhNQ1JFVXhEekFOQmdOVkJBY01Ca0psY214cGJqRWRNQnNHQTFVRUNnd1VRblZ1WkdWelpISjFZMnRsY21WcElFZHRZa2d4Q2pBSUJnTlZCQXNNQVVreEdEQVdCZ05WQkFNTUQwbEVkVzVwYjI0Z1ZHVnpkQ0JEUVRBZUZ3MHlNekEzTVRNd09USTFNamhhRncwek16QTNNVEF3T1RJMU1qaGFNR014Q3pBSkJnTlZCQVlUQWtSRk1ROHdEUVlEVlFRSERBWkNaWEpzYVc0eEhUQWJCZ05WQkFvTUZFSjFibVJsYzJSeWRXTnJaWEpsYVNCSGJXSklNUW93Q0FZRFZRUUxEQUZKTVJnd0ZnWURWUVFEREE5SlJIVnVhVzl1SUZSbGMzUWdRMEV3V1RBVEJnY3Foa2pPUFFJQkJnZ3Foa2pPUFFNQkJ3TkNBQVNFSHo4WWpyRnlUTkhHTHZPMTRFQXhtOXloOGJLT2drVXpZV2NDMWN2ckpuNUpnSFlITXhaYk5NTzEzRWgwRXIyNzM4UVFPZ2VSb1pNSVRhb2RrZk5TbzJZd1pEQWRCZ05WSFE0RUZnUVVUNWJyUHRxTk82WjdINWh3VnVnMnRWNExhMUV3SHdZRFZSMGpCQmd3Rm9BVVQ1YnJQdHFOTzZaN0g1aHdWdWcydFY0TGExRXdFZ1lEVlIwVEFRSC9CQWd3QmdFQi93SUJBREFPQmdOVkhROEJBZjhFQkFNQ0FZWXdDZ1lJS29aSXpqMEVBd0lEUndBd1JBSWdZMERlcmRDeHQ0ekdQWW44eU5yRHhJV0NKSHB6cTRCZGpkc1ZOMm8xR1JVQ0lCMEtBN2JHMUZWQjFJaUs4ZDU3UUFMK1BHOVg1bGRLRzdFa29BbWhXVktlIl0sImtpZCI6ImJjMGRlZGE0NTU1NGVjYzNlZTQzNjA5YmEyMDc4MzIwMWY5NGEwOTYiLCJ0eXAiOiJkYytzZC1qd3QiLCJhbGciOiJFUzI1NiJ9.eyJfc2QiOlsiODQ5T1hCSnktcHZfNTk4UVhWcVFmVEp3X0tIdjlzOGdna25VMzBVaDZjayIsIlB6cTRsLUdBU2tlTV9kM09tS1lnQ0tUelU4T1FlNjZZbnFKal9XcUtPTGMiLCJiX3FLZXRVT3ppRXdPRl9LYVEyR0xoNXo5d2huYUVjNmtFdEFDdHlqSnZ3IiwiakVhd1FHRUVjU2RJWDRWbUtRS0J1dHQxRWJPVGc0QS1wcXJVbXlvOHFsRSIsImphSUpMSDc5MTYySVEydmZONTRBZ011VWxCNmx0OV80N1NsczlwUDUtQWsiLCJubEl4OXQxOXlrcEkxSm1pTF9mcmV4X0xVTnpMcEFUYXVZX0VXd09ES2ljIiwicDlXbTQxLUNsTy1acXNSVnNucnNUc3JlUGpoWGRqMnJUbFdJa2dqOUNsMCIsInZqZVFoSmJfU1J6NXY3TjY0NEd1bkczTkZQRURRY3RLZFQwN215ZjVLRzgiXSwidmN0IjoidXJuOmV1ZGk6cGlkOjEiLCJfc2RfYWxnIjoic2hhLTI1NiIsImlzcyI6Imh0dHBzOi8vZGVtby5iZHItcHJvdG90eXBlcy9pc3N1ZXIvcGlkIiwiY25mIjp7Imp3ayI6eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2IiwieCI6IjRsbElYWVpJLVIzSWppMm5wRDAwWmdZc3gtQmtKamY4bnFSeFVrRndDUTAiLCJ5IjoiVlRBMFdRSFo1el96NDJvQ2RFWHdCakYxblpUdHdyUnBYODNMazgweTE0ZyJ9fSwiZXhwIjoxNzUzNzg3NjY1LCJpYXQiOjE3NDYwMTE2NjUsImFnZV9lcXVhbF9vcl9vdmVyIjp7Il9zZCI6WyI4c2ZoOFJPaHlRV00tcmR6el9pb2xBYnZZY21ncFdjRHFNdUVSWnZ0NTFjIiwiVkFvc0czU2ZYb0hmUFVWTmgtaGlCRlNIbnl4MWRoM1FzZm9xcURwcS1aRSJdfX0.5KKKPiTNxNDzagKzYnaolyRciOZgOHFwub33BUWetUt8UatNQcX3nc87lRR6X1hzFY3p5gKWyP0BAtTX_mZyGA~WyIwSmRWenhqT1BmcGdETU9KOUNSWkxRIiwiZmFtaWx5X25hbWUiLCJNVVNURVJNQU5OIl0~WyI5UWFPdGVra193ZmxzZkFheFRNWVp3IiwiZ2l2ZW5fbmFtZSIsIkVSSUtBIl0~WyJQMzVoUEtIWlZ0bTVacnp3MTMwV0RBIiwiYmlydGhkYXRlIiwiMjAwNi0wMi0yOCJd~WyJXeExqYVZ2eHozem1NUkl6bm1SbVRBIiwiY291bnRyeSIsIkJFUkxJTiJd~WyJWdjM4ZVM3YTB1aXJCbWEydDAwaDh3IiwibG9jYWxpdHkiLCJCRVJMSU4iXQ~WyJNVGVhZVZkVmVKa243Tm1GOFNSdU53IiwicmVnaW9uIiwiQkVSTElOIl0~WyJRSkt6WktUVldqaHo4dFhuVWFGZTJBIiwicGxhY2Vfb2ZfYmlydGgiLHsiX3NkIjpbIjVMNkJiUGIxU2tGSWdvQUZ6VXFzRGJqeHVzN3V0RFVPUHgwZ3FadXVLYmMiLCJIanBVd3pjcXNCeEgtUFdmRnJqc2x0UzVmM3pWUExGU1liZjNPYUZ1OGprIiwiTmtkTEJzX180M0ROSDNKd09UNll2UDhxbUc1em9OMDJBbHZNbmJTcDF6byJdfV0~WyJlVm9qMTRPQ1pPN0VaYVZGcHQybTRRIiwibmF0aW9uYWxpdGllcyIsW11d~WyJUbTVITUY0cmhhY0ltNV91WGxKbkhnIiwiZGF0ZV9vZl9leHBpcnkiLCIyMDI2LTEyLTMxIl0~WyIxTURKcDhZV193Qnp1QktNa0hKSExBIiwiMTgiLHRydWVd~WyI5QVRkUldEdGVuVnNiMF85MWRueENnIiwiMjEiLGZhbHNlXQ~WyJmMFItM0xSMzA0bG45WXVKeVlnUGRRIiwiaXNzdWluZ19hdXRob3JpdHkiLCJERSJd~WyJhQlpCd3hyT1Rjd0JZemVDeDRlenpRIiwiaXNzdWluZ19jb3VudHJ5IiwiREUiXQ~                
            """.trimIndent()
        val sdjwt = SdJwtVerifiableCredential.fromString(sdjwtStr)
        val obj = sdjwt.getDisclosed()
        Logger.iJson("TAG", "disclosed", obj)
        for (d in sdjwt.disclosures) {
            println("${d.key}: ${d.value}")
        }
    }
}
