package org.multipaz.flow.annotation

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class FlowJoin(
    /** Path component for the URL used for this method. Must not contain '/'. */
    val path: String = ""
)
