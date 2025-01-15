/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.identity.android.direct_access

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.icu.util.Calendar
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.identity.R
import com.android.identity.android.mdoc.deviceretrieval.IsoDepWrapper
import com.android.identity.android.mdoc.deviceretrieval.VerificationHelper
import com.android.identity.android.mdoc.transport.DataTransportOptions
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.android.storage.AndroidStorageEngine
import com.android.identity.cbor.Bstr
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.Tagged
import com.android.identity.cbor.Tstr
import com.android.identity.cbor.toDataItem
import com.android.identity.cose.Cose
import com.android.identity.cose.Cose.coseSign1Sign
import com.android.identity.cose.CoseNumberLabel
import com.android.identity.credential.CredentialFactory
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.X509Cert
import com.android.identity.crypto.X509CertChain
import com.android.identity.crypto.toEcPrivateKey
import com.android.identity.direct_access.DirectAccessTransport
import com.android.identity.document.DocumentStore
import com.android.identity.document.NameSpacedData
import com.android.identity.mdoc.connectionmethod.ConnectionMethod
import com.android.identity.mdoc.connectionmethod.ConnectionMethod.Companion.disambiguate
import com.android.identity.mdoc.mso.MobileSecurityObjectGenerator
import com.android.identity.mdoc.mso.StaticAuthDataGenerator
import com.android.identity.mdoc.request.DeviceRequestGenerator
import com.android.identity.mdoc.response.DeviceResponseParser
import com.android.identity.mdoc.util.MdocUtil
import com.android.identity.securearea.SecureAreaRepository
import com.android.identity.util.Constants
import com.android.identity.util.Logger
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.io.files.Path
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.After
import org.junit.Assert
import org.junit.Assume.assumeTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.InvalidKeyException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.Security
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.security.auth.x500.X500Principal

@RunWith(AndroidJUnit4::class)
class DirectAccessTest {
    companion object {
        const val TAG = "DirectAccessTest"
        private const val CREDENTIAL_DOMAIN = "da_test"
        private const val DEVICE_CONNECT_STATUS_DISCONNECTED = 0
        private const val DEVICE_CONNECT_STATUS_CONNECTED = 1
        private const val MDL_DOC_TYPE = "org.iso.18013.5.1.mDL"
        private const val MDL_NAMESPACE = "org.iso.18013.5.1"
        private const val AAMVA_NAMESPACE = "org.iso.18013.5.1.aamva"
    }

    private var context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private var transport: DirectAccessTransport = DirectAccessOmapiTransport(context, DirectAccess.DIRECT_ACCESS_PROVISIONING_APPLET_ID)
    // Uncomment if testing via socket is preferred.
    // private var transport = DirectAccessSocketTransport(DirectAccessCredential.DIRECT_ACCESS_PROVISIONING_APPLET_ID)
    private var documentStore: DocumentStore
    private lateinit var readerKeys: ArrayList<KeyPair>
    private lateinit var readerCertChain: X509CertChain

