package com.android.mdl.app.transfer

import android.content.Context
import com.android.identity.ConnectionMethod
import com.android.identity.ConnectionMethodBle
import com.android.identity.ConnectionMethodNfc
import com.android.identity.ConnectionMethodWifiAware
import com.android.identity.DataTransportOptions
import com.android.mdl.app.util.PreferencesHelper
import java.util.ArrayList
import java.util.OptionalLong
import java.util.UUID

class ConnectionSetup(
    private val context: Context
) {

    fun getConnectionOptions(): DataTransportOptions {
        val builder = DataTransportOptions.Builder()
            .setBleUseL2CAP(PreferencesHelper.isBleL2capEnabled(context))
            .setBleClearCache(PreferencesHelper.isBleClearCacheEnabled(context))
        return builder.build()
    }

    fun getConnectionMethods(): List<ConnectionMethod> {
        val connectionMethods = ArrayList<ConnectionMethod>()
        if (PreferencesHelper.isBleDataRetrievalEnabled(context)) {
            connectionMethods.add(ConnectionMethodBle(false, true, null, UUID.randomUUID()))
        }
        if (PreferencesHelper.isBleDataRetrievalPeripheralModeEnabled(context)) {
            connectionMethods.add(ConnectionMethodBle(true, false, UUID.randomUUID(), null))
        }
        if (PreferencesHelper.isWifiDataRetrievalEnabled(context)) {
            val empty = OptionalLong.empty()
            connectionMethods.add(ConnectionMethodWifiAware(null, empty, empty, null))
        }
        if (PreferencesHelper.isNfcDataRetrievalEnabled(context)) {
            // TODO: Add API to ConnectionMethodNfc to get sizes appropriate for the device
            connectionMethods.add(ConnectionMethodNfc(0xffff, 0x10000))
        }
        return connectionMethods
    }
}