package org.multipaz.testapp

import org.multipaz.mdoc.nfc.MdocNfcDataTransferService

class TestAppMdocNfcDataTransferService(
    sendResponse: (ByteArray) -> Unit
): MdocNfcDataTransferService(sendResponse)