package org.multipaz.testapp.multidevicetests

enum class Plan(
    val description: String,
    val tests: List<Pair<Test, Int>>,
    val prewarm: Boolean,
) {
    ALL_TESTS(
        description = "All Tests & Corner-cases",
        tests = Test.entries.toList().map { Pair(it, 1) },
        prewarm = true,
    ),
    HAPPY_FLOW_SHORT(
        description = "Happy Flow Short",
        tests = listOf(
            Pair(Test.MDOC_CENTRAL_CLIENT_MODE, 10),
            Pair(Test.MDOC_PERIPHERAL_SERVER_MODE, 10)
        ),
        prewarm = true,
    ),
    HAPPY_FLOW_LONG(
        description = "Happy Flow Long",
        tests = listOf(
            Pair(Test.MDOC_CENTRAL_CLIENT_MODE, 50),
            Pair(Test.MDOC_PERIPHERAL_SERVER_MODE, 50)
        ),
        prewarm = true,
    ),
    BLE_CENTRAL_CLIENT_MODE_ONLY(
        description = "BLE Central Client Mode",
        tests = listOf(
            Pair(Test.MDOC_CENTRAL_CLIENT_MODE, 40),
        ),
        prewarm = true,
    ),
    BLE_CENTRAL_CLIENT_MODE_ONLY_NO_PREWARM(
        description = "BLE Central Client Mode w/o prewarm",
        tests = listOf(
            Pair(Test.MDOC_CENTRAL_CLIENT_MODE, 40),
        ),
        prewarm = false,
    ),
    BLE_PERIPHERAL_SERVER_MODE(
        description = "BLE Peripheral Server Mode",
        tests = listOf(
            Pair(Test.MDOC_PERIPHERAL_SERVER_MODE, 40),
        ),
        prewarm = true,
    ),
    BLE_PERIPHERAL_SERVER_MODE_NO_PREWARM(
        description = "BLE Peripheral Server Mode w/o prewarm",
        tests = listOf(
            Pair(Test.MDOC_PERIPHERAL_SERVER_MODE, 40),
        ),
        prewarm = false,
    ),
    BLE_CENTRAL_CLIENT_MODE_L2CAP_PSM_IN_TWO_WAY_ENGAGEMENT(
        description = "BLE Central Client L2CAP w/ PSM in Engagement",
        tests = listOf(
            Pair(Test.MDOC_CENTRAL_CLIENT_MODE_L2CAP_PSM_IN_TWO_WAY_ENGAGEMENT, 40),
        ),
        prewarm = true,
    ),
    BLE_PERIPHERAL_SERVER_MODE_L2CAP_PSM_IN_TWO_WAY_ENGAGEMENT(
        description = "BLE Peripheral Server L2CAP w/ PSM in Engagement",
        tests = listOf(
            Pair(Test.MDOC_PERIPHERAL_SERVER_MODE_L2CAP_PSM_IN_DEVICE_ENGAGEMENT, 40),
        ),
        prewarm = true,
    ),
}