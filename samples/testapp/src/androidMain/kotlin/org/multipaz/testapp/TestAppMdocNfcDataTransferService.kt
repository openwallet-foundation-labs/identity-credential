package org.multipaz.testapp

import android.content.Intent
import android.nfc.cardemulation.OffHostApduService
import android.os.IBinder

class TestAppMdocNfcDataTransferService : OffHostApduService() {
  override fun onBind(p0: Intent?): IBinder? {
    return null
  }
}