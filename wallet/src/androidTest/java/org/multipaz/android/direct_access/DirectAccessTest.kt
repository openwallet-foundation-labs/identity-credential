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

package org.multipaz.android.direct_access

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.identity.android.mdoc.deviceretrieval.IsoDepWrapper
import com.android.identity.android.mdoc.deviceretrieval.VerificationHelper
import com.android.identity.android.mdoc.transport.DataTransportOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.After
import org.junit.Assert
import org.junit.Assume.assumeTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.multipaz.cbor.Bstr
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborArray
import org.multipaz.cbor.RawCbor
import org.multipaz.cbor.Tagged
import org.multipaz.cbor.Tstr
import org.multipaz.cbor.buildCborArray
import org.multipaz.cbor.buildCborMap
import org.multipaz.cbor.toDataItem
import org.multipaz.context.initializeApplication
import org.multipaz.cose.Cose
import org.multipaz.cose.Cose.coseSign1Sign
import org.multipaz.cose.CoseNumberLabel
import org.multipaz.credential.CredentialLoader
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcPublicKey
import org.multipaz.crypto.EcPublicKeyDoubleCoordinate
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509CertChain
import org.multipaz.crypto.javaPublicKey
import org.multipaz.crypto.toEcPrivateKey
import org.multipaz.document.DocumentStore
import org.multipaz.document.NameSpacedData
import org.multipaz.flow.server.Resources
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethod
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethod.Companion.disambiguate
import org.multipaz.mdoc.mso.MobileSecurityObjectGenerator
import org.multipaz.mdoc.mso.StaticAuthDataGenerator
import org.multipaz.mdoc.request.DeviceRequestGenerator
import org.multipaz.mdoc.response.DeviceResponseParser
import org.multipaz.mdoc.role.MdocRole
import org.multipaz.mdoc.util.MdocUtil
import org.multipaz.securearea.AndroidKeystoreSecureArea
import org.multipaz.securearea.SecureAreaRepository
import org.multipaz.storage.Storage
import org.multipaz.storage.android.AndroidStorage
import org.multipaz.util.Constants
import org.multipaz.util.Logger
import org.multipaz.wallet.R
import org.multipaz.wallet.provisioning.WalletDocumentMetadata
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigInteger
import java.nio.ByteBuffer
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
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Arrays
import java.util.Calendar
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.security.auth.x500.X500Principal
import kotlin.coroutines.CoroutineContext

@RunWith(AndroidJUnit4::class)
class DirectAccessTest {
    // TODO During testing change DirectAccessOmapiTransport to DirectAccessSmartCardTransport
    //  in DirectAccess.kt
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
    private var storage: Storage
    private var secureAreaRepository: SecureAreaRepository
    private var documentStore: DocumentStore
    private lateinit var readerKeys: ArrayList<KeyPair>
    private lateinit var readerCertChain: X509CertChain

