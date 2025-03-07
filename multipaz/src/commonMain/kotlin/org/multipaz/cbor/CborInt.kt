package org.multipaz.cbor

/**
 * Base class for [Nint] and [Uint].
 */
abstract class CborInt(majorType: MajorType) : DataItem(majorType)