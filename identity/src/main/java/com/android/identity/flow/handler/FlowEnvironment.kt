package com.android.identity.flow.handler

import kotlin.reflect.KClass

/**
 * Interface to access server-provided interfaces from the flow getters
 * and methods.
 */
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