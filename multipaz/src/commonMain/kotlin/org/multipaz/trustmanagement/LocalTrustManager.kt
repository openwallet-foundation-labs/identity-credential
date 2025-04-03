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
import org.multipaz.storage.Storage
import org.multipaz.storage.StorageTable
import org.multipaz.storage.StorageTableSpec
import org.multipaz.util.Logger
import org.multipaz.util.toHex


// Unfortunately we can't make TrustPoint @CborSerializable right now
private fun TrustPoint.toDataItem(): DataItem {
    return buildCborMap {
        when (this@toDataItem) {
            is X509CertTrustPoint -> {
                put("type", "X509CertTrustPoint")
                put("certificate", certificate.toDataItem())
            }
            is OriginTrustPoint -> {
                put("type", "OriginTrustPoint")
                put("origin", origin)
            }
        }
        put("metadata", metadata.toDataItem())
    }
}

private fun TrustPoint.Companion.fromDataItem(dataItem: DataItem, trustManager: TrustManager): TrustPoint {
    val type = dataItem["type"].asTstr
    return when (type) {
        "X509CertTrustPoint" -> X509CertTrustPoint(
            certificate = X509Cert.fromDataItem(dataItem["certificate"]),
            metadata = TrustPointMetadata.fromDataItem(dataItem["metadata"]),
            trustManager = trustManager
        )
        "OriginTrustPoint" -> OriginTrustPoint(
            origin = dataItem["origin"].asTstr,
            metadata = TrustPointMetadata.fromDataItem(dataItem["metadata"]),
            trustManager = trustManager
        )
        else -> throw IllegalStateException("Unexpected type $type")
    }
}

/**
 * An implementation of [TrustManager] using a local persistent store of trust points.
 *
 * For management, this includes [addTrustPoint] and [deleteTrustPoint] methods.
 *
 * @param storage the [Storage] to use.
 * @param partitionId an identifier to use if multiple [LocalTrustManager] instances share the same [storage].
 * @param identifier an identity for the Trust Manager for use by the application.
 */
class LocalTrustManager(
    private val storage: Storage,
    private val partitionId: String = "default",
    val identifier: String = ""
): TrustManager {
    private lateinit var storageTable: StorageTable

    private val skiToTrustPoint = mutableMapOf<String, X509CertTrustPoint>()
    private val webOriginToTrustPoint = mutableMapOf<String, OriginTrustPoint>()

    private val initializationLock = Mutex()
    private var initializationComplete = false

    private suspend fun ensureInitialized() {
        initializationLock.withLock {
            if (initializationComplete) {
                return
            }
            storageTable = storage.getTable(tableSpec)
            val t0 = Clock.System.now()
            for ((key, encodedData) in storageTable.enumerateWithData(partitionId = partitionId)) {
                val trustPoint = TrustPoint.Companion.fromDataItem(Cbor.decode(encodedData.toByteArray()), this)
                when (trustPoint) {
                    is X509CertTrustPoint -> skiToTrustPoint[key] = trustPoint
                    is OriginTrustPoint -> webOriginToTrustPoint[key] = trustPoint
                }

            }
            val t1 = Clock.System.now()
            Logger.i(TAG, "for ${skiToTrustPoint.size} rows, initialize() took ${t1 - t0}")
            initializationComplete = true
        }
    }

    override suspend fun getTrustPoints(): List<TrustPoint> {
        ensureInitialized()
        return (skiToTrustPoint.values + webOriginToTrustPoint.values).toList()
    }

    /**
     * Adds a new trust point for CAs identified by a X.509 certificate.
     *
     * The trust point will be persisted in the [Storage] passed at construction time.
     *
     * The SubjectKeyIdentifier of [certificate] is used to identify the certificate.
     *
     * @param certificate the root X509 certificate for the CA, must have SubjectKeyIdentifier set.
     * @param metadata the metadata for the trust point.
     * @return the [X509CertTrustPoint] which was added.
     * @throws TrustPointAlreadyExistsException if there already is another trust point with the same SubjectKeyIdentifier.
     */
    suspend fun addTrustPoint(
        certificate: X509Cert,
        metadata: TrustPointMetadata,
    ): X509CertTrustPoint {
        ensureInitialized()
        val trustPoint = X509CertTrustPoint(certificate, metadata, this)
        val ski = trustPoint.certificate.subjectKeyIdentifier?.toHex()
        require(ski != null) { "SubjectKeyIdentifier must be set in certificate for TrustPoint" }
        if (skiToTrustPoint.containsKey(ski)) {
            throw TrustPointAlreadyExistsException("TrustPoint with given SubjectKeyIdentifier already exists")
        }
        storageTable.insert(
            key = ski,
            data = ByteString(Cbor.encode(trustPoint.toDataItem())),
            partitionId = partitionId,
        )
        skiToTrustPoint[ski] = trustPoint
        return trustPoint
    }

    /**
     * Adds a new trust point for CAs identified by a web origin.
     *
     * The trust point will be persisted in the [Storage] passed at construction time.
     *
     * @param origin the origin, e.g. https://verifier.multipaz.org.
     * @param metadata the metadata for the trust point.
     * @return the [OriginTrustPoint] which was added.
     * @throws TrustPointAlreadyExistsException if there already is another trust point with the same origin.
     */
    suspend fun addTrustPoint(
        origin: String,
        metadata: TrustPointMetadata,
    ): OriginTrustPoint {
        ensureInitialized()
        val trustPoint = OriginTrustPoint(origin, metadata, this)
        if (webOriginToTrustPoint.containsKey(trustPoint.origin)) {
            throw TrustPointAlreadyExistsException("TrustPoint with given origin already exists")
        }
        storageTable.insert(
            key = trustPoint.origin,
            data = ByteString(Cbor.encode(trustPoint.toDataItem())),
            partitionId = partitionId,
        )
        webOriginToTrustPoint[trustPoint.origin] = trustPoint
        return trustPoint
    }

    /**
     * Removes a [TrustPoint] previously added.
     *
     * @param trustPoint the [TrustPoint] to remove.
     * @return `true` if the trust point was found and removed, `false` otherwise.
     */
    suspend fun deleteTrustPoint(trustPoint: TrustPoint): Boolean {
        ensureInitialized()
        val (key, table) = when (trustPoint) {
            is X509CertTrustPoint -> {
                Pair(trustPoint.certificate.subjectKeyIdentifier!!.toHex(), skiToTrustPoint)
            }
            is OriginTrustPoint -> Pair(trustPoint.origin, webOriginToTrustPoint)
        }
        storageTable.delete(
            key = key,
            partitionId = partitionId,
        )
        return table.remove(key) != null
    }

    override suspend fun verify(
        chain: List<X509Cert>,
        atTime: Instant,
    ): TrustResult {
        ensureInitialized()
        return TrustManagerUtil.verifyX509TrustChain(chain, atTime, skiToTrustPoint)
    }

    override suspend fun verify(origin: String, atTime: Instant): TrustResult {
        ensureInitialized()
        val trustPoint = webOriginToTrustPoint[origin]
        if (trustPoint == null) {
            return TrustResult(
                isTrusted = false,
                error = IllegalStateException("No trusted origin could not be found")
            )
        }
        return TrustResult(
            isTrusted = true,
            trustChain = null,
            trustPoints = listOf(trustPoint),
        )
    }

    companion object {
        private const val TAG = "LocalTrustManager"

        private val tableSpec = StorageTableSpec(
            name = "LocalTrustManager",
            supportPartitions = true,
            supportExpiration = false
        )
    }
}

