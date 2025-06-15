package org.multipaz.mdoc.zkp

import kotlinx.io.bytestring.ByteString

/**
 * Singleton object that manages registration and lookup of [ZkSystem] implementations,
 * and provides central interface for proof generation and verification.
 */
object ZkSystemRepository {
    private var repositories: MutableList<ZkSystem> = mutableListOf()

    /**
     * Registers a new [ZkSystem] into the repository.
     *
     * @param zkSystem the [ZkSystem] implementation to add.
     */
    fun add(zkSystem: ZkSystem) {
        repositories.add(zkSystem)
    }

    /**
     * Looks up a registered [ZkSystem] by name.
     *
     * @param zkSystemName the name of the system to find.
     * @return the matching [ZkSystem], or null if not found.
     */
    fun lookup(zkSystemName: String): ZkSystem? {
        for (system in repositories) {
            if (system.name == zkSystemName) {
                return system
            }
        }
        return null
    }

    /**
     * Verifies the proof embedded in a [ZkDocument] using the appropriate [ZkSystem].
     *
     * @param zkDocument the document containing the proof to verify
     * @param encodedSessionTranscript the encoded session transcript used for context
     *
     * @throws SystemNotFoundException if the referenced system is not registered
     */
    fun verifyZkDocumentProof(zkDocument: ZkDocument, encodedSessionTranscript: ByteString) {
        val system = lookup(zkDocument.zkDocumentData.zkSystemSpec.system) ?: throw SystemNotFoundException("${zkDocument.zkDocumentData.zkSystemSpec.system} System not found.")
        system.verifyProof(zkDocument, encodedSessionTranscript)
    }

    /**
     * Attempts to generate a zero-knowledge proof for a given document using a registered [ZkSystem].
     *
     * Iterates through all registered systems and calls [ZkSystem.generateProof] if a compatible system spec is found.
     *
     * @param zkSystemSpecs list of candidate system specifications to match against
     * @param document the encoded mdoc document to prove
     * @param encodedSessionTranscript the session transcript for the proof context
     * @return a [ZkDocument] containing the generated proof
     *
     * @throws SystemNotFoundException if no matching system could generate a proof
     */
    fun generateMdocProof(
        zkSystemSpecs: List<ZkSystemSpec>,
        document: ByteString,
        encodedSessionTranscript: ByteString
    ): ZkDocument {
        for (system in repositories) {
            val matchingSystemSpec = system.getMatchingSystemSpec(zkSystemSpecs, document)
            if (matchingSystemSpec == null) {
                continue
            }
            return system.generateProof(matchingSystemSpec, document, encodedSessionTranscript)
        }
        throw SystemNotFoundException("Could not find supported system for provided ZkSystemSpecs.")
    }

    fun getAllZkSystemSpecs(): List<ZkSystemSpec> {
        val zkSystemSpecs = mutableListOf<ZkSystemSpec>()
        for (system in repositories) {
            zkSystemSpecs.addAll(system.getSystemSpecs())
        }

        return zkSystemSpecs
    }
}
