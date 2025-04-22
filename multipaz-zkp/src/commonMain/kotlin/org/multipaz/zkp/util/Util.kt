package org.multipaz.zkp.util

import org.multipaz.zkp.mdoc.Attribute
import org.multipaz.zkp.mdoc.NativeAttribute

internal fun Attribute.toNative(): NativeAttribute =
    NativeAttribute(key, value.toByteArray())

internal fun Array<Attribute>.toNativeArray(): Array<NativeAttribute> =
    this.map { it.toNative() }.toTypedArray()
