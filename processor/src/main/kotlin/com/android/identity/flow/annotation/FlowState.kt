package com.android.identity.flow.annotation

import kotlin.reflect.KClass

/**
 * Marks Cbor-serializable object that is used as flow state and defines
 * implementations for flow getters and methods.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class FlowState(
    /** Path component for the URL used for this flow. Must not contain '/'. */
    val path: String = "",
    /** Flow interface that is "implemented" by this flow state. */
    val flowInterface: KClass<out Any> = Unit::class,
    /**
     * Fully-qualified name for the flow interface (generated only if flowInterface
     * is not given)
     */
    val flowInterfaceName: String = "",
    /**
     * Fully-qualified name for the flow interface implementation (only used if
     * flowInterface is not given).
     */
    val flowImplementationName: String = "",
    /**
     * By default, flows must be created by other flows. Set this to true to allow client to
     * create this flow directly.
     */
    val creatable: Boolean = false
)
