package org.multipaz.util

import platform.Foundation.NSUUID

// TODO: this could probably be faster if we use blobs instead of strings.

fun UUID.toNSUUID(): NSUUID {
    return NSUUID(this.toString())
}

fun UUID.Companion.fromNSUUID(nsUUID: NSUUID): UUID {
    return UUID.fromString(nsUUID.UUIDString)
}
