package org.multipaz.trustmanagement

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.buildCborMap
import org.multipaz.crypto.X509Cert
import org.multipaz.mdoc.vical.SignedVical
import org.multipaz.storage.Storage
import org.multipaz.storage.StorageTable
import org.multipaz.storage.StorageTableSpec
import org.multipaz.util.toHex

private data class LocalTrustEntry(
    val id: String,
    val ski: String,
    val timeAdded: Instant,
    val entry: TrustEntry
) {
    fun toDataItem(): DataItem {
        return buildCborMap {
            put("id", id)
            put("ski", ski)
            put("timeAddedSec", timeAdded.epochSeconds)
            put("timeAddedNSec", timeAdded.nanosecondsOfSecond)
            put("entry", entry.toDataItem())
        }
    }

    companion object {
        fun fromDataItem(dataItem: DataItem): LocalTrustEntry {
            val id = dataItem["id"].asTstr
            val ski = dataItem["ski"].asTstr
            val timeAddedSeconds = dataItem["timeAddedSec"].asNumber
            val timeAddedNanoSeconds = dataItem["timeAddedNSec"].asNumber
            val timeAdded = Instant.fromEpochSeconds(timeAddedSeconds, timeAddedNanoSeconds)
            val entry = TrustEntry.fromDataItem(dataItem["entry"])
            return LocalTrustEntry(id, ski, timeAdded, entry)
        }
    }
}

/**
 * An implementation of [TrustManager] using a local persistent store of entries that provide trust points.
 *
 * For management, this includes [addX509Cert], [addVical], [getEntries] and [deleteEntry] methods.
 *
 * @param storage the [Storage] to use.
 * @param identifier an identifier for the [TrustManager].
 * @param partitionId an identifier to use if multiple [TrustManagerLocal] instances share the same [storage].
 */