    @Throws(Exception::class)
    private fun generateEcdsaKeyPair(): KeyPair {
        Security.removeProvider("BC")
        Security.addProvider(BouncyCastleProvider())
        val kpg = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            BouncyCastleProvider.PROVIDER_NAME
        )
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
        initializeApplication(context)
        DirectAccessSmartCardTransport()
        //assumeTrue(DirectAccess.isDirectAccessSupported)
        storage = AndroidStorage(":memory:")
        secureAreaRepository = SecureAreaRepository.build {
            add(AndroidKeystoreSecureArea.create(storage))
        }
        val credentialLoader: CredentialLoader = CredentialLoader()
        credentialLoader.addCredentialImplementation(DirectAccessCredential::class) { document ->
            DirectAccessCredential(document)
        }
        documentStore = DocumentStore(
            storage = storage,
            secureAreaRepository = secureAreaRepository,
            credentialLoader = credentialLoader,
            documentMetadataFactory = WalletDocumentMetadata::create
        )

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
        if (DirectAccess.isDirectAccessSupported) {
            DirectAccessSmartCardTransport.closeConnection()
            DirectAccessSmartCardTransport.openConnection()

            // Todo uncomment once enumerateAllocatedSlots is implemented
//        // Delete all slots to reset applet state.
//        for (slot in DirectAccess.enumerateAllocatedSlots()) {
//            DirectAccess.clearDocumentSlot(slot)
//        }

            try {
                DirectAccessSmartCardTransport.closeConnection()
            } catch (e: IOException) {
                Assert.fail("Unexpected Exception $e")
            }
        }
    }

    @Ignore("enumerateAllocatedSlots needs to be implemented")
    @Test
    fun testAllocateAndClearDocumentSlot() {
        // First try allocating a single slot.
        Assert.assertEquals(0, DirectAccess.enumerateAllocatedSlots().size)
        val singleSlot = DirectAccess.allocateDocumentSlot(MDL_DOC_TYPE)
        Assert.assertEquals(1, DirectAccess.enumerateAllocatedSlots().size)
        Assert.assertEquals(1, DirectAccess.enumerateAllocatedSlots()[0])

        // And deleting that single slot.
        Assert.assertTrue(DirectAccess.clearDocumentSlot(singleSlot))
        Assert.assertEquals(0, DirectAccess.enumerateAllocatedSlots().size)

        // Now allocate slots until -1 is returned, meaning there are no more available slots to be
        // allocated.
        var fullyAllocated = false
        val allocatedSlots = ArrayList<Int>()
        while (!fullyAllocated) {
            val newSlot = DirectAccess.allocateDocumentSlot(MDL_DOC_TYPE)
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
                    DirectAccess.clearDocumentSlot(slot)
                }
                Assert.fail(
                    "The applet has allowed for the allocation of 50 slots, " +
                            "indicating it has not correctly implemented allocateDocumentSlot in the " +
                            "event of allocation attempts when full."
                )
            }
        }

        // Delete all slots to reset applet state.
        for (slot in allocatedSlots) {
            Assert.assertTrue(DirectAccess.clearDocumentSlot(slot))
        }
    }

    @Test
    fun testProvisioning() {
        runTest {
            val document = documentStore.createDocument()
            val slot = DirectAccess.allocateDocumentSlot(MDL_DOC_TYPE)
            val metadata = document.metadata as WalletDocumentMetadata
            metadata.setDocumentSlot(slot)
            val pendingCredential = DirectAccessCredential.create(
                document,
                null,
                CREDENTIAL_DOMAIN,
                MDL_DOC_TYPE,
            )
            Assert.assertEquals(1, document.getPendingCredentials().size)

            // Certify pending credential.
            val issuerAuthData = createTestIssuerAuthData(
                context,
                pendingCredential,
                MDL_DOC_TYPE
            )
            val validFrom = Clock.System.now()
            pendingCredential.certify(
                issuerAuthData,
                validFrom,
                Instant.fromEpochMilliseconds(validFrom.toEpochMilliseconds() + 50)
            )
            Assert.assertEquals(1, document.getCertifiedCredentials().size)

            // Delete credentials and document, and clear slot.
            documentStore.deleteDocument(document.identifier)
            DirectAccess.clearDocumentSlot(slot)
        }
    }

    @Test
    fun testProvisionWithInvalidCredentialData() {
        runTest {
            val document = documentStore.createDocument()
            val slot = DirectAccess.allocateDocumentSlot(MDL_DOC_TYPE)
            val metadata = document.metadata as WalletDocumentMetadata
            metadata.setDocumentSlot(slot)

            metadata.setDocumentSlot(slot)
            val pendingCredential = DirectAccessCredential.create(
                document,
                null,
                CREDENTIAL_DOMAIN,
                MDL_DOC_TYPE,
            )
            Assert.assertEquals(1, document.getPendingCredentials().size)


            // Attempt to certify pending credential.
            val mapIssuerAuthToErrorMsg = mapOf(
                Pair(byteArrayOf(), "Expected to fail when empty issuer auth is passed."),
                Pair(
                    "invalid-cred-data".toByteArray(StandardCharsets.UTF_8),
                    "Expected to fail when empty credential data is passed."
                )
            )
            val validFrom = Clock.System.now()
            for ((invalidIssuerAuth, errorMsg) in mapIssuerAuthToErrorMsg) {
                Assert.assertThrows(errorMsg, IllegalArgumentException::class.java) {
                    runBlocking {
                        pendingCredential.certify(
                            invalidIssuerAuth,
                            validFrom,
                            Instant.fromEpochMilliseconds(validFrom.toEpochMilliseconds() + 1000)
                        )
                    }
                }
            }

            // Delete credentials and document, and clear slot.
            documentStore.deleteDocument(document.identifier)
            DirectAccess.clearDocumentSlot(slot)
        }
    }

    @Test
    fun testBasicPresentation() {
        runTest {
            val document = documentStore.createDocument()
            val slot = DirectAccess.allocateDocumentSlot(MDL_DOC_TYPE)
            val metadata = document.metadata as WalletDocumentMetadata
            metadata.setDocumentSlot(slot)

            metadata.setDocumentSlot(slot)
            val pendingCredential = DirectAccessCredential.create(
                document,
                null,
                CREDENTIAL_DOMAIN,
                MDL_DOC_TYPE,
            )
            Assert.assertEquals(1, document.getPendingCredentials().size)


            val firstName = "Erika"
            val issuerAuthData = createTestIssuerAuthData(
                context,
                pendingCredential,
                MDL_DOC_TYPE,
                firstName
            )
            val validFrom = Clock.System.now()
            pendingCredential.certify(
                issuerAuthData,
                validFrom,
                Instant.fromEpochMilliseconds(validFrom.toEpochMilliseconds() + 50)
            )
            pendingCredential.setAsActiveCredential()

            mockPresentationAndCheckExpectedFirstName(firstName)

            // Delete credentials and document, and clear slot.
            documentStore.deleteDocument(document.identifier)
            DirectAccess.clearDocumentSlot(slot)
        }
    }


    @Test
    fun testMaxCredentialSize() {
        Assert.assertTrue(0x7fffL <= DirectAccess.maximumCredentialSize)
    }

    @Test
    fun testAppletStatePostCreateAndCertify() {
        runTest {
            val document = documentStore.createDocument()
            val slot = DirectAccess.allocateDocumentSlot(MDL_DOC_TYPE)
            val metadata = document.metadata as WalletDocumentMetadata
            metadata.setDocumentSlot(slot)

            metadata.setDocumentSlot(slot)
            val erikaCredential = DirectAccessCredential.create(
                document,
                null,
                CREDENTIAL_DOMAIN,
                MDL_DOC_TYPE,
            )
            Assert.assertEquals(1, document.getPendingCredentials().size)

            val erikaCredentialFirstName = "Erika"
            var issuerAuthData = createTestIssuerAuthData(
                context,
                erikaCredential,
                MDL_DOC_TYPE,
                erikaCredentialFirstName
            )
            var validFrom = Clock.System.now()
            erikaCredential.certify(
                issuerAuthData,
                validFrom,
                Instant.fromEpochMilliseconds(validFrom.toEpochMilliseconds() + 100)
            )

            // Set it as active and ensure it's used during presentation.
            erikaCredential.setAsActiveCredential()
            mockPresentationAndCheckExpectedFirstName(erikaCredentialFirstName)

            // Create a second credential and mock a presentation to ensure the active credential has
            // not been changed.
            val janeCredential = DirectAccessCredential.create(
                document,
                null,
                CREDENTIAL_DOMAIN,
                MDL_DOC_TYPE,
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
            janeCredential.certify(
                issuerAuthData,
                validFrom,
                Instant.fromEpochMilliseconds(validFrom.toEpochMilliseconds() + 100)
            )
            mockPresentationAndCheckExpectedFirstName(erikaCredentialFirstName)

            // Set the second credential as active and mock a presentation to ensure the active credential
            // has been changed.
            janeCredential.setAsActiveCredential()
            mockPresentationAndCheckExpectedFirstName(janeCredentialFirstName)

            // Delete credentials and document, and clear slot.
            documentStore.deleteDocument(document.identifier)
            DirectAccess.clearDocumentSlot(slot)
        }
    }


    @Ignore("need to implement getCredentialUsageCount + clearCredentialUsageCount")
    @Test
    fun testGetAndClearCredentialUsageCount() {
        runTest {
            val document = documentStore.createDocument()
            val slot = DirectAccess.allocateDocumentSlot(MDL_DOC_TYPE)
            val metadata = document.metadata as WalletDocumentMetadata
            metadata.setDocumentSlot(slot)

            metadata.setDocumentSlot(slot)

            // Create and certify a credential, and set it as active
            val credential = DirectAccessCredential.create(
                document,
                null,
                CREDENTIAL_DOMAIN,
                MDL_DOC_TYPE,
            )
            Assert.assertEquals(1, document.getPendingCredentials().size)

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

            var expectedUsageCount = 0
            Assert.assertEquals(expectedUsageCount, DirectAccess.getCredentialUsageCount(slot))
            for (i in 1..5) {
                mockPresentationAndCheckExpectedFirstName(firstName)
                expectedUsageCount += 1
                Assert.assertEquals(expectedUsageCount, DirectAccess.getCredentialUsageCount(slot))
            }

            // Post clearing usage count, assert getCredentialUsageCount returns 0
            DirectAccess.clearCredentialUsageCount(slot)
            expectedUsageCount = 0
            Assert.assertEquals(expectedUsageCount, DirectAccess.getCredentialUsageCount(slot))

            // Mock another 5 presentations and check usage count
            for (i in 1..5) {
                mockPresentationAndCheckExpectedFirstName(firstName)
                expectedUsageCount += 1
                Assert.assertEquals(expectedUsageCount, DirectAccess.getCredentialUsageCount(slot))
            }

            // Delete credentials and document, and clear slot.
            documentStore.deleteDocument(document.identifier)
            DirectAccess.clearDocumentSlot(slot)
        }
    }

    private fun mockPresentationAndCheckExpectedFirstName(expectedFirstName: String) {
        DirectAccessSmartCardTransport.closeConnection()

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
        var mConnectionMethods: List<MdocConnectionMethod>? = null
        var deviceResponse: ByteArray? = null
        val mResponseListener: VerificationHelper.Listener = object : VerificationHelper.Listener {
            override fun onDeviceEngagementReceived(connectionMethods: List<MdocConnectionMethod>) {
                mConnectionMethods = disambiguate(connectionMethods, MdocRole.MDOC_READER)
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

        val wrapper: IsoDepWrapper = ShadowIsoDep()
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
        Assert.assertEquals(
            expectedFirstName,
            mDl!!.getIssuerEntryString(MDL_NAMESPACE, "given_name")
        )

        // Reset state of helper objects.
        resetLatch()
        verificationHelper.disconnect()
        //waitForResponse(DEVICE_CONNECT_STATUS_DISCONNECTED, 60 /* 1 minute */)
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
                Algorithm.SHA256,
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
                            Algorithm.ES256.coseAlgorithmIdentifier!!.toDataItem()
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
            val digestIdMappingItem = buildCborMap {
                for ((namespace, bytesList) in issuerSignedMapping) {
                    putArray(namespace).let { innerBuilder ->
                        bytesList.forEach { encodedIssuerSignedItemMetadata ->
                            innerBuilder.add(RawCbor(encodedIssuerSignedItemMetadata))
                        }
                    }
                }
            }
            val readerBuilder = CborArray.builder()
            for (cert in readerCertChain.certificates) {
                val pubKey = getAndFormatRawPublicKey(cert)
                readerBuilder.add(pubKey)
            }
            val readerAuth = readerBuilder.end().build()
            Cbor.encode(
                buildCborMap {
                    put("issuerNameSpaces", digestIdMappingItem)
                    put("issuerAuth", RawCbor(encodedIssuerAuth))
                    put("readerAccess", readerAuth)
                }
            )
        } catch (e: Exception) {
            throw IllegalStateException("Failed to create CredentialData error: " + e.message)
        }
    }

    private fun getAndFormatRawPublicKey(cert: X509Cert): ByteArray {
        val pubKey: EcPublicKey = cert.ecPublicKey
        val key: ECPublicKey = pubKey.javaPublicKey as ECPublicKey
        val xCoord: BigInteger = key.w.affineX
        val yCoord: BigInteger = key.w.affineY
        var keySize = 0
        if ("EC" == key.algorithm) {
            val curve: Int =
                key.params.curve.field.fieldSize
            when (curve) {
                256 -> keySize = 32
                384 -> keySize = 48
                521 -> keySize = 66
                512 -> keySize = 65
            }
        } else {
            // TODO Handle other Algorithms
        }
        val bb: ByteBuffer = ByteBuffer.allocate((keySize * 2) + 1)
        Arrays.fill(bb.array(), 0.toByte())
        bb.put(0x04.toByte())
        val xBytes: ByteArray = xCoord.toByteArray()
        // BigInteger returns the value as two's complement big endian byte encoding. This means
        // that a positive, 32-byte value with a leading 1 bit will be converted to a byte array of
        // length 33 in order to include a leading 0 bit.
        if (xBytes.size == (keySize + 1)) {
            bb.put(xBytes, 1, keySize)
        } else {
            bb.position(bb.position() + keySize - xBytes.size)
            bb.put(xBytes, 0, xBytes.size)
        }
        val yBytes: ByteArray = yCoord.toByteArray()
        if (yBytes.size == (keySize + 1)) {
            bb.put(yBytes, 1, keySize)
        } else {
            bb.position(bb.position() + keySize - yBytes.size)
            bb.put(yBytes, 0, yBytes.size)
        }
        return bb.array()
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
        nsBuilder.putEntry(
            MDL_NAMESPACE,
            "birth_date",
            Cbor.encode(Tagged(1004, Tstr("1971-09-01")))
        )
        nsBuilder.putEntryByteString(
            MDL_NAMESPACE,
            "portrait",
            portrait
        )
        nsBuilder.putEntry(
            MDL_NAMESPACE,
            "issue_date",
            Cbor.encode(Tagged(1004, Tstr("2021-04-18")))
        )
        nsBuilder.putEntry(
            MDL_NAMESPACE,
            "expiry_date",
            Cbor.encode(Tagged(1004, Tstr("2026-04-18")))
        )
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
