package org.multipaz.mdoc.zkp

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.io.bytestring.ByteString
import org.multipaz.request.MdocRequest

/**
 * Interface representing a Zero-Knowledge Proof system that can generate and verify proofs
 * for identity documents according to the ISO/IEC 18013-5 standard.
 */
interface ZkSystem {
    /**
     * The unique name identifying this ZK system implementation.
     */
    val name: String

    /**
     * A list of all [ZkSystemSpec]s supported by this ZK system. Each spec describes
     * the parameters and configuration for a supported circuit or proof scheme.
     */
    val systemSpecs: List<ZkSystemSpec>

    /**
     * Generates a zero-knowledge proof for a given document using the specified [zkSystemSpec]
     * and session context.
     *
     * @param zkSystemSpec the system spec indicating which ZK circuit or rules to use
     * @param encodedDocument the encoded mdoc Document CBOR (per ISO 18013-5 section 8.3.2.1.2.2)
     * @param encodedSessionTranscript the session transcript CBOR (used as public context)
     * @return a [ZkDocument] containing the resulting proof and metadata
     */
    fun generateProof(
        zkSystemSpec: ZkSystemSpec,
        encodedDocument: ByteString,
        encodedSessionTranscript: ByteString,
        timestamp: Instant = Clock.System.now()
    ): ZkDocument

    /**
     * Verifies a zero-knowledge proof in the given [zkDocument] using the provided session context.
     *
     * @param zkDocument the document containing the proof and associated metadata
     * @param zkSystemSpec the system spec used to generate the proof
     * @param encodedSessionTranscript the same session transcript used during proof generation
     *
     * @throws ProofVerificationFailureException if the proof is invalid or cannot be verified
     */
    fun verifyProof(
        zkDocument: ZkDocument,
        zkSystemSpec: ZkSystemSpec,
        encodedSessionTranscript: ByteString
    )

    /**
     * Searches through the provided [zkSystemSpecs] and returns the first one that is
     * compatible with the given document.
     *
     * This is used during proof generation to select the correct spec for a document instance.
     *
     * @param zkSystemSpecs the list of specs available from the device request
     * @param encodedDocument bytes of the document to generate the proof for
     * @return a compatible [ZkSystemSpec], or null if none match
     */
    fun getMatchingSystemSpec(zkSystemSpecs: List<ZkSystemSpec>, mdocRequest: MdocRequest): ZkSystemSpec?
}
