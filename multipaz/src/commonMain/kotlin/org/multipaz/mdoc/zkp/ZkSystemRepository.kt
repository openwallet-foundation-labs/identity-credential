package org.multipaz.mdoc.zkp

/**
 * A repository of [ZkSystem] implementations.
 */
class ZkSystemRepository {
    private val _system: MutableList<ZkSystem> = mutableListOf()

    /**
     * Registers a new [ZkSystem] into the repository.
     *
     * @param zkSystem the [ZkSystem] implementation to add.
     */
    fun add(zkSystem: ZkSystem): ZkSystemRepository {
        _system.add(zkSystem)
        return this
    }

    /**
     * Looks up a registered [ZkSystem] by name.
     *
     * @param zkSystemName the name of the system to find.
     * @return the matching [ZkSystem], or null if not found.
     */
    fun lookup(zkSystemName: String): ZkSystem? {
        return _system.find { it.name == zkSystemName }
    }

    /**
     * Returns a list of all [ZkSystemSpec] from each ZkSystem implementation.
     * @return a list of [ZkSystemSpec].
     */
    fun getAllZkSystemSpecs(): List<ZkSystemSpec> {
        return _system.flatMap { it.systemSpecs }
    }
}
