package org.multipaz.trustmanagement

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.buildCborMap
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509CertChain
import org.multipaz.storage.Storage
import org.multipaz.storage.StorageTable
import org.multipaz.storage.StorageTableSpec
import org.multipaz.util.toHex
import kotlin.collections.containsKey
import kotlin.collections.get


/**
 * An implementation of [TrustManager] using a local persistent store of root certificates.
 *
 * Use [create] to create an instance.
 */
class LocalTrustManager private constructor(
    private val storageTable: StorageTable,
    private val partitionId: String
): TrustManager {

    private val skiToTrustPoint = mutableMapOf<String, TrustPoint>()

    // called by create()
    private suspend fun initialize() {
        val t0 = Clock.System.now()
        var parsing = t0 - t0
        for (ski in storageTable.enumerate(partitionId = partitionId)) {
            val encodedData = storageTable.get(
                key = ski,
                partitionId = partitionId
            )
            val tt0 = Clock.System.now()
            val trustPoint = Cbor.decode(encodedData!!.toByteArray()).toTrustPoint()
            val tt1 = Clock.System.now()
            parsing = parsing + (tt1 - tt0)
            skiToTrustPoint[ski] = trustPoint
        }
        val t1 = Clock.System.now()
        println("initialize took ${t1 - t0}, parsing $parsing")
    }

    /**
     * Adds a new trust point.
     *
     * The certificate in the given [TrustPoint] must have a valid Subject Key Identifier.
     *
     * The trust point will be persisted in the [Storage] passed to [create].
     *
     * @param trustPoint the [TrustPoint] to add.
     * @throws TrustPointAlreadyExistsException if there already is an identifier for the trust point in the database or
     *   if there already is another trust point with the same SubjectKeyIdentifier.
     */
    suspend fun addTrustPoint(
        trustPoint: TrustPoint
    ) {
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
    }

    /**
     * Gets all trust points previously added with [addTrustPoint].
     *
     * @return the Subject Key Identifiers for trust points previously added with [addTrustPoint].
     */
    fun getTrustPointSkis(): Set<String> {
        return skiToTrustPoint.keys
    }

    /**
     * Gets the trust point previously added with [addTrustPoint].
     *
     * @param ski the Subject Key Identifier for the trust point.
     * @return a [TrustPoint] for the Subject Key Identifier or `null` if no such trust point exists.
     */
    fun getTrustPoint(ski: String): TrustPoint? {
        return skiToTrustPoint[ski]
    }

    /**
     * Deletes a trust point previously added with [addTrustPoint].
     *
     * @param ski the Subject Key Identifier for the trust point to delete.
     * @return `true` if the trust point was found and deleted, `false` otherwise.
     */
    suspend fun deleteTrustPoint(ski: String): Boolean {
        storageTable.delete(
            key = ski,
            partitionId = partitionId,
        )
        return skiToTrustPoint.remove(ski) != null
    }

    override suspend fun verify(
        chain: List<X509Cert>,
        atTime: Instant,
    ): TrustResult {
        // TODO: add support for customValidators similar to PKIXCertPathChecker
        try {
            val trustPoints = getAllTrustPoints(chain)
            val completeChain = chain.plus(trustPoints.map { it.certificate })
            try {
                validateCertificationTrustPath(completeChain, atTime)
                return TrustResult(
                    isTrusted = true,
                    trustPoints = trustPoints,
                    trustChain = X509CertChain(completeChain)
                )
            } catch (e: Throwable) {
                // there are validation errors, but the trust chain could be built.
                return TrustResult(
                    isTrusted = false,
                    trustPoints = trustPoints,
                    trustChain = X509CertChain(completeChain),
                    error = e
                )
            }
        } catch (e: Throwable) {
            // No CA certificate found for the passed in chain.
            //
            // However, handle the case where the passed in chain _is_ a trust point. This won't
            // happen for mdoc issuer auth (the IACA cert is never part of the chain) but can happen
            // with mdoc reader auth, especially at mDL test events where each participant
            // just submits a certificate for the key that their reader will be using.
            //
            if (chain.size == 1) {
                val trustPoint = skiToTrustPoint[chain[0].subjectKeyIdentifier!!.toHex()]
                if (trustPoint != null) {
                    return TrustResult(
                        isTrusted = true,
                        trustChain = X509CertChain(chain),
                        listOf(trustPoint),
                        error = null
                    )
                }
            }
            // no CA certificate could be found.
            return TrustResult(
                isTrusted = false,
                error = e
            )
        }
    }

    private fun getAllTrustPoints(chain: List<X509Cert>): List<TrustPoint> {
        val result = mutableListOf<TrustPoint>()

        // only an exception if not a single CA certificate is found
        var caCertificate: TrustPoint? = findCaCertificate(chain)
            ?: throw IllegalStateException("No trusted root certificate could not be found")
        result.add(caCertificate!!)
        while (caCertificate != null && !TrustManagerUtil.isSelfSigned(caCertificate.certificate)) {
            caCertificate = findCaCertificate(listOf(caCertificate.certificate))
            if (caCertificate != null) {
                result.add(caCertificate)
            }
        }
        return result
    }

    /**
     * Find a CA Certificate for a certificate chain.
     */
    private fun findCaCertificate(chain: List<X509Cert>): TrustPoint? {
        chain.forEach { cert ->
            cert.authorityKeyIdentifier?.toHex().let {
                if (skiToTrustPoint.containsKey(it)) {
                    return skiToTrustPoint[it]
                }
            }
        }
        return null
    }

    /**
     * Validate the certificate trust path.
     */
    private fun validateCertificationTrustPath(
        certificationTrustPath: List<X509Cert>,
        atTime: Instant
    ) {
        val certIterator = certificationTrustPath.iterator()
        val leafCertificate = certIterator.next()
        TrustManagerUtil.checkKeyUsageDocumentSigner(leafCertificate)
        TrustManagerUtil.checkValidity(leafCertificate, atTime)

        var previousCertificate = leafCertificate
        var caCertificate: X509Cert? = null
        while (certIterator.hasNext()) {
            caCertificate = certIterator.next()
            TrustManagerUtil.checkKeyUsageCaCertificate(caCertificate)
            TrustManagerUtil.checkCaIsIssuer(previousCertificate, caCertificate)
            TrustManagerUtil.verifySignature(previousCertificate, caCertificate)
            previousCertificate = caCertificate
        }
        if (caCertificate != null && TrustManagerUtil.isSelfSigned(caCertificate)) {
            // check the signature of the self signed root certificate
            TrustManagerUtil.verifySignature(caCertificate, caCertificate)
        }
    }

    companion object {
        private val tableSpec = StorageTableSpec(
            name = "LocalTrustManager",
            supportPartitions = true,
            supportExpiration = false
        )

        /**
         * Creates a new [LocalTrustManager] instance.
         *
         * This loads trust points from [storage] previously added with [addTrustPoint].
         *
         * @param storage the [Storage] to use.
         * @param partitionId an identifier to use if multiple [LocalTrustManager] instances share the same [storage].
         * @return a [LocalTrustManager] instance.
         */
        suspend fun create(
            storage: Storage,
            partitionId: String = "default"
        ): LocalTrustManager {
            val trustManager = LocalTrustManager(storage.getTable(tableSpec), partitionId)
            trustManager.initialize()
            return trustManager
        }
    }
}

// TODO: Make TrustPoint serializable
//
private fun TrustPoint.toDataItem(): DataItem {
    return buildCborMap {
        put("certificate", certificate.toDataItem())
        displayName?.let { put("displayName", it) }
        displayIcon?.let { put("displayIcon", it.toByteArray()) }
    }
}

private fun DataItem.toTrustPoint(): TrustPoint {
    return TrustPoint(
        certificate = get("certificate").asX509Cert,
        displayName = getOrNull("displayName")?.asTstr,
        displayIcon = getOrNull("displayIcon")?.asBstr?.let { ByteString(it) }
    )
}