    @Throws(Exception::class)
    private fun generateEcdsaKeyPair(): KeyPair {
        Security.removeProvider("BC")
        Security.addProvider(BouncyCastleProvider())
        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, BouncyCastleProvider.PROVIDER_NAME)
        val ecSpec = ECGenParameterSpec(EcCurve.P256.SECGName)
        kpg.initialize(ecSpec)
        return kpg.generateKeyPair()
    }

    @Throws(Exception::class)
    private fun getReaderCertificateChain(
        readerKey: KeyPair
    ): X509CertChain {
        val certChain: X509CertChain?
        val issuer = X500Principal("CN=SelfSigned, O=Android, C=US")
        val subject = X500Principal("CN=Subject, O=Android, C=US")
        // Make the certificate valid for two days.
        val millisPerDay = (24 * 60 * 60 * 1000).toLong()
        val now = System.currentTimeMillis()
        val start = Date(now - millisPerDay)
        val end = Date(now + millisPerDay)
        val serialBytes = ByteArray(16)
        SecureRandom().nextBytes(serialBytes)
        val serialNumber = BigInteger(1, serialBytes)
        val x509cg = X509v3CertificateBuilder(
            X500Name.getInstance(issuer.encoded),
            serialNumber,
            start,
            end,
            X500Name.getInstance(subject.encoded),
            SubjectPublicKeyInfo.getInstance(readerKey.public.encoded)
        )
        val x509holder: X509CertificateHolder = x509cg.build(
            JcaContentSignerBuilder("SHA256withECDSA")
                .build(readerKey.private)
        )
        val certFactory = CertificateFactory.getInstance("X.509")
        val x509c = certFactory.generateCertificate(
            ByteArrayInputStream(x509holder.encoded)
        ) as X509Certificate
        certChain = X509CertChain(listOf(X509Cert(x509c.encoded)))
        return certChain
    }

    init {
        assumeTrue(DirectAccess(transport).isDirectAccessSupported)

        val storageDir = Path(File(context.dataDir, "ic-testing").path)
        val storageEngine = AndroidStorageEngine.Builder(context, storageDir).build()
        val secureAreaRepository = SecureAreaRepository()
        val secureArea = AndroidKeystoreSecureArea(context, storageEngine)
        secureAreaRepository.addImplementation(secureArea)
        val credentialFactory = CredentialFactory()
        credentialFactory.addCredentialImplementation(DirectAccessCredential::class) {
                document, dataItem -> DirectAccessCredential(document, dataItem)
        }
        documentStore = DocumentStore(storageEngine, secureAreaRepository, credentialFactory, transport)

        // generate reader certs
        try {
            val keyPair = generateEcdsaKeyPair()
            readerKeys = ArrayList()
            readerKeys.add(keyPair)

            readerCertChain = getReaderCertificateChain(keyPair)
        } catch (e: Exception) {
            Assert.fail(e.message)
        }
    }

    @After
    fun reset() {
        if (DirectAccess(transport).isDirectAccessSupported) {
            transport.closeConnection()
            transport.openConnection()

            // Todo uncomment once enumerateAllocatedSlots is implemented
//        // Delete all slots to reset applet state.
//        val directAccess = DirectAccess(transport)
//        for (slot in directAccess.enumerateAllocatedSlots()) {
//            directAccess.clearDocumentSlot(slot)
//        }

            try {
                transport.closeConnection()
            } catch (e: IOException) {
                Assert.fail("Unexpected Exception $e")
            }
        }
    }

    @Ignore("enumerateAllocatedSlots needs to be implemented")
    @Test
    fun testAllocateAndClearDocumentSlot() {
        val directAccess = DirectAccess(transport)

        // First try allocating a single slot.
        Assert.assertEquals(0, directAccess.enumerateAllocatedSlots().size)
        val singleSlot = directAccess.allocateDocumentSlot()
        Assert.assertEquals(1, directAccess.enumerateAllocatedSlots().size)
        Assert.assertEquals(1, directAccess.enumerateAllocatedSlots()[0])

        // And deleting that single slot.
        Assert.assertTrue(directAccess.clearDocumentSlot(singleSlot))
        Assert.assertEquals(0, directAccess.enumerateAllocatedSlots().size)

        // Now allocate slots until -1 is returned, meaning there are no more available slots to be
        // allocated.
        var fullyAllocated = false
        val allocatedSlots = ArrayList<Int>()
        while (!fullyAllocated) {
            val newSlot = directAccess.allocateDocumentSlot()
            if (newSlot == -1) {
                fullyAllocated = true
            } else {
                allocatedSlots.add(newSlot)
            }

            // To ensure the while loop will end even if the applet does not return -1 as expected,
            // stop after 50 slots have been allocated since it is unlikely that any applet will
            // allow for so many slots in practice.
            if (allocatedSlots.size >= 50) {
                // Delete all slots to reset applet state, then throw error.
                for (slot in allocatedSlots) {
                    directAccess.clearDocumentSlot(slot)
                }
                Assert.fail("The applet has allowed for the allocation of 50 slots, " +
                        "indicating it has not correctly implemented allocateDocumentSlot in the " +
                        "event of allocation attempts when full.")
            }
        }

        // Delete all slots to reset applet state.
        for (slot in allocatedSlots) {
            Assert.assertTrue(directAccess.clearDocumentSlot(slot))
        }
    }

    @Test
    fun testProvisioning() {
        val directAccess = DirectAccess(transport)
        val slot = directAccess.allocateDocumentSlot()
        val document = documentStore.createDocument("testMdl")

        val pendingCredential = DirectAccessCredential(
            document,
            null,
            CREDENTIAL_DOMAIN,
            MDL_DOC_TYPE,
            readerCertChain,
            slot
        )
        Assert.assertEquals(1, document.pendingCredentials.size)

        // Certify pending credential.
        val issuerAuthData = createTestIssuerAuthData(
            context,
            pendingCredential,
            MDL_DOC_TYPE
        )
        val validFrom = Clock.System.now()
        pendingCredential.certify(issuerAuthData,
            validFrom,
            Instant.fromEpochMilliseconds(validFrom.toEpochMilliseconds() + 50))
        Assert.assertEquals(1, document.certifiedCredentials.size)

        // Delete credentials and document, and clear slot.
        pendingCredential.delete()
        documentStore.deleteDocument("testMdl")
        directAccess.clearDocumentSlot(slot)
    }

    @Test
    fun testProvisionWithInvalidCredentialData() {
        val directAccess = DirectAccess(transport)
        val slot = directAccess.allocateDocumentSlot()
        val document = documentStore.createDocument("testMdl")

        val pendingCredential = DirectAccessCredential(
            document,
            null,
            CREDENTIAL_DOMAIN,
            MDL_DOC_TYPE,
            readerCertChain,
            slot
        )
        Assert.assertEquals(1, document.pendingCredentials.size)

        // Attempt to certify pending credential.
        val mapIssuerAuthToErrorMsg = mapOf(
            Pair(byteArrayOf(), "Expected to fail when empty issuer auth is passed."),
            Pair("invalid-cred-data".toByteArray(StandardCharsets.UTF_8), "Expected to fail when empty credential data is passed.")
        )
        val validFrom = Clock.System.now()
        for ((invalidIssuerAuth, errorMsg) in mapIssuerAuthToErrorMsg) {
            Assert.assertThrows(errorMsg, IllegalArgumentException::class.java) {
                pendingCredential.certify(
                    invalidIssuerAuth,
                    validFrom,
                    Instant.fromEpochMilliseconds(validFrom.toEpochMilliseconds() + 1000)
                )
            }
        }

        // Delete credential and document, and clear slot.
        pendingCredential.delete()
        documentStore.deleteDocument("testMdl")
        directAccess.clearDocumentSlot(slot)
    }

    @Test
    fun testBasicPresentation() {
        val directAccess = DirectAccess(transport)
        val slot = directAccess.allocateDocumentSlot()
        val document = documentStore.createDocument("testMdl")

        val pendingCredential = DirectAccessCredential(
            document,
            null,
            CREDENTIAL_DOMAIN,
            MDL_DOC_TYPE,
            readerCertChain,
            slot
        )

        val firstName = "Erika"
        val issuerAuthData = createTestIssuerAuthData(
            context,
            pendingCredential,
            MDL_DOC_TYPE,
            firstName
        )
        val validFrom = Clock.System.now()
        pendingCredential.certify(issuerAuthData,
            validFrom,
            Instant.fromEpochMilliseconds(validFrom.toEpochMilliseconds() + 50))
        pendingCredential.setAsActiveCredential()

        mockPresentationAndCheckExpectedFirstName(firstName)

        // Delete credential and document, and clear slot.
        pendingCredential.delete()
        documentStore.deleteDocument("testMdl")
        DirectAccess(transport).clearDocumentSlot(slot)
    }

    @Ignore
    @Test
    fun testAppletStatePostCreateAndCertify() {
        val directAccess = DirectAccess(transport)
        val slot = directAccess.allocateDocumentSlot()
        val document = documentStore.createDocument("testMdl")

        // Create and certify a single credential.
        val erikaCredential = DirectAccessCredential(
            document,
            null,
            CREDENTIAL_DOMAIN,
            MDL_DOC_TYPE,
            readerCertChain,
            slot
        )
        val erikaCredentialFirstName = "Erika"
        var issuerAuthData = createTestIssuerAuthData(
            context,
            erikaCredential,
            MDL_DOC_TYPE,
            erikaCredentialFirstName
        )
        var validFrom = Clock.System.now()
        erikaCredential.certify(issuerAuthData,
            validFrom,
            Instant.fromEpochMilliseconds(validFrom.toEpochMilliseconds() + 100))

        // Set it as active and ensure it's used during presentation.
        erikaCredential.setAsActiveCredential()
        mockPresentationAndCheckExpectedFirstName(erikaCredentialFirstName)

        // Create a second credential and mock a presentation to ensure the active credential has
        // not been changed.
        val janeCredential = DirectAccessCredential(
            document,
            null,
            CREDENTIAL_DOMAIN,
            MDL_DOC_TYPE,
            readerCertChain,
            slot
        )
        mockPresentationAndCheckExpectedFirstName(erikaCredentialFirstName)

        // Certify the second credential and mock a presentation to ensure the active credential has
        // not been changed.
        val janeCredentialFirstName = "Jane"
        issuerAuthData = createTestIssuerAuthData(
            context,
            janeCredential,
            MDL_DOC_TYPE,
            janeCredentialFirstName
        )
        validFrom = Clock.System.now()
        janeCredential.certify(issuerAuthData,
            validFrom,
            Instant.fromEpochMilliseconds(validFrom.toEpochMilliseconds() + 100))
        mockPresentationAndCheckExpectedFirstName(erikaCredentialFirstName)

        // Set the second credential as active and mock a presentation to ensure the active credential
        // has been changed.
        janeCredential.setAsActiveCredential()
        mockPresentationAndCheckExpectedFirstName(janeCredentialFirstName)

        // Delete both credentials and document, and clear slot.
        erikaCredential.delete()
        janeCredential.delete()
        documentStore.deleteDocument("testMdl")
        directAccess.clearDocumentSlot(slot)
    }

    @Ignore("need to implement getCredentialUsageCount + clearCredentialUsageCount")
    @Test
    fun testGetAndClearCredentialUsageCount() {
        // Create and certify a credential, and set it as active
        val directAccess = DirectAccess(transport)
        val slot = directAccess.allocateDocumentSlot()
        val document = documentStore.createDocument("testMdl")
        val credential = DirectAccessCredential(
            document,
            null,
            CREDENTIAL_DOMAIN,
            MDL_DOC_TYPE,
            readerCertChain,
            slot
        )
        val firstName = "Erika"
        val issuerAuthData = createTestIssuerAuthData(
            context,
            credential,
            MDL_DOC_TYPE,
            firstName
        )
        val validFrom = Clock.System.now()
        credential.certify(issuerAuthData,
            validFrom,
            Instant.fromEpochMilliseconds(validFrom.toEpochMilliseconds() + 50))
        credential.setAsActiveCredential()

        // Mock presentation 5 times and assert usage count increases with each presentation.
        var expectedUsageCount = 0
        Assert.assertEquals(expectedUsageCount, directAccess.getCredentialUsageCount(slot))
        for (i in 1..5) {
            mockPresentationAndCheckExpectedFirstName(firstName)
            expectedUsageCount += 1
            Assert.assertEquals(expectedUsageCount, directAccess.getCredentialUsageCount(slot))
        }

        // Post clearing usage count, assert getCredentialUsageCount returns 0
        directAccess.clearCredentialUsageCount(slot)
        expectedUsageCount = 0
        Assert.assertEquals(expectedUsageCount, directAccess.getCredentialUsageCount(slot))

        // Mock another 5 presentations and check usage count
        for (i in 1..5) {
            mockPresentationAndCheckExpectedFirstName(firstName)
            expectedUsageCount += 1
            Assert.assertEquals(expectedUsageCount, directAccess.getCredentialUsageCount(slot))
        }

        // Delete credential and document, and clear slot.
        credential.delete()
        documentStore.deleteDocument("testMdl")
        directAccess.clearDocumentSlot(slot)
    }

    private fun mockPresentationAndCheckExpectedFirstName(expectedFirstName: String) {
        transport.closeConnection()

        // Define helper functions for presentation mocking.
        var countDownLatch: CountDownLatch? = null
        var deviceConnectStatus = 0
        var mError: Throwable? = null
        fun resetLatch() {
            countDownLatch = CountDownLatch(1)
        }
        fun waitForResponse(expectedDeviceConnectionStatus: Int) {
            try {
                countDownLatch!!.await()
            } catch (e: InterruptedException) {
                // do nothing
            }
            Assert.assertEquals(
                "Device connection status ",
                expectedDeviceConnectionStatus.toLong(),
                deviceConnectStatus.toLong()
            )
            if (mError != null) {
                Assert.fail(mError!!.message)
            }
        }
        fun waitForResponse(expectedDeviceConnectionStatus: Int, timeInSeconds: Long) {
            try {
                countDownLatch!!.await(timeInSeconds, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                // do nothing
            }
        }

        // Create the VerificationHelper.
        var mConnectionMethods: List<ConnectionMethod>? = null
        var deviceResponse: ByteArray? = null
        val mResponseListener: VerificationHelper.Listener = object : VerificationHelper.Listener {
            override fun onDeviceEngagementReceived(connectionMethods: List<ConnectionMethod>) {
                mConnectionMethods = disambiguate(connectionMethods)
                countDownLatch!!.countDown()
            }

            override fun onReaderEngagementReady(readerEngagement: ByteArray) {}
            override fun onMoveIntoNfcField() {}
            override fun onDeviceConnected() {
                deviceConnectStatus = DEVICE_CONNECT_STATUS_CONNECTED
            }

            override fun onDeviceDisconnected(transportSpecificTermination: Boolean) {
                deviceConnectStatus = DEVICE_CONNECT_STATUS_DISCONNECTED
                countDownLatch!!.countDown()
            }

            override fun onResponseReceived(deviceResponseBytes: ByteArray) {
                deviceResponse = deviceResponseBytes
                countDownLatch!!.countDown()
            }

            override fun onError(error: Throwable) {
                mError = error
                countDownLatch!!.countDown()
            }
        }
        val builder = VerificationHelper.Builder(
            context, mResponseListener,
            context.mainExecutor
        )
        val options = DataTransportOptions.Builder().setBleClearCache(false)
            .setBleClearCache(false).build()
        builder.setDataTransportOptions(options)
        val verificationHelper = builder.build()

        val wrapper: IsoDepWrapper = ShadowIsoDep(transport)
        resetLatch()
        verificationHelper.mockTagDiscovered(wrapper)
        // Wait till the device engagement is received.
        waitForResponse(DEVICE_CONNECT_STATUS_DISCONNECTED)
        Assert.assertNotNull(mConnectionMethods)
        Assert.assertTrue(mConnectionMethods!!.size > 0)
        verificationHelper.connect(mConnectionMethods!![0])
        var devReq: ByteArray? = null
        val entries = arrayOf(
            "sex", "portrait", "given_name", "issue_date",
            "expiry_date", "family_name", "document_number", "issuing_authority"
        )
        try {
            devReq = createMdocRequest(
                readerKeys[0],
                readerCertChain,
                entries,
                verificationHelper.sessionTranscript
            )
        } catch (e: Exception) {
            Assert.fail(e.message)
        }
        resetLatch()
        verificationHelper.sendRequest(devReq!!)
        // Wait till the mdoc response is received.
        waitForResponse(DEVICE_CONNECT_STATUS_CONNECTED)
        Assert.assertNotNull(deviceResponse)
        val parser = DeviceResponseParser(deviceResponse!!, verificationHelper.sessionTranscript)
        parser.setEphemeralReaderKey(verificationHelper.eReaderKey)
        val dr = parser.parse()
        Assert.assertNotNull(dr)
        Assert.assertEquals(Constants.DEVICE_RESPONSE_STATUS_OK, dr.status)
        Assert.assertNotNull(dr.documents)
        Assert.assertTrue(dr.documents.isNotEmpty())
        val documentList: List<DeviceResponseParser.Document> = dr.documents
        // Confirm all fields are present.
        for (doc in documentList) {
            for (eleId in entries) {
                doc.getIssuerEntryData(MDL_NAMESPACE, eleId)
            }
        }
        // Confirm given_name matches firstName.
        val mDl = dr.documents.find { it.docType == MDL_DOC_TYPE }
        Assert.assertNotNull(mDl)
        Assert.assertEquals(expectedFirstName, mDl!!.getIssuerEntryString(MDL_NAMESPACE, "given_name"))

        // Reset state of helper objects.
        resetLatch()
        verificationHelper.disconnect()
        waitForResponse(DEVICE_CONNECT_STATUS_DISCONNECTED, 60 /* 1 minute */)
        Logger.d(TAG, "finished mock presentation")
    }

    private fun createTestIssuerAuthData(
        context: Context,
        credential: DirectAccessCredential,
        docType: String,
        firstName: String = "Erika"
    ): ByteArray {
        return try {
            val issuerKeypair = generateEcdsaKeyPair()
            val issuer =
                X500Name("CN=State Of Utopia")
            val subject =
                X500Name("CN=State Of Utopia Issuing Authority Signing Key")

            // Valid from now to five years from now.
            val now = Date()
            val kMilliSecsInOneYear = 365L * 24 * 60 * 60 * 1000
            val expirationDate = Date(now.time + 5 * kMilliSecsInOneYear)
            val serial = BigInteger("42")
            val builder = JcaX509v3CertificateBuilder(
                issuer, serial, now,
                expirationDate, subject, issuerKeypair.public
            )
            val signer: ContentSigner =
                JcaContentSignerBuilder("SHA256withECDSA").build(
                    issuerKeypair.private
                )
            val encodedCert: ByteArray = builder.build(signer).encoded
            val cf =
                CertificateFactory.getInstance("X.509")
            val bais = ByteArrayInputStream(encodedCert)
            val selfSignedIssuerCert = cf.generateCertificate(bais) as X509Certificate

            val nameSpacedData = getNameSpacedData(context, firstName)
            val issuerSignedMapping = MdocUtil.generateIssuerNameSpaces(
                nameSpacedData,
                kotlin.random.Random.Default,
                16,
                null
            )
            val authKey = credential.attestation.publicKey

            // Create MSO.
            val signedDate = Clock.System.now()
            val validFromDate = Clock.System.now()
            val validToCalendar = Calendar.getInstance()
            validToCalendar.add(Calendar.MONTH, 12)
            val validToDate = Instant.fromEpochMilliseconds(validToCalendar.timeInMillis)
            val msoGenerator = MobileSecurityObjectGenerator(
                "SHA-256",
                docType, authKey
            ).setValidityInfo(signedDate, validFromDate, validToDate, null)

            issuerSignedMapping.forEach { (ns: String, issuerSignedItems: List<ByteArray>) ->

                val digests = MdocUtil.calculateDigestsForNameSpace(
                    ns,
                    issuerSignedMapping,
                    Algorithm.SHA256
                )

                msoGenerator.addDigestIdsForNamespace(ns, digests)
            }
            val encodedMobileSecurityObject = msoGenerator.generate()
            val taggedEncodedMso = Cbor.encode(
                Tagged(Tagged.ENCODED_CBOR, Bstr(encodedMobileSecurityObject))
            )
            val encodedIssuerAuth = Cbor.encode(
                coseSign1Sign(
                    issuerKeypair.private.toEcPrivateKey(issuerKeypair.public, EcCurve.P256),
                taggedEncodedMso,
                true,
                Algorithm.ES256,
                mapOf(
                    Pair(
                        CoseNumberLabel(Cose.COSE_LABEL_ALG),
                        Algorithm.ES256.coseAlgorithmIdentifier.toDataItem()
                    )
                ),
                mapOf(
                    Pair(
                        CoseNumberLabel(Cose.COSE_LABEL_X5CHAIN),
                        X509CertChain(listOf(X509Cert(selfSignedIssuerCert.encoded))).toDataItem()
                    )
                )
            ).toDataItem()
            )
            StaticAuthDataGenerator(issuerSignedMapping, encodedIssuerAuth).generate()
        } catch (e: Exception) {
            throw IllegalStateException("Failed to create CredentialData error: " + e.message)
        }
    }

    private fun getNameSpacedData(
        context: Context,
        firstName: String
    ): NameSpacedData {
        val nsBuilder = NameSpacedData.Builder()
        val bitmapPortrait = BitmapFactory.decodeResource(
            context.resources,
            R.drawable.img_erika_portrait
        )
        val baos = ByteArrayOutputStream()
        bitmapPortrait.compress(Bitmap.CompressFormat.JPEG, 50, baos)
        val portrait = baos.toByteArray()
        val bitmapSignature = BitmapFactory.decodeResource(
            context.resources,
            R.drawable.img_erika_signature
        )
        baos.reset()
        bitmapSignature.compress(Bitmap.CompressFormat.JPEG, 50, baos)
        val signature = baos.toByteArray()
        val biometric_template = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        baos.reset()
        biometric_template.compress(Bitmap.CompressFormat.JPEG, 50, baos)
        val biometric = baos.toByteArray()

        // reused fields
        val unDistinguishingSign = "US"
        val ageOver18 = true
        val ageOver21 = true
        val sex = 2

        val drivingPrivileges = CborArray.Companion.builder().addMap()
            .put("vehicle_category_code", "A")
            .put("issue_date", Tagged(1004, Tstr("2018-08-09")))
            .put("expiry_date", Tagged(1004, Tstr("2024-10-20"))).end()
            .addMap()
            .put("vehicle_category_code", "B")
            .put("issue_date", Tagged(1004, Tstr("2017-02-23")))
            .put("expiry_date", Tagged(1004, Tstr("2024-10-20"))).end().end().build()

        nsBuilder.putEntryString(
            MDL_NAMESPACE,
            "given_name",
            firstName
        )
        nsBuilder.putEntryString(
            MDL_NAMESPACE,
            "family_name",
            "Mustermann"
        )
        nsBuilder.putEntry(MDL_NAMESPACE,
            "birth_date",
            Cbor.encode(Tagged(1004, Tstr("1971-09-01"))))
        nsBuilder.putEntryByteString(
            MDL_NAMESPACE,
            "portrait",
            portrait
        )
        nsBuilder.putEntry(MDL_NAMESPACE,
            "issue_date",
            Cbor.encode(Tagged(1004, Tstr("2021-04-18"))))
        nsBuilder.putEntry(MDL_NAMESPACE,
            "expiry_date",
            Cbor.encode(Tagged(1004, Tstr("2026-04-18"))))
        nsBuilder.putEntryString(
            MDL_NAMESPACE,
            "issuing_country",
            "US"
        )
        nsBuilder.putEntryString(
            MDL_NAMESPACE,
            "issuing_authority",
            "Google"
        )
        nsBuilder.putEntryString(
            MDL_NAMESPACE,
            "document_number",
            "987654321"
        )
        nsBuilder.putEntry(
            MDL_NAMESPACE,
            "driving_privileges",
            Cbor.encode(drivingPrivileges)
        )
        nsBuilder.putEntryString(
            MDL_NAMESPACE,
            "un_distinguishing_sign",
            unDistinguishingSign
        )
        nsBuilder.putEntryBoolean(
            MDL_NAMESPACE,
            "age_over_18",
            ageOver18
        )
        nsBuilder.putEntryBoolean(
            MDL_NAMESPACE,
            "age_over_21",
            ageOver21
        )
        nsBuilder.putEntryByteString(
            MDL_NAMESPACE,
            "signature_usual_mark",
            signature
        )
        nsBuilder.putEntryByteString(
            MDL_NAMESPACE,
            "biometric_template_iris",
            biometric
        )
        nsBuilder.putEntryByteString(
            MDL_NAMESPACE,
            "biometric_template_signature_sign",
            biometric
        )
        nsBuilder.putEntryNumber(
            MDL_NAMESPACE,
            "sex",
            sex.toLong()
        )

        // second namespace
        nsBuilder.putEntryString(
            AAMVA_NAMESPACE,
            "un_distinguishing_sign",
            unDistinguishingSign
        )
        nsBuilder.putEntryBoolean(
            AAMVA_NAMESPACE,
            "age_over_18",
            ageOver18
        )
        nsBuilder.putEntryBoolean(
            AAMVA_NAMESPACE,
            "age_over_21",
            ageOver21
        )
        nsBuilder.putEntryByteString(
            AAMVA_NAMESPACE,
            "signature_usual_mark",
            signature
        )
        nsBuilder.putEntryByteString(
            AAMVA_NAMESPACE,
            "biometric_template_iris",
            biometric
        )
        nsBuilder.putEntryByteString(
            AAMVA_NAMESPACE,
            "biometric_template_signature_sign",
            biometric
        )
        nsBuilder.putEntryNumber(
            AAMVA_NAMESPACE,
            "sex",
            sex.toLong()
        )

        return nsBuilder.build()
    }

    @Throws(NoSuchAlgorithmException::class, InvalidKeyException::class)
    private fun createMdocRequest(
        readerKey: KeyPair,
        readerKeyCertChain: X509CertChain,
        reqIds: Array<String>,
        sessionTranscript: ByteArray
    ): ByteArray {
        val mdlNamespace: MutableMap<String, Map<String, Boolean>> = HashMap()
        val entries: MutableMap<String, Boolean> = HashMap()
        for (eleId in reqIds) {
            entries[eleId] = false
        }
        mdlNamespace[MDL_NAMESPACE] = entries
        var signature: Signature? = null
        signature = Signature.getInstance("SHA256withECDSA", BouncyCastleProvider())
        signature.initSign(readerKey.private)
        val generator = DeviceRequestGenerator(sessionTranscript)
        generator.addDocumentRequest(
            MDL_DOC_TYPE,
            mdlNamespace,
            null,
            readerKey.private.toEcPrivateKey(readerKey.public, EcCurve.P256),
            Algorithm.ES256,
            readerKeyCertChain
        )
        return generator.generate()
    }
}