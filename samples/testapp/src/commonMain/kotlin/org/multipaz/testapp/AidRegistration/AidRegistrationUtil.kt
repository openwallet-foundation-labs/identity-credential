package org.multipaz.testapp.AidRegistration

/**
 * Switch NFC AIDs for routing traffic to the host (TestApp impl) for handling.
 * Initially the state is unknown. But if never used it's safe to assume the routing is set to the Host.
 * On app restart the state might be restored to default (Host).
 */
expect fun routeNfcAidsToHost()

/**
 * Switch NFC AIDs for routing traffic to the SE (Secure Element) for handling.
 * Initially the state is unknown. But if never used it's safe to assume the routing is set to the Host.
 * On app restart the state might be restored to default (Host).
 */
expect fun routeNfcAidsToSe()