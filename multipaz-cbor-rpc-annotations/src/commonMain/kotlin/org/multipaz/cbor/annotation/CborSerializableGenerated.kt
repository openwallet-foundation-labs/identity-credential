package org.multipaz.cbor.annotation

/**
 * Annotation that is used in the generated code to communicate assigned schemaId to other
 * compilation modules.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class CborSerializableGenerated(val schemaId: String)