package com.android.identity.presentationlog

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.identity.android.storage.AndroidStorageEngine
import com.android.identity.storage.StorageEngine
import com.android.identity.util.EngagementTypeDef
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PresentationLogStoreTest {

    private lateinit var storageEngine: StorageEngine
    private lateinit var presentationLogStore: PresentationLogStore
    private val presentationHistoryStore: PresentationLogStore.PresentationHistoryStore
        get() = presentationLogStore.presentationHistoryStore
    @Before
    fun setUp() {
        /**
         * Localized function for obtaining identity storage location dir.
         */
        fun getKeystoreBackedStorageLocation(context: Context): File {
            // As per the docs, the credential data contains reference to Keystore aliases so ensure
            // this is stored in a location where it's not automatically backed up and restored by
            // Android Backup as per https://developer.android.com/guide/topics/data/autobackup
            val storageDir = File(context.noBackupFilesDir, "identity")
            if (!storageDir.exists()) {
                storageDir.mkdir()
            }
            return storageDir;
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storageDir = getKeystoreBackedStorageLocation(context)
        storageEngine = AndroidStorageEngine.Builder(context, storageDir).build()
        presentationLogStore = PresentationLogStore(storageEngine)
    }


    /**
     * After every test case, remove any persisted log entries.
     */
    @After
    fun teardown() {
        presentationLogStore.presentationHistoryStore.deleteAllLogs()
    }

    private object TestConst {
        const val REQUEST_DATA_PREFIX = "Request_Data_"
        const val RESPONSE_DATA_PREFIX = "Response_Data_"
        const val ERROR_PREFIX = "ERROR_"
    }

    /**
     * Singleton object providing functions that generate text during testing.
     */
    private object TestGen {
        fun getSampleRequestText(id: Int) = TestConst.REQUEST_DATA_PREFIX + id
        fun getSampleRequestBytes(id: Int) = getSampleRequestText(id).toByteArray()

        fun getSampleResponseText(id: Int) = TestConst.RESPONSE_DATA_PREFIX + id
        fun getSampleResponseBytes(id: Int) = getSampleResponseText(id).toByteArray()

        fun getSampleErrorText(id: Int) = TestConst.ERROR_PREFIX + id
        fun getSampleErrorThrowable(id: Int) = Throwable(getSampleErrorText(id))
    }


    /**
     * Verify a single full entry with Request, Response and Metadata are logged properly
     * which confirms the transaction's start and end timestamps, engagement type (NFC), and the
     * data of request and response match what was asked to be logged.
     */
    @Test
    fun logOneEntry_Component_Request_Response_1Metadata() {
        val id = 10

        val startTime = 1L
        val endTime = 2L

        // create a new log entry with sample request bytes
        val entryBuilder = presentationLogStore.newLogEntryWithRequest(
            TestGen.getSampleRequestBytes(id),
            null,
            EngagementTypeDef.NFC_STATIC_HANDOVER
        )
        entryBuilder.metadataBuilder.transactionStartTimestamp(startTime)
        entryBuilder.metadataBuilder.transactionEndTimestamp(endTime)


        // add the response
        presentationLogStore.logResponseData(
            data = TestGen.getSampleResponseBytes(id),
            currentEntryId = entryBuilder.id
        )
        // mark that we have completed presentation (persist data to secure store)
        presentationLogStore.persistLogEntryTransactionComplete(currentEntryId = entryBuilder.id)

        // get all stored entries
        val entries = presentationHistoryStore.fetchAllLogEntries()

        // verify there's only 1 log entry stored
        Assert.assertEquals(entries.size, 1)

        val entry = entries.first()

        // verify metadata was logged correctly by checking start & end millis and engagement type
        val metadata = entry.getMetadata()
        Assert.assertNotNull(metadata)
        Assert.assertEquals(metadata!!.transactionStartTime, startTime)
        Assert.assertEquals(metadata.transactionEndTime, endTime)
        Assert.assertEquals(metadata.engagementType, EngagementTypeDef.NFC_STATIC_HANDOVER)

        // verify the request data matches what was stored (string with ID, not necessarily a DeviceRequest object)
        val requestBytes = entry.componentLogs[PresentationLogStore.LogComponent.Request]
        Assert.assertNotNull(requestBytes)
        Assert.assertEquals(java.lang.String(requestBytes), TestConst.REQUEST_DATA_PREFIX + id)

        // verify the response data matches what was stored (string with ID, not necessarily a DeviceResponse object)
        val responseBytes = entry.componentLogs[PresentationLogStore.LogComponent.Response]
        Assert.assertNotNull(responseBytes)
        Assert.assertEquals(java.lang.String(responseBytes), TestConst.RESPONSE_DATA_PREFIX + id)
    }

    /**
     * Verify that we can log 1 entry with only metadata component (without request or response bytes)
     * if an error was encountered.
     */
    @Test
    fun logOneEntry_Transaction_Error_Component_Metadata() {
        val id = 10
        presentationLogStore.newLogEntryWithRequest(byteArrayOf(), null, EngagementTypeDef.NFC_STATIC_HANDOVER)
        presentationLogStore.persistLogEntryTransactionError(TestGen.getSampleErrorThrowable(id))
        // get all stored entries
        val entries = presentationHistoryStore.fetchAllLogEntries()

        // verify there's only 1 log entry stored
        Assert.assertEquals(entries.size, 1)

        val entry = entries.first()

        // verify stored error matches what was thrown
        val metadata = entry.getMetadata()
        Assert.assertNotNull(metadata)
        Assert.assertEquals(
            metadata!!.presentationTransactionStatus,
            PresentationLogMetadata.PresentationTransactionStatus.Error
        )
        Assert.assertEquals(metadata.error, TestGen.getSampleErrorText(id))

        // verify there are no request bytes stored in store
        val requestBytes = entry.componentLogs[PresentationLogStore.LogComponent.Request]
        Assert.assertNull(requestBytes)
        // verify there are no response bytes stored
        val responseBytes = entry.componentLogs[PresentationLogStore.LogComponent.Response]
        Assert.assertNull(responseBytes)
    }

    /**
     * Verify that we can log 3 entries testing for:
     * - 1, 2 or 3 components being logged per entry
     * - engagement types are persisted and retrieved correctly: NFC, QR, Unattended
     * - transaction statuses are persisted and retrieved correctly: Canceled, Disconnected, Error
     *
     * where:
     *
     * - first entry has all 3 components (request, response, metadata), NFC engagement, Disconnected status
     * - second entry has 2 components (request, metadata) due to di, QR engagement, Canceled status
     * - third entry has 1 component (metadata) due to , UNATTENDED engagement, Error status
     */
    @Test
    fun logOneEntry_Transaction_Error_Component_Request_Metadata() {
        // define first log entry
        val firstId = 10
        val firstEngagement = EngagementTypeDef.NFC_STATIC_HANDOVER
        val firstStatus = PresentationLogMetadata.PresentationTransactionStatus.Disconnected

        // insert first log entry
        presentationLogStore.newLogEntryWithRequest(
            TestGen.getSampleRequestBytes(firstId),
            null,
            firstEngagement
        )
        presentationLogStore.logResponseData(TestGen.getSampleResponseBytes(firstId))
        presentationLogStore.persistLogEntryTransactionDisconnected() // mark as disconnected (and persist this entry)

        // define second log entry
        val secondId = 20
        val secondEngagement = EngagementTypeDef.QR_CODE
        val secondStatus = PresentationLogMetadata.PresentationTransactionStatus.Canceled

        // insert second log entry
        presentationLogStore.newLogEntryWithRequest(
            TestGen.getSampleRequestBytes(secondId),
            null,
            secondEngagement
        )
        presentationLogStore.persistLogEntryTransactionCanceled() // mark as canceled (and persist)


        // define third log entry
        val thirdId = 30
        val thirdEngagement = EngagementTypeDef.UNATTENDED
        val thirdStatus = PresentationLogMetadata.PresentationTransactionStatus.Error

        // insert third log entry
        presentationLogStore.newLogEntryWithRequest(byteArrayOf(), null, thirdEngagement)
        presentationLogStore.persistLogEntryTransactionError(TestGen.getSampleErrorThrowable(thirdId)) // mark as error (and persist)

        ////////////////////////////////////////////

        // get all stored entries, in descending order (last entry is first)
        val entries = presentationHistoryStore.fetchAllLogEntries()


        // verify there's 3 stored entries
        Assert.assertEquals(entries.size, 3)

        // verify third entry components
        val thirdEntry = entries[0]
        val thirdMetadata = thirdEntry.getMetadata()
        Assert.assertNotNull(thirdMetadata)
        Assert.assertEquals(thirdMetadata!!.presentationTransactionStatus, thirdStatus)
        Assert.assertEquals(thirdMetadata.error, TestGen.getSampleErrorText(thirdId))
        Assert.assertEquals(thirdMetadata.engagementType, thirdEngagement)
        // verify third entry request
        val thirdRequestBytes = thirdEntry.componentLogs[PresentationLogStore.LogComponent.Request]
        Assert.assertNull(thirdRequestBytes)
        // verify third entry response
        val thirdResponseBytes =
            thirdEntry.componentLogs[PresentationLogStore.LogComponent.Response]
        Assert.assertNull(thirdResponseBytes)

        // verify second entry components
        val secondEntry = entries[1]
        val secondMetadata = secondEntry.getMetadata()
        Assert.assertNotNull(secondMetadata)
        Assert.assertEquals(secondMetadata!!.presentationTransactionStatus, secondStatus)
        Assert.assertEquals(secondMetadata.engagementType, secondEngagement)
        // verify second entry request
        val secondRequestBytes =
            secondEntry.componentLogs[PresentationLogStore.LogComponent.Request]
        Assert.assertNotNull(secondRequestBytes)
        Assert.assertEquals(String(secondRequestBytes!!), TestGen.getSampleRequestText(secondId))
        // verify second entry response
        val secondResponseBytes =
            secondEntry.componentLogs[PresentationLogStore.LogComponent.Response]
        Assert.assertNull(secondResponseBytes)

        // verify first entry components
        val firstEntry = entries[2]
        val firstMetadata = firstEntry.getMetadata()
        Assert.assertNotNull(firstMetadata)
        Assert.assertEquals(firstMetadata!!.presentationTransactionStatus, firstStatus)
        Assert.assertEquals(firstMetadata.engagementType, firstEngagement)
        // verify first entry request
        val firstRequestBytes = firstEntry.componentLogs[PresentationLogStore.LogComponent.Request]
        Assert.assertNotNull(firstRequestBytes)
        Assert.assertEquals(String(firstRequestBytes!!), TestGen.getSampleRequestText(firstId))
        // verify first entry response
        val firstResponseBytes =
            firstEntry.componentLogs[PresentationLogStore.LogComponent.Response]
        Assert.assertNotNull(firstResponseBytes)
        Assert.assertEquals(String(firstResponseBytes!!), TestGen.getSampleResponseText(firstId))
    }

    /**
     * Verify that deleting a single entry from 3 works without affecting other entries.
     */
    @Test
    fun log3Entries_delete1Entry() {
        // insert 3 entries
        for (i in 1..3) {
            val entryBuilder = presentationLogStore.newLogEntryWithRequest(
                TestGen.getSampleRequestBytes(i),
                null,
                EngagementTypeDef.NFC_STATIC_HANDOVER
            )

            entryBuilder.metadataBuilder.transactionStartTimestamp(i * 1L)
            entryBuilder.metadataBuilder.transactionEndTimestamp(i * (10L + i))

            presentationLogStore.logResponseData(TestGen.getSampleResponseBytes(i))
            presentationLogStore.persistLogEntryTransactionComplete()
        }

        // get entries in ascending order, delete the first (oldest entry, smallest timestamp)
        val entries = presentationHistoryStore.fetchAllLogEntries().sortedBy { it.id }
        Assert.assertEquals(entries.size, 3)

        val oldest = entries[0]
        val mid = entries[1]
        val youngest = entries[2]

        // delete first entry
        presentationHistoryStore.deleteLogEntry(oldest.id)

        // get new entries, verify count is 2
        val newEntries = presentationHistoryStore.fetchAllLogEntries().sortedBy { it.id }
        Assert.assertEquals(newEntries.size, 2)

        // verify the entries that remain are not the oldest entry
        Assert.assertEquals(newEntries[0].id, mid.id)
        Assert.assertEquals(newEntries[1].id, youngest.id)
    }

    /**
     * Add 3 entries, delete 2 youngest entries, verify the oldest entry remains
     */
    @Test
    fun log3Entries_delete2Entries() {
        // insert 3 entries
        for (i in 1..3) {
            val entryBuilder = presentationLogStore.newLogEntryWithRequest(
                TestGen.getSampleRequestBytes(i),
                null,
                EngagementTypeDef.NFC_STATIC_HANDOVER
            )

            entryBuilder.metadataBuilder.transactionStartTimestamp(i * 1L)
            entryBuilder.metadataBuilder.transactionEndTimestamp(i * (10L + i))

            presentationLogStore.logResponseData(TestGen.getSampleResponseBytes(i))
            presentationLogStore.persistLogEntryTransactionComplete()
        }

        // get entries in ascending order, delete the first (oldest entry, smallest timestamp)
        val entries = presentationHistoryStore.fetchAllLogEntries().sortedBy { it.id }
        Assert.assertEquals(entries.size, 3)

        val oldest = entries[0]
        val mid = entries[1]
        val youngest = entries[2]

        // delete 2 oldest entries
        presentationHistoryStore.deleteLogEntry(oldest.id)
        presentationHistoryStore.deleteLogEntry(mid.id)

        // get new entries, verify count is 1
        val newEntries = presentationHistoryStore.fetchAllLogEntries().sortedBy { it.id }
        Assert.assertEquals(newEntries.size, 1)

        // verify the only remaining entry is the oldest entry
        Assert.assertEquals(newEntries[0].id, youngest.id)
    }

    /**
     * Test PresentationHistoryStore by deleting all log entries after adding 20 entries.
     */
    @Test
    fun log20Entries_deleteAllEntries() {
        // insert 20 entries
        for (i in 1..20) {
            presentationLogStore.newLogEntryWithRequest(
                TestGen.getSampleRequestBytes(i),
                null,
                EngagementTypeDef.NFC_STATIC_HANDOVER
            )
            presentationLogStore.logResponseData(TestGen.getSampleResponseBytes(i))
            presentationLogStore.persistLogEntryTransactionComplete()
        }

        // get entries in ascending order, delete the first (oldest entry, smallest timestamp)
        val entries = presentationHistoryStore.fetchAllLogEntries()
        Assert.assertEquals(entries.size, 20)

        // delete all logs
        presentationHistoryStore.deleteAllLogs()

        // get new list of logs
        val newEntries = presentationHistoryStore.fetchAllLogEntries()

        // confirm there are no presentation logs persisted in StorageEngine
        Assert.assertEquals(newEntries.size, 0)
    }

    /**
     * Test enforcement of MAX_ENTRIES_COUNT by adding MAX_ENTRIES_COUNT entries and verifying the
     * count of stored entries matches MAX_ENTRIES_COUNT, then add 1 more entry and confirm the count
     * of stored entries continues to match MAX_ENTRIES_COUNT.l
     */
    @Test
    fun maxEntriesEnforcement() {
        // add as many entries as defined in PresentationLogStore.StoreConst.MAX_ENTRIES
        for (i in 1..PresentationLogStore.StoreConst.MAX_ENTRIES_COUNT) {
            presentationLogStore.newLogEntryWithRequest(
                TestGen.getSampleRequestBytes(i),
                null,
                EngagementTypeDef.NFC_STATIC_HANDOVER
            )
            presentationLogStore.logResponseData(TestGen.getSampleResponseBytes(i))
            presentationLogStore.persistLogEntryTransactionComplete()
        }

        // confirm there are MAX_ENTRIES_COUNT in StorageEngine
        val entries = presentationHistoryStore.fetchAllLogEntries()
        Assert.assertEquals(entries.size, PresentationLogStore.StoreConst.MAX_ENTRIES_COUNT)

        // add 1 more entry
        presentationLogStore.newLogEntryWithRequest(
            TestGen.getSampleRequestBytes(200),
            null,
            EngagementTypeDef.NFC_STATIC_HANDOVER
        )
        presentationLogStore.logResponseData(TestGen.getSampleResponseBytes(200))
        presentationLogStore.persistLogEntryTransactionComplete()

        // verify that we still have at most MAX_ENTRIES_COUNT entries in StorageEngine
        val newEntries = presentationHistoryStore.fetchAllLogEntries()
        Assert.assertEquals(newEntries.size, PresentationLogStore.StoreConst.MAX_ENTRIES_COUNT)
    }
}