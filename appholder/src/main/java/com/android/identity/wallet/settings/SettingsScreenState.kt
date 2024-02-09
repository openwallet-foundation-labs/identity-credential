package com.android.identity.wallet.settings

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.android.identity.securearea.EcCurve
import kotlinx.parcelize.Parcelize

@Stable
@Immutable
data class SettingsScreenState(
    val autoCloseEnabled: Boolean = true,
    val sessionEncryptionCurveOption: SessionEncryptionCurveOption = SessionEncryptionCurveOption.P256,
    val useStaticHandover: Boolean = true,
    val isL2CAPEnabled: Boolean = false,
    val isBleClearCacheEnabled: Boolean = false,
    val isBleDataRetrievalEnabled: Boolean = true,
    val isBlePeripheralModeEnabled: Boolean = false,
    val wifiAwareEnabled: Boolean = false,
    val nfcEnabled: Boolean = false,
    val debugEnabled: Boolean = true
) {

    fun isBleEnabled(): Boolean = isBleDataRetrievalEnabled || isBlePeripheralModeEnabled

    fun canToggleBleDataRetrievalMode(newBleCentralMode: Boolean): Boolean {
        val updatedState = copy(isBleDataRetrievalEnabled = newBleCentralMode)
        return updatedState.hasDataRetrieval()
    }

    fun canToggleBlePeripheralMode(newBlePeripheralMode: Boolean): Boolean {
        val updatedState = copy(isBlePeripheralModeEnabled = newBlePeripheralMode)
        return updatedState.hasDataRetrieval()
    }

    fun canToggleWifiAware(newWifiAwareValue: Boolean): Boolean {
        val updatedState = copy(wifiAwareEnabled = newWifiAwareValue)
        return updatedState.hasDataRetrieval()
    }

    fun canToggleNfc(newNfcValue: Boolean): Boolean {
        val updatedState = copy(nfcEnabled = newNfcValue)
        return updatedState.hasDataRetrieval()
    }

    private fun hasDataRetrieval(): Boolean =
        isBleDataRetrievalEnabled
                || isBlePeripheralModeEnabled
                || wifiAwareEnabled
                || nfcEnabled

    @Parcelize
    enum class SessionEncryptionCurveOption : Parcelable {
        P256,
        P384,
        P521,
        BrainPoolP256R1,
        BrainPoolP320R1,
        BrainPoolP384R1,
        BrainPoolP512R1,
        X25519,
        X448;

        fun toEcCurve(): EcCurve =
            when (this) {
                P256 -> EcCurve.P256
                P384 -> EcCurve.P384
                P521 -> EcCurve.P521
                BrainPoolP256R1 -> EcCurve.BRAINPOOLP256R1
                BrainPoolP320R1 -> EcCurve.BRAINPOOLP320R1
                BrainPoolP384R1 -> EcCurve.BRAINPOOLP384R1
                BrainPoolP512R1 -> EcCurve.BRAINPOOLP512R1
                X25519 -> EcCurve.X25519
                X448 -> EcCurve.X448
            }

        companion object {
            fun fromEcCurve(curve: EcCurve): SessionEncryptionCurveOption =
                when (curve) {
                    EcCurve.P256 -> P256
                    EcCurve.P384 -> P384
                    EcCurve.P521 -> P521
                    EcCurve.BRAINPOOLP256R1 -> BrainPoolP256R1
                    EcCurve.BRAINPOOLP320R1 -> BrainPoolP320R1
                    EcCurve.BRAINPOOLP384R1 -> BrainPoolP384R1
                    EcCurve.BRAINPOOLP512R1 -> BrainPoolP512R1
                    EcCurve.X25519 -> X25519
                    EcCurve.X448 -> X448
                    else -> throw IllegalStateException("Unknown EcCurve")
                }
        }
    }
}