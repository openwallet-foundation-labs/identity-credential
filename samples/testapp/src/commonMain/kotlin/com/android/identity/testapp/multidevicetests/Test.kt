package org.multipaz.testapp.multidevicetests

enum class Test(val description: String) {
    // holder terminates by including status 0x20 in same message as DeviceResponse
    //
    MDOC_CENTRAL_CLIENT_MODE("mdoc central client mode"),
    MDOC_PERIPHERAL_SERVER_MODE("mdoc peripheral server mode"),

    // holder terminates with separate message with status 0x20
    //
    MDOC_CENTRAL_CLIENT_MODE_HOLDER_TERMINATION_MSG("mdoc cc (holder term MSG)"),
    MDOC_PERIPHERAL_SERVER_MODE_HOLDER_TERMINATION_MSG("mdoc ps (holder term MSG)"),

    // holder terminates with BLE specific termination
    //
    MDOC_CENTRAL_CLIENT_MODE_HOLDER_TERMINATION_BLE("mdoc cc (holder term BLE)"),
    MDOC_PERIPHERAL_SERVER_MODE_HOLDER_TERMINATION_BLE("mdoc ps (holder term BLE)"),

    // reader terminates with message with status 0x20
    //
    MDOC_CENTRAL_CLIENT_MODE_READER_TERMINATION_MSG("mdoc cc (reader term MSG)"),
    MDOC_PERIPHERAL_SERVER_MODE_READER_TERMINATION_MSG("mdoc ps (reader term MSG)"),

    // reader terminates with BLE specific termination
    //
    MDOC_CENTRAL_CLIENT_MODE_READER_TERMINATION_BLE("mdoc cc (reader term BLE)"),
    MDOC_PERIPHERAL_SERVER_MODE_READER_TERMINATION_BLE("mdoc ps (reader term BLE)"),


    ///////////////////////////////
    // L2CAP with PSM from GATT
    ///////////////////////////////

    // holder L2CAP terminates by including status 0x20 in same message as DeviceResponse
    //
    MDOC_CENTRAL_CLIENT_MODE_L2CAP("mdoc cc L2CAP"),
    MDOC_PERIPHERAL_SERVER_MODE_L2CAP("mdoc psm L2CAP"),

    // holder L2CAP terminates with separate message with status 0x20
    //
    MDOC_CENTRAL_CLIENT_MODE_L2CAP_HOLDER_TERMINATION_MSG("mdoc cc L2CAP (holder term MSG)"),
    MDOC_PERIPHERAL_SERVER_MODE_L2CAP_HOLDER_TERMINATION_MSG("mdoc ps L2CAP (holder term MSG)"),

    // holder L2CAP terminates with BLE specific termination: N/A
    //

    // reader L2CAP terminates with message with status 0x20
    //
    MDOC_CENTRAL_CLIENT_MODE_L2CAP_READER_TERMINATION_MSG("mdoc cc L2CAP (reader term MSG)"),
    MDOC_PERIPHERAL_SERVER_MODE_L2CAP_READER_TERMINATION_MSG("mdoc ps L2CAP (reader term MSG)"),

    // reader L2CAP terminates with BLE specific termination: N/A
    //

    ///////////////////////////////
    // L2CAP with PSM in engagement (18013-5 Amendment 1)
    ///////////////////////////////

    // PSM is conveyed from reader during two-way engagement
    //
    MDOC_CENTRAL_CLIENT_MODE_L2CAP_PSM_IN_TWO_WAY_ENGAGEMENT("mdoc cc L2CAP w/ PSM in two-way-eng"),

    // PSM is in Device Engagement
    //
    MDOC_PERIPHERAL_SERVER_MODE_L2CAP_PSM_IN_DEVICE_ENGAGEMENT("mdoc psm L2CAP w/ PSM in DE"),

}