package org.multipaz.mdoc.zkp.longfellow

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborArray
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.buildCborMap
import org.multipaz.crypto.EcPublicKeyDoubleCoordinate
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509CertChain
import org.multipaz.util.Constants
import org.multipaz.util.Logger
import org.multipaz.mdoc.zkp.ProofVerificationFailureException
import org.multipaz.mdoc.zkp.ZkDocument
import org.multipaz.mdoc.zkp.ZkDocumentData
import org.multipaz.mdoc.zkp.ZkSystem
import org.multipaz.mdoc.zkp.ZkSystemSpec
import org.multipaz.request.MdocRequest
import java.io.IOException
import kotlinx.datetime.Instant
import java.io.File
import java.io.FileOutputStream

/**
 * Abstract base class for Longfellow-based ZK systems implementing [ZkSystem].
 *
 * Provides core logic for proof generation and verification using native Longfellow
 * libraries. Circuit files are expected to be located under `circuits/longfellow-libzk-v1/` and named
 * with the format: `<version>_<numAttributes>_<circuitHash>`.
 */
abstract class LongfellowZkSystemBase(): ZkSystem {
    protected abstract fun getAllCircuitFileNames(): List<String>
    protected abstract fun loadCircuit(circuitFileName: String): ByteString

    companion object {
        private const val TAG = "LongfellowZkSystem"
    }

    override val name: String
        get() = "longfellow-libzk-v1"

    private fun getFormattedCoordinate(value: ByteArray): String {
        return ("0x" + value.joinToString("") {String.format("%02x", it)})
    }

    private fun formatDate(timestamp: Instant): String {
        return timestamp.toLocalDateTime(TimeZone.UTC).format(LocalDateTime.Formats.ISO)
    }

    private fun getLongfellowZkSystemSpec(zkSystemSpec: ZkSystemSpec): LongfellowZkSystemSpec {
        val version: Long = zkSystemSpec.getParam("version")
            ?: throw IllegalArgumentException("Missing or invalid 'version' in systemSpec.params")
        val numAttributes: Long = zkSystemSpec.getParam("numAttributes")
            ?: throw IllegalArgumentException("Missing or invalid 'numAttributes' in systemSpec.params")
        val circuitHash: String = zkSystemSpec.getParam("circuitHash")
            ?: throw IllegalArgumentException("Missing or invalid 'circuitHash' in systemSpec.params")

        return LongfellowZkSystemSpec(
            system=name,
            circuitHash=circuitHash,
            numAttributes=numAttributes,
            version=version
        )
    }

    private fun getCircuitBytes(zkSystemSpec: LongfellowZkSystemSpec): ByteString {
        val circuitName = "${zkSystemSpec.version}_${zkSystemSpec.numAttributes}_${zkSystemSpec.circuitHash}"
        return loadCircuit(circuitName)
    }

    open fun getCurrentTimestamp(): Instant {
        return Clock.System.now()
    }

    /**
     * Generates a [ZkDocument] containing a zero-knowledge proof from an mdoc input.
     *
     * @param zkSystemSpec the system spec describing the circuit to use
     * @param document the encoded mdoc document
     * @param encodedSessionTranscript the session transcript
     * @return a [ZkDocument] containing the generated proof and metadata
     */
    override fun generateProof(
        zkSystemSpec: ZkSystemSpec,
        document: ByteString,
        encodedSessionTranscript: ByteString
    ): ZkDocument {
        val longfellowZkSystemSpec = getLongfellowZkSystemSpec(zkSystemSpec)
        val circuitBytes = getCircuitBytes(longfellowZkSystemSpec)

        val doc = Cbor.decode(document.toByteArray())

        // The Longfellow ZKP library expects this format, and will grab the 1st document
        // in the array.
        val longfellowDocBytes = Cbor.encode(
            buildCborMap {
                put("version", "1.0")
                put("documents", CborArray.builder().add(doc).end().build())
                put("status", Constants.DEVICE_RESPONSE_STATUS_OK)
            }
        )

        val docType = doc["docType"].asTstr

        val issuerCert = X509Cert(doc["issuerSigned"]["issuerAuth"][1][33].asBstr)
        val ecPubKeyCoordinates = issuerCert.ecPublicKey as EcPublicKeyDoubleCoordinate
        val x = getFormattedCoordinate(ecPubKeyCoordinates.x)
        val y = getFormattedCoordinate(ecPubKeyCoordinates.y)

        val issuerItems = mutableListOf<DataItem>()
        val attributes = mutableListOf<NativeAttribute>()
        for ((_, v) in doc["issuerSigned"]["nameSpaces"].asMap) {
            for (attr in v.asArray) {
                issuerItems.add(attr)
                attributes.add(NativeAttribute.fromDataItem(attr))
            }
        }

        val timestamp = getCurrentTimestamp()

        val proof = LongfellowNatives.runMdocProver(
            circuitBytes,
            circuitBytes.size,
            ByteString(longfellowDocBytes),
            longfellowDocBytes.size,
            x,
            y,
            encodedSessionTranscript,
            encodedSessionTranscript.size,
            formatDate(timestamp),
            longfellowZkSystemSpec,
            attributes
        )

        val zkDocument = ZkDocument(
            proof=ByteString(proof),
            zkDocumentData = ZkDocumentData (
                zkSystemSpecId = zkSystemSpec.id,
                docType = docType,
                timestamp = timestamp,
                issuerSignedItems = issuerItems,
                deviceSignedItems = listOf(),
                msoX5chain = X509CertChain(listOf(issuerCert)),
            )
        )

        return zkDocument
    }

