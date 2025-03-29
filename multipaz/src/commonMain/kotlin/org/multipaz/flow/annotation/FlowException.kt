package org.multipaz.flow.annotation

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class FlowException(
    /** Unique identifier for this exception (simple class name by default). */
    val exceptionId: String = ""
)
