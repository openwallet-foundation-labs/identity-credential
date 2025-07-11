package org.multipaz.trustmanagement

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.buildCborMap
import org.multipaz.crypto.X509Cert
import org.multipaz.mdoc.vical.SignedVical
import org.multipaz.storage.Storage
import org.multipaz.storage.StorageTable
import org.multipaz.storage.StorageTableSpec
import org.multipaz.util.Logger
import org.multipaz.util.toHex


private fun TrustEntry.toDataItem(): DataItem {
    return buildCborMap {
        put("timeAddedSec", timeAdded.epochSeconds)
        put("timeAddedNSec", timeAdded.nanosecondsOfSecond)
        put("metadata", metadata.toDataItem())
        when (this@toDataItem) {
            is TrustEntryX509Cert -> {
                put("type", "X509Cert")
                put("ski", ski)
                put("certificate", certificate.toDataItem())
            }
            is TrustEntryVical -> {
                put("type", "Vical")
                put("numCertificates", numCertificates)
                put("data", encodedSignedVical.toByteArray())
            }
        }
    }
}

private fun TrustEntry.Companion.fromDataItem(
    dataItem: DataItem,
    key: String,
): TrustEntry {
    val type = dataItem["type"].asTstr
    val timeAddedSeconds = dataItem["timeAddedSec"].asNumber
    val timeAddedNanoSeconds = dataItem["timeAddedNSec"].asNumber
    val timeAdded = Instant.fromEpochSeconds(timeAddedSeconds, timeAddedNanoSeconds)
    val metadata = TrustMetadata.fromDataItem(dataItem["metadata"])
    when (type) {
        "X509Cert" -> {
            val ski = dataItem["ski"].asTstr
            val certificate = X509Cert.fromDataItem(dataItem["certificate"])
            return TrustEntryX509Cert(key, timeAdded, metadata, ski, certificate)
        }
        "Vical" -> {
            val numCertificates = dataItem["numCertificates"].asNumber.toInt()
            val encodedSignedVical = ByteString(dataItem["data"].asBstr)
            return TrustEntryVical(key, timeAdded, metadata, numCertificates, encodedSignedVical)
        }
        else -> throw IllegalStateException("Unexpected type $type")
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
    private val vicalEntryToKey = mutableMapOf<TrustEntryVical, String>()

    private val entries = mutableListOf<TrustEntry>()

    private val initializationLock = Mutex()
    private var initializationComplete = false

    private suspend fun ensureInitialized() {
        initializationLock.withLock {
            if (initializationComplete) {
                return
            }
            storageTable = storage.getTable(tableSpec)
            for ((key, encodedData) in storageTable.enumerateWithData(partitionId = partitionId)) {
                val entry = TrustEntry.fromDataItem(Cbor.decode(encodedData.toByteArray()), key)
                when (entry) {
                    is TrustEntryX509Cert -> {
                        skiToTrustPoint[entry.ski] = TrustPoint(
                            certificate = entry.certificate,
                            metadata = entry.metadata,
                            trustManager = this
                        )
                    }
                    is TrustEntryVical -> {
                        // We assume the caller already verified the signature before adding the VICAL
                        val signedVical = SignedVical.parse(
                            encodedSignedVical = entry.encodedSignedVical.toByteArray(),
                            disableSignatureVerification = true
                        )
                        vicalTrustManagers.put(key, VicalTrustManager(signedVical))
                        vicalEntryToKey.put(entry, key)
                    }
                }
                entries.add(entry)
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
     * @return a list, sorted by the [TrustEntry.timeAdded] field.
     */
    suspend fun getEntries(): List<TrustEntry> {
        ensureInitialized()
        return entries.sortedBy { it.timeAdded }
    }

    /**
     * Gets a [TrustEntry] by id.
     *
     * @param id the id of the entry to get
     * @return the [TrustEntry]
     * @throws IllegalArgumentException if there is no entry with the given id
     */
    suspend fun getEntry(id: String): TrustEntry {
        return getEntries().find { it.id == id }
            ?: throw IllegalArgumentException("No entry with given id")
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
        val entry = TrustEntryX509Cert(
            id = key,
            timeAdded = Clock.System.now(),
            ski = ski,
            certificate = certificate,
            metadata = metadata
        )
        storageTable.update(
            key = key,
            data = ByteString(Cbor.encode(entry.toDataItem())),
            partitionId = partitionId,
        )
        skiToTrustPoint[ski] = trustPoint
        entries.add(entry)
        return entry
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
        val entry = TrustEntryVical(
            id = key,
            timeAdded = Clock.System.now(),
            numCertificates = signedVical.vical.certificateInfos.size,
            encodedSignedVical = encodedSignedVical,
            metadata = metadata
        )
        storageTable.update(
            key = key,
            data = ByteString(Cbor.encode(entry.toDataItem())),
            partitionId = partitionId,
        )
        vicalTrustManagers.put(key, VicalTrustManager(signedVical))
        vicalEntryToKey.put(entry, key)
        entries.add(entry)
        return entry
    }

    /**
     * Removes a [TrustEntry] previously added with [addX509Cert] or [addVical].
     *
     * @param entry the [TrustPoint] to remove.
     * @return `true` if the trust entry was found and removed, `false` otherwise.
     */
    suspend fun deleteEntry(entry: TrustEntry): Boolean {
        ensureInitialized()
        if (!entries.contains(entry)) {
            return false
        }
        when (entry) {
            is TrustEntryX509Cert -> {
                storageTable.delete(
                    key = entry.id,
                    partitionId = partitionId,
                )
                entries.remove(entry)
                return skiToTrustPoint.remove(entry.ski) != null
            }
            is TrustEntryVical -> {
                val key = vicalEntryToKey[entry]!!
                storageTable.delete(
                    key = key,
                    partitionId = partitionId,
                )
                entries.remove(entry)
                vicalEntryToKey.remove(entry)
                return vicalTrustManagers.remove(key) != null
            }
        }
    }

    /**
     * Updates metadata for an entry.
     *
     * @param entry the entry to update.
     * @param metadata the new metadata to use.
     * @return the new [TrustEntry]
     */
    suspend fun updateMetadata(
        entry: TrustEntry,
        metadata: TrustMetadata
    ): TrustEntry {
        ensureInitialized()
        check(entries.contains(entry))
        when (entry) {
            is TrustEntryX509Cert -> {
                val newEntry = TrustEntryX509Cert(
                    id = entry.id,
                    timeAdded = Clock.System.now(),
                    ski = entry.ski,
                    certificate = entry.certificate,
                    metadata = metadata
                )
                storageTable.update(
                    key = entry.id,
                    data = ByteString(Cbor.encode(newEntry.toDataItem())),
                    partitionId = partitionId,
                )
                entries.remove(entry)
                entries.add(newEntry)
                skiToTrustPoint[newEntry.ski] = TrustPoint(
                    certificate = newEntry.certificate,
                    metadata = newEntry.metadata,
                    trustManager = this
                )
                return newEntry
            }
            is TrustEntryVical -> {
                val newEntry = TrustEntryVical(
                    id = entry.id,
                    timeAdded = Clock.System.now(),
                    numCertificates = entry.numCertificates,
                    encodedSignedVical = entry.encodedSignedVical,
                    metadata = metadata
                )
                storageTable.update(
                    key = entry.id,
                    data = ByteString(Cbor.encode(newEntry.toDataItem())),
                    partitionId = partitionId,
                )
                entries.remove(entry)
                entries.add(newEntry)
                vicalEntryToKey.put(newEntry, newEntry.id)
                return newEntry
            }
        }
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