class TrustManagerLocal(
    private val storage: Storage,
    override val identifier: String = "default",
    private val partitionId: String = "default_$identifier"
): TrustManager {
    private lateinit var storageTable: StorageTable

    private val skiToTrustPoint = mutableMapOf<String, TrustPoint>()
    private val vicalTrustManagers = mutableMapOf<String, VicalTrustManager>()

    private val localEntries = mutableListOf<LocalTrustEntry>()

    private val initializationLock = Mutex()
    private var initializationComplete = false

    private suspend fun ensureInitialized() {
        initializationLock.withLock {
            if (initializationComplete) {
                return
            }
            storageTable = storage.getTable(tableSpec)
            for ((key, encodedData) in storageTable.enumerateWithData(partitionId = partitionId)) {
                val localEntry = LocalTrustEntry.fromDataItem(Cbor.decode(encodedData.toByteArray()))
                when (localEntry.entry) {
                    is TrustEntryX509Cert -> {
                        skiToTrustPoint[localEntry.ski] = TrustPoint(
                            certificate = localEntry.entry.certificate,
                            metadata = localEntry.entry.metadata,
                            trustManager = this
                        )
                    }
                    is TrustEntryVical -> {
                        // We assume the caller already verified the signature before adding the VICAL
                        val signedVical = SignedVical.parse(
                            encodedSignedVical = localEntry.entry.encodedSignedVical.toByteArray(),
                            disableSignatureVerification = true
                        )
                        vicalTrustManagers.put(key, VicalTrustManager(signedVical))
                    }
                }
                localEntries.add(localEntry)
            }
            initializationComplete = true
        }
    }

    override suspend fun getTrustPoints(): List<TrustPoint> {
        ensureInitialized()
        val ret = mutableListOf<TrustPoint>()
        ret.addAll(skiToTrustPoint.values)
        vicalTrustManagers.forEach { ret.addAll(it.value.getTrustPoints()) }
        return ret
    }

    /**
     * Gets a list of all entries added with [addX509Cert] or [addVical].
     *
     * @return a list, sorted by the time they were added.
     */
    suspend fun getEntries(): List<TrustEntry> {
        ensureInitialized()
        return localEntries.sortedBy { it.timeAdded }.map { it.entry }
    }

    /**
     * Adds a new entry for CAs identified by a X.509 certificate.
     *
     * The entry will be persisted in the [Storage] passed at construction time.
     *
     * The SubjectKeyIdentifier of [certificate] is used to identify the certificate.
     *
     * @param certificate the root X509 certificate for the CA, must have SubjectKeyIdentifier set.
     * @param metadata the metadata for the trust point.
     * @return the [TrustEntryX509Cert] which was added.
     * @throws TrustPointAlreadyExistsException if there already is another trust point with the same SubjectKeyIdentifier.
     */
    suspend fun addX509Cert(
        certificate: X509Cert,
        metadata: TrustMetadata,
    ): TrustEntryX509Cert {
        ensureInitialized()
        val trustPoint = TrustPoint(
            certificate = certificate,
            metadata = metadata,
            trustManager = this
        )
        val ski = trustPoint.certificate.subjectKeyIdentifier?.toHex()
        require(ski != null) { "SubjectKeyIdentifier must be set in certificate for TrustPoint" }
        if (skiToTrustPoint.containsKey(ski)) {
            throw TrustPointAlreadyExistsException("TrustPoint with given SubjectKeyIdentifier already exists")
        }
        val key = storageTable.insert(
            key = null,
            data = ByteString(),
            partitionId = partitionId,
        )
        val localEntry = LocalTrustEntry(
            id = key,
            ski = ski,
            timeAdded = Clock.System.now(),
            entry = TrustEntryX509Cert(
                certificate = certificate,
                metadata = metadata
            )
        )
        storageTable.update(
            key = key,
            data = ByteString(Cbor.encode(localEntry.toDataItem())),
            partitionId = partitionId,
        )
        skiToTrustPoint[ski] = trustPoint
        localEntries.add(localEntry)
        return localEntry.entry as TrustEntryX509Cert
    }

    /**
     * Adds a new entry with a signed VICAL.
     *
     * The signature will NOT be checked so make sure to check the Signed VICAL is from a trusted party
     * before adding it.
     *
     * @param encodedSignedVical the bytes of the signed VICAL.
     * @return a [TrustEntryVical].
     */
    suspend fun addVical(
        encodedSignedVical: ByteString,
        metadata: TrustMetadata,
    ): TrustEntryVical {
        ensureInitialized()
        // We assume the caller already verified the signature before adding the Vical...
        val signedVical = SignedVical.parse(
            encodedSignedVical = encodedSignedVical.toByteArray(),
            disableSignatureVerification = true
        )
        val key = storageTable.insert(
            key = null,
            data = ByteString(),
            partitionId = partitionId,
        )
        val localEntry = LocalTrustEntry(
            id = key,
            ski = "",
            timeAdded = Clock.System.now(),
            entry = TrustEntryVical(
                encodedSignedVical = encodedSignedVical,
                metadata = metadata
            )
        )
        storageTable.update(
            key = key,
            data = ByteString(Cbor.encode(localEntry.toDataItem())),
            partitionId = partitionId,
        )
        vicalTrustManagers.put(key, VicalTrustManager(signedVical))
        localEntries.add(localEntry)
        return localEntry.entry as TrustEntryVical
    }

    /**
     * Removes a [TrustEntry] previously added with [addX509Cert] or [addVical].
     *
     * @param entry the [TrustPoint] to remove.
     * @return `true` if the trust entry was found and removed, `false` otherwise.
     */
    suspend fun deleteEntry(entry: TrustEntry): Boolean {
        ensureInitialized()
        // TODO: this could be made faster using e.g. a lookup hashtable but O(n) is fine
        val localEntry = localEntries.find { it.entry == entry }
            ?: return false
        when (localEntry.entry) {
            is TrustEntryX509Cert -> {
                storageTable.delete(
                    key = localEntry.id,
                    partitionId = partitionId,
                )
                localEntries.remove(localEntry)
                return skiToTrustPoint.remove(localEntry.ski) != null
            }
            is TrustEntryVical -> {
                storageTable.delete(
                    key = localEntry.id,
                    partitionId = partitionId,
                )
                localEntries.remove(localEntry)
                return vicalTrustManagers.remove(localEntry.id) != null
            }
        }
    }

    /**
     * Deletes all entries.
     */
    suspend fun deleteAll() {
        ensureInitialized()
        storageTable.deletePartition(partitionId = partitionId)
        localEntries.clear()
        skiToTrustPoint.clear()
        vicalTrustManagers.clear()
    }

    /**
     * Updates metadata for an entry.
     *
     * @param entry the entry to update.
     * @param metadata the new metadata to use.
     * @return the new [TrustEntry]
     * @throws IllegalStateException if the trust manager doesn't contain [entry].
     */
    suspend fun updateMetadata(
        entry: TrustEntry,
        metadata: TrustMetadata
    ): TrustEntry {
        ensureInitialized()

        // TODO: this could be made faster using e.g. a lookup hashtable but O(n) is fine
        val localEntry = localEntries.find { it.entry == entry }
            ?: throw IllegalStateException("Trust Manager does not contain entry")

        val newEntry = when (localEntry.entry) {
            is TrustEntryX509Cert -> {
                val e = TrustEntryX509Cert(
                    metadata = metadata,
                    certificate = localEntry.entry.certificate
                )
                skiToTrustPoint[localEntry.ski] = TrustPoint(
                    certificate = e.certificate,
                    metadata = e.metadata,
                    trustManager = this
                )
                e
            }
            is TrustEntryVical -> {
                TrustEntryVical(
                    metadata = metadata,
                    encodedSignedVical = localEntry.entry.encodedSignedVical
                )
            }
        }

        val newLocalEntry = LocalTrustEntry(
            id = localEntry.id,
            timeAdded = localEntry.timeAdded,
            ski = localEntry.ski,
            entry = newEntry
        )
        storageTable.update(
            key = localEntry.id,
            data = ByteString(Cbor.encode(newLocalEntry.toDataItem())),
            partitionId = partitionId,
        )
        localEntries.remove(localEntry)
        localEntries.add(newLocalEntry)

        return newLocalEntry.entry
    }

    override suspend fun verify(
        chain: List<X509Cert>,
        atTime: Instant,
    ): TrustResult {
        ensureInitialized()
        // VICAL trust managers get first dibs...
        vicalTrustManagers.forEach { (_, trustManager) ->
            val ret = trustManager.verify(chain, atTime)
            if (ret.isTrusted) {
                return ret
            }
        }
        return TrustManagerUtil.verifyX509TrustChain(chain, atTime, skiToTrustPoint)
    }

    companion object {
        private const val TAG = "LocalTrustManager"

        private val tableSpec = StorageTableSpec(
            name = "LocalTrustManager",
            supportPartitions = true,
            supportExpiration = false,
            schemaVersion = 1L
        )
    }
}