    /**
     * Verifies a zero-knowledge proof in a [ZkDocument] against the session transcript and circuit spec.
     *
     * @throws IllegalArgumentException if the ZkDocument lacks required certificate data
     * @throws ProofVerificationFailureException if the proof is invalid
     */
    override fun verifyProof(zkDocument: ZkDocument, zkSystemSpec: ZkSystemSpec, encodedSessionTranscript: ByteString) {
        if (zkDocument.zkDocumentData.msoX5chain == null || zkDocument.zkDocumentData.msoX5chain!!.certificates.isEmpty()) {
            throw IllegalArgumentException("zkDocument must contain at least 1 certificate in msoX5chain.")
        }

        val cert = zkDocument.zkDocumentData.msoX5chain!!.certificates[0]
        val ecPubKeyCoordinates = cert.ecPublicKey as EcPublicKeyDoubleCoordinate
        val x = getFormattedCoordinate(ecPubKeyCoordinates.x)
        val y = getFormattedCoordinate(ecPubKeyCoordinates.y)

        val longfellowZkSystemSpec = getLongfellowZkSystemSpec(zkSystemSpec)
        val circuitBytes = getCircuitBytes(longfellowZkSystemSpec)
        val attributes = zkDocument.zkDocumentData.issuerSignedItems.map{ NativeAttribute.fromDataItem(it) }

        val verifierResult = LongfellowNatives.runMdocVerifier(
            circuitBytes,
            circuitBytes.size,
            x,
            y,
            encodedSessionTranscript,
            encodedSessionTranscript.size,
            formatDate(zkDocument.zkDocumentData.timestamp),
            zkDocument.proof,
            zkDocument.proof.size,
            zkDocument.zkDocumentData.docType,
            longfellowZkSystemSpec,
            attributes.toTypedArray()
        )

        Logger.i(TAG, "Verification Code: ${verifierResult}")

        if (verifierResult != VerifierCodeEnum.MDOC_VERIFIER_SUCCESS.value) {
            val verifierCodeEnum = VerifierCodeEnum.fromInt(verifierResult)
            throw ProofVerificationFailureException("Verification failed with error: $verifierCodeEnum")
        }
    }

    /**
     * Enumerates all valid [ZkSystemSpec]s for this implementation by parsing available circuit file names.
     *
     * @return a list of [ZkSystemSpec]s representing available proof configurations
     * @throws IOException if the circuits directory is missing or unsupported protocol is encountered
     */
    override fun getSystemSpecs(): List<ZkSystemSpec> {
        val circuitFileNames = getAllCircuitFileNames()
        val systemSpecs = mutableListOf<ZkSystemSpec>()

        for (circuitFileName in circuitFileNames) {
            val circuitNameParts = circuitFileName.split("_")
            if (circuitNameParts.size != 3) {
                Logger.w(TAG, "$circuitFileName does not match expected <version>_<numAttributes>_<hash>")
                continue
            }

            val version = circuitNameParts[0].toLongOrNull()
            if (version == null) {
                Logger.w(TAG, "$circuitFileName does not match expected <version>_<numAttributes>_<hash>, could not find version number.")
                continue
            }

            val numAttributes = circuitNameParts[1].toLongOrNull()
            if (numAttributes == null) {
                Logger.w(TAG, "$circuitFileName does not match expected <version>_<numAttributes>_<hash>, could not find number of attributes.")
                continue
            }

            val spec = ZkSystemSpec(
                id = "${name}_${circuitFileName}",
                system = name,
            )
            spec.addParam("version", version)
            spec.addParam("circuitHash", circuitNameParts[2])
            spec.addParam("numAttributes", numAttributes)
            systemSpecs.add(spec)
        }

        return systemSpecs
    }

    /**
     * Finds the best matching [ZkSystemSpec] from a given list based on the number of signed attributes.
     *
     * @param zkSystemSpecs the available specs from the request
     * @param issuerSignedAttributes the attributes from the issuer
     * @return the best matching [ZkSystemSpec], or null if none are suitable
     * @throws IOException if the circuits directory is missing or unsupported protocol is encountered
     */
    override fun getMatchingSystemSpec(zkSystemSpecs: List<ZkSystemSpec>, mdocRequest: MdocRequest): ZkSystemSpec? {
        val numAttributesRequested = mdocRequest.requestedClaims.size.toLong()
        if (numAttributesRequested == 0L) {
            // We need at least one issuer signed attribute to find a matching spec for longfellow.
            return null
        }

        var systemSpecs = getSystemSpecs()
        systemSpecs = systemSpecs
            .filter { it.getParam<Long>("numAttributes") == numAttributesRequested }
            .sortedBy{ it.getParam<Long>("version") ?: Long.MIN_VALUE }
        return systemSpecs.firstOrNull()
    }
}
