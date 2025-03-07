package org.multipaz.cbor.annotation

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class CborSerializable(
    /**
     * Optional parameter for sealed class hierarchies to define the key that carries type id.
     *
     * This parameter should be used on the root (sealed) class.
     */
    val typeKey: String = "",
    /**
     * Optional parameter for sealed class hierarchies to define the type id for this class.
     *
     * This parameter should be used on the leaf (data) class.
     */
    val typeId: String = ""
)
