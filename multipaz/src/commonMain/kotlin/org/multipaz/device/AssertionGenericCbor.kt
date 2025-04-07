package org.multipaz.device

import org.multipaz.cbor.DataItem

/**
 * General-purpose [Assertion] that contains arbitrary Cbor data.
 */
class AssertionGenericCbor(val data: DataItem): Assertion()