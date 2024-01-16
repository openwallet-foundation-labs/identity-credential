package com.android.identity.wallet.presentationlog

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.identity.android.storage.AndroidStorageEngine
import com.android.identity.storage.StorageEngine
import com.android.identity.wallet.util.EngagementType
import com.android.identity.wallet.util.PreferencesHelper
import com.android.identity.wallet.util.ProvisioningUtil
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PresentationLogStoreTest {

    private lateinit var storageEngine: StorageEngine
    private lateinit var presentationLogStore: PresentationLogStore
    private val presentationHistoryStore: PresentationLogStore.PresentationHistoryStore
        get() = presentationLogStore.presentationHistoryStore

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storageDir = PreferencesHelper.getKeystoreBackedStorageLocation(context)
        storageEngine = AndroidStorageEngine.Builder(context, storageDir).build()
        presentationLogStore = ProvisioningUtil.getInstance(context).logStore
    }

    @After
    fun teardown() {
        presentationLogStore.presentationHistoryStore.deleteAllLogs()
    }

    private object TestConst {
        const val REQUEST_DATA_PREFIX = "Request_Data_"
        const val RESPONSE_DATA_PREFIX = "Response_Data_"
        const val ERROR_PREFIX = "ERROR_"
    }

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
    fun logOneEntry_Component_Request_Response_fMetadata() {
        val id = 10

        val startTime = 1L
        val endTime = 2L

        // create a new log entry with sample request bytes
        val entryBuilder = presentationLogStore.newLogEntryWithRequest(
            TestGen.getSampleRequestBytes(id),
            null,
            EngagementType.NFC
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
        assertThat(entries.size).isEqualTo(1)

        val entry = entries.first()

        // verify metadata was logged correctly by checking start & end millis and engagement type
        val metadata = entry.getMetadata()
        assertThat(metadata).isNotNull()
        assertThat(metadata!!.transactionStartTime).isEqualTo(startTime)
        assertThat(metadata.transactionEndTime).isEqualTo(endTime)
        assertThat(metadata.engagementType).isEqualTo(EngagementType.NFC)

        // verify the request data matches what was stored (string with ID, not necessarily a DeviceRequest object)
        val requestBytes = entry.componentLogs[PresentationLogStore.LogComponent.Request]
        assertThat(requestBytes).isNotNull()
        assertThat(java.lang.String(requestBytes)).isEqualTo(TestConst.REQUEST_DATA_PREFIX + id)

        // verify the response data matches what was stored (string with ID, not necessarily a DeviceResponse object)
        val responseBytes = entry.componentLogs[PresentationLogStore.LogComponent.Response]
        assertThat(responseBytes).isNotNull()
        assertThat(java.lang.String(responseBytes)).isEqualTo(TestConst.RESPONSE_DATA_PREFIX + id)
    }

    /**
     * Verify that we can log 1 entry with only metadata component (without request or response bytes)
     * if an error was encountered.
     */
    @Test
    fun logOneEntry_Transaction_Error_Component_Metadata() {
        val id = 10
        presentationLogStore.newLogEntryWithRequest(byteArrayOf(), null, EngagementType.NFC)
        presentationLogStore.persistLogEntryTransactionError(TestGen.getSampleErrorThrowable(id))
        // get all stored entries
        val entries = presentationHistoryStore.fetchAllLogEntries()

        // verify there's only 1 log entry stored
        assertThat(entries.size).isEqualTo(1)

        val entry = entries.first()

        // verify stored error matches what was thrown
        val metadata = entry.getMetadata()
        assertThat(metadata).isNotNull()
        assertThat(metadata!!.presentationTransactionStatus).isEqualTo(PresentationLogMetadata.PresentationTransactionStatus.Error)
        assertThat(metadata.error).isEqualTo(TestGen.getSampleErrorText(id))

        // verify there are no request bytes stored in store
        val requestBytes = entry.componentLogs[PresentationLogStore.LogComponent.Request]
        assertThat(requestBytes).isNull()
        // verify there are no response bytes stored
        val responseBytes = entry.componentLogs[PresentationLogStore.LogComponent.Response]
        assertThat(responseBytes).isNull()
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
        val firstEngagement = EngagementType.NFC
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
        val secondEngagement = EngagementType.QR
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
        val thirdEngagement = EngagementType.UNATTENDED
        val thirdStatus = PresentationLogMetadata.PresentationTransactionStatus.Error

        // insert third log entry
        presentationLogStore.newLogEntryWithRequest(byteArrayOf(), null, thirdEngagement)
        presentationLogStore.persistLogEntryTransactionError(TestGen.getSampleErrorThrowable(thirdId)) // mark as error (and persist)

        ////////////////////////////////////////////

        // get all stored entries, in descending order (last entry is first)
        val entries = presentationHistoryStore.fetchAllLogEntries()


        // verify there's 3 stored entries
        assertThat(entries.size).isEqualTo(3)

        // verify third entry components
        val thirdEntry = entries[0]
        val thirdMetadata = thirdEntry.getMetadata()
        assertThat(thirdMetadata).isNotNull()
        assertThat(thirdMetadata!!.presentationTransactionStatus).isEqualTo(thirdStatus)
        assertThat(thirdMetadata.error).isEqualTo(TestGen.getSampleErrorText(thirdId))
        assertThat(thirdMetadata.engagementType).isEqualTo(thirdEngagement)
        // verify third entry request
        val thirdRequestBytes = thirdEntry.componentLogs[PresentationLogStore.LogComponent.Request]
        assertThat(thirdRequestBytes).isNull()
        // verify third entry response
        val thirdResponseBytes =
            thirdEntry.componentLogs[PresentationLogStore.LogComponent.Response]
        assertThat(thirdResponseBytes).isNull()

        // verify second entry components
        val secondEntry = entries[1]
        val secondMetadata = secondEntry.getMetadata()
        assertThat(secondMetadata).isNotNull()
        assertThat(secondMetadata!!.presentationTransactionStatus).isEqualTo(secondStatus)
        assertThat(secondMetadata.engagementType).isEqualTo(secondEngagement)
        // verify second entry request
        val secondRequestBytes =
            secondEntry.componentLogs[PresentationLogStore.LogComponent.Request]
        assertThat(secondRequestBytes).isNotNull()
        assertThat(secondRequestBytes).isEqualTo(TestGen.getSampleRequestBytes(secondId))
        // verify second entry response
        val secondResponseBytes =
            secondEntry.componentLogs[PresentationLogStore.LogComponent.Response]
        assertThat(secondResponseBytes).isNull()

        // verify first entry components
        val firstEntry = entries[2]
        val firstMetadata = firstEntry.getMetadata()
        assertThat(firstMetadata).isNotNull()
        assertThat(firstMetadata!!.presentationTransactionStatus).isEqualTo(firstStatus)
        assertThat(firstMetadata.engagementType).isEqualTo(firstEngagement)
        // verify first entry request
        val firstRequestBytes = firstEntry.componentLogs[PresentationLogStore.LogComponent.Request]
        assertThat(firstRequestBytes).isNotNull()
        assertThat(firstRequestBytes).isEqualTo(TestGen.getSampleRequestBytes(firstId))
        // verify first entry response
        val firstResponseBytes =
            firstEntry.componentLogs[PresentationLogStore.LogComponent.Response]
        assertThat(firstResponseBytes).isNotNull()
        assertThat(firstResponseBytes).isEqualTo(TestGen.getSampleResponseBytes(firstId))
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
                EngagementType.NFC
            )

            entryBuilder.metadataBuilder.transactionStartTimestamp(i * 1L)
            presentationLogStore.getMetadataBuilder().transactionEndTimestamp(i * (10L + i))

            presentationLogStore.logResponseData(TestGen.getSampleResponseBytes(i))
            presentationLogStore.persistLogEntryTransactionComplete()
        }

        // get entries in ascending order, delete the first (oldest entry, smallest timestamp)
        val entries = presentationHistoryStore.fetchAllLogEntries().sortedBy { it.id }
        assertThat(entries.size).isEqualTo(3)

        val oldest = entries[0]
        val mid = entries[1]
        val youngest = entries[2]

        // delete first entry
        presentationHistoryStore.deleteLogEntry(oldest.id)

        // get new entries, verify count is 2
        val newEntries = presentationHistoryStore.fetchAllLogEntries().sortedBy { it.id }
        assertThat(newEntries.size).isEqualTo(2)

        // verify the entries that remain are not the oldest entry
        assertThat(newEntries[0].id).isEqualTo(mid.id)
        assertThat(newEntries[1].id).isEqualTo(youngest.id)
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
                EngagementType.NFC
            )

            entryBuilder.metadataBuilder.transactionStartTimestamp(i * 1L)
            presentationLogStore.getMetadataBuilder().transactionEndTimestamp(i * (10L + i))

            presentationLogStore.logResponseData(TestGen.getSampleResponseBytes(i))
            presentationLogStore.persistLogEntryTransactionComplete()
        }

        // get entries in ascending order, delete the first (oldest entry, smallest timestamp)
        val entries = presentationHistoryStore.fetchAllLogEntries().sortedBy { it.id }
        assertThat(entries.size).isEqualTo(3)

        val oldest = entries[0]
        val mid = entries[1]
        val youngest = entries[2]

        // delete 2 oldest entries
        presentationHistoryStore.deleteLogEntry(oldest.id)
        presentationHistoryStore.deleteLogEntry(mid.id)

        // get new entries, verify count is 1
        val newEntries = presentationHistoryStore.fetchAllLogEntries().sortedBy { it.id }
        assertThat(newEntries.size).isEqualTo(1)

        // verify the only remaining entry is the oldest entry
        assertThat(newEntries[0].id).isEqualTo(youngest.id)
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
                EngagementType.NFC
            )
            presentationLogStore.logResponseData(TestGen.getSampleResponseBytes(i))
            presentationLogStore.persistLogEntryTransactionComplete()
        }

        // get entries in ascending order, delete the first (oldest entry, smallest timestamp)
        val entries = presentationHistoryStore.fetchAllLogEntries()
        assertThat(entries.size).isEqualTo(20)

        // delete all logs
        presentationHistoryStore.deleteAllLogs()

        // get new list of logs
        val newEntries = presentationHistoryStore.fetchAllLogEntries()

        // confirm there are no presentation logs persisted in StorageEngine
        assertThat(newEntries.size).isEqualTo(0)
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
                EngagementType.NFC
            )
            presentationLogStore.logResponseData(TestGen.getSampleResponseBytes(i))
            presentationLogStore.persistLogEntryTransactionComplete()
        }

        // confirm there are MAX_ENTRIES_COUNT in StorageEngine
        val entries = presentationHistoryStore.fetchAllLogEntries()
        assertThat(entries.size).isEqualTo(PresentationLogStore.StoreConst.MAX_ENTRIES_COUNT)

        // add 1 more entry
        presentationLogStore.newLogEntryWithRequest(
            TestGen.getSampleRequestBytes(200),
            null,
            EngagementType.NFC
        )
        presentationLogStore.logResponseData(TestGen.getSampleResponseBytes(200))
        presentationLogStore.persistLogEntryTransactionComplete()

        // verify that we still have at most MAX_ENTRIES_COUNT entries in StorageEngine
        val newEntries = presentationHistoryStore.fetchAllLogEntries()
        assertThat(newEntries.size).isEqualTo(PresentationLogStore.StoreConst.MAX_ENTRIES_COUNT)
    }
}