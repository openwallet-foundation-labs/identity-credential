package org.multipaz.mdoc.zkp

import kotlinx.io.bytestring.ByteString

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
     * Generates a zero-knowledge proof for a given document using the specified [zkSystemSpec]
     * and session context.
     *
     * @param zkSystemSpec the system spec indicating which ZK circuit or rules to use
     * @param document the encoded mdoc Document CBOR (per ISO 18013-5 section 8.3.2.1.2.2)
     * @param encodedSessionTranscript the session transcript CBOR (used as public context)
     * @return a [ZkDocument] containing the resulting proof and metadata
     */
    fun generateProof(
        zkSystemSpec: ZkSystemSpec,
        document: ByteString,
        encodedSessionTranscript: ByteString
    ): ZkDocument

    /**
     * Verifies a zero-knowledge proof in the given [zkDocument] using the provided session context.
     *
     * @param zkDocument the document containing the proof and associated metadata
     * @param encodedSessionTranscript the same session transcript used during proof generation
     *
     * @throws ProofVerificationFailureException if the proof is invalid or cannot be verified
     */
    fun verifyProof(
        zkDocument: ZkDocument,
        encodedSessionTranscript: ByteString
    )

    /**
     * Returns all [ZkSystemSpec]s supported by this ZK system. Each spec describes
     * the parameters and configuration for a supported circuit or proof scheme.
     *
     * @return a list of supported [ZkSystemSpec]s
     */
    fun getSystemSpecs(): List<ZkSystemSpec>

    /**
     * Searches through the provided [zkSystemSpecs] and returns the first one that is
     * compatible with the given document.
     *
     * This is used during proof generation to select the correct spec for a document instance.
     *
     * @param zkSystemSpecs the list of specs available from the device request
     * @param document bytes of the document to generate the proof for
     * @return a compatible [ZkSystemSpec], or null if none match
     */
    fun getMatchingSystemSpec(zkSystemSpecs: List<ZkSystemSpec>, document: ByteString): ZkSystemSpec?
}
