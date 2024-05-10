package com.android.identity.flow.annotation

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class FlowGetter(
    /** Path component for the URL used for this method. Must not contain '/'. */
    val path: String = ""
)
