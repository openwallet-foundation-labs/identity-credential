package org.multipaz.mdoc.zkp

import kotlinx.io.bytestring.ByteString

/**
 * Singleton object that manages registration and lookup of [ZkSystem] implementations,
 * and provides central interface for proof generation and verification.
 */
class ZkSystemRepository {
    private val repositories: MutableList<ZkSystem> = mutableListOf()

    /**
     * Registers a new [ZkSystem] into the repository.
     *
     * @param zkSystem the [ZkSystem] implementation to add.
     */
    fun add(zkSystem: ZkSystem): ZkSystemRepository {
        repositories.add(zkSystem)
        return this
    }

    /**
     * Looks up a registered [ZkSystem] by name.
     *
     * @param zkSystemName the name of the system to find.
     * @return the matching [ZkSystem], or null if not found.
     */
    fun lookup(zkSystemName: String): ZkSystem? {
        return repositories.find { it.name == zkSystemName }
    }

    /**
     * Returns a list of all [ZkSystemSpec] from each ZkSystem implementation.
     * @return a list of [ZkSystemSpec].
     */
    fun getAllZkSystemSpecs(): List<ZkSystemSpec> {
        return repositories.flatMap { it.getSystemSpecs() }
    }

    /**
     * Finds a [ZkSystemSpec] by its generated ID.
     * @param id the generated ID to search for.
     * @return the matching [ZkSystemSpec], or null if not found.
     */
    fun findZkSystemSpecByGeneratedId(id: String): ZkSystemSpec? {
        return getAllZkSystemSpecs().find { it.id == id }
    }
}
