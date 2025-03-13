package org.multipaz.cbor.annotation

/**
 * Annotation for classes that provide their own (manually coded) CBOR serialization.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class CborSerializationImplemented(
    /**
     * See [CborSerializable.schemaId]. Required.
     */
    val schemaId: String
)
