package org.multipaz.mdoc.transport

// Various constants used for Bluetooth Low Energy as used in ISO/IEC 18013-5:2021
internal object BleTransportConstants {

    // This indicates that the mdoc reader may/will begin
    // transmission (see ISO/IEC 18013-5:2021 Table 13).
    const val STATE_CHARACTERISTIC_START = 0x01

    // Signal to finish/terminate transaction (see ISO/IEC 18013-5:2021 Table 13)..
    const val STATE_CHARACTERISTIC_END = 0x02
}
