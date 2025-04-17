package com.android.identity.testapp.provisioning.openid4vci

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.backend.Configuration
import org.multipaz.rpc.backend.Resources
import kotlin.coroutines.coroutineContext
import kotlin.reflect.KClass
import kotlin.reflect.cast

/**
 * Lazily create and memoize an object that is defined by the given class and a key.
 *
 * If/when configuration or resource objects change in the environment, object is created
 * anew.
 *
 * This method is thread/coroutine-safe. The same object is returned for all threads,
 * however, when the object is being created, multiple copies might be created if there
 * is a race condition.
 */
suspend fun<ResourceT : Any> BackendEnvironment.cache(
    clazz: KClass<ResourceT>,
    key: Any = "",
    factory: suspend (Configuration, Resources) -> ResourceT
): ResourceT {
    val configuration = getInterface(Configuration::class)!!
    val resources = getInterface(Resources::class)!!
    return cache
        .getOrPut(configuration) {
            mutableMapOf<Resources, EnvironmentCache>()
        }
        .getOrPut(resources) {
            EnvironmentCache()
        }
        .obtain(configuration, resources, clazz, key, factory)
}

suspend fun<ResourceT : Any> BackendEnvironment.Companion.cache(
    clazz: KClass<ResourceT>,
    key: Any = "",
    factory: suspend (Configuration, Resources) -> ResourceT
): ResourceT =
    get(coroutineContext).cache(clazz, key, factory)

private val cache = mutableMapOf<Configuration, MutableMap<Resources, EnvironmentCache>>()

private class EnvironmentCache {
    val map = mutableMapOf<KClass<out Any>, MutableMap<Any, Any>>()
    val lock = Mutex()

    suspend fun<ResourceT : Any> obtain(
        configuration: Configuration,
        resources: Resources,
        clazz: KClass<ResourceT>,
        key: Any,
        factory: suspend (Configuration, Resources) -> ResourceT
    ): ResourceT {
        lock.withLock {
            val submap = map[clazz]
            if (submap != null) {
                val cached = submap[key]
                if (cached != null) {
                    return clazz.cast(cached)
                }
            }
        }
        val resource = factory(configuration, resources)
        lock.withLock {
            (map.getOrPut(clazz) { mutableMapOf() })[key] = resource
        }
        return resource
    }
}