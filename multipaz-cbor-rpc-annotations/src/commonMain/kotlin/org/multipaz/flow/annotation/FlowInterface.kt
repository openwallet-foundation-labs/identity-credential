package org.multipaz.flow.annotation

/**
 * Marks interface that defines flow getters and methods.
 *
 * Must inherit from org.multipaz.flow.FlowBaseInterface
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class FlowInterface(
    /** Fully qualified name for generated implementation for this interface. */
    val flowImplementationName: String = ""
)
