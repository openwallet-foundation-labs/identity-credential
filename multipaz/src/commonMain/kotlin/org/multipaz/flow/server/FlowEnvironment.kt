package org.multipaz.flow.server

import kotlin.reflect.KClass

/** Interface to access server-provided interfaces from within the flow getters and methods. */
interface FlowEnvironment {
    fun<T: Any> getInterface(clazz: KClass<T>): T?

    companion object {
        val EMPTY = object: FlowEnvironment {
            override fun <T : Any> getInterface(clazz: KClass<T>): T? {
                return null
            }
        }
    }
}