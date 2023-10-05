package com.android.identity.wallet.settings

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.android.identity.securearea.SecureArea
import com.android.identity.securearea.SecureArea.EcCurve
import kotlinx.parcelize.Parcelize

@Stable
@Immutable
data class SettingsScreenState(
    val autoCloseEnabled: Boolean = true,
    val ephemeralKeyCurveOption: EphemeralKeyCurveOption = EphemeralKeyCurveOption.P256,
    val useStaticHandover: Boolean = true,
    val isL2CAPEnabled: Boolean = false,
    val isBleClearCacheEnabled: Boolean = false,
    val isBleDataRetrievalEnabled: Boolean = true,
    val isBlePeripheralModeEnabled: Boolean = false,
    val wifiAwareEnabled: Boolean = false,
    val nfcEnabled: Boolean = false,
    val debugEnabled: Boolean = true
) {

    fun isBleEnabled(): Boolean {
        return isBleDataRetrievalEnabled
                || isBlePeripheralModeEnabled
    }

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

    private fun hasDataRetrieval(): Boolean {
        return isBleDataRetrievalEnabled
                || isBlePeripheralModeEnabled
                || wifiAwareEnabled
                || nfcEnabled
    }

    @Parcelize
    enum class EphemeralKeyCurveOption : Parcelable {
        P256,
        P384,
        P521,
        BrainPoolP256R1,
        BrainPoolP320R1,
        BrainPoolP384R1,
        BrainPoolP512R1;

        fun toEcCurve(): Int {

            return when (this) {
                P256 -> SecureArea.EC_CURVE_P256
                P384 -> SecureArea.EC_CURVE_P384
                P521 -> SecureArea.EC_CURVE_P521
                BrainPoolP256R1 -> SecureArea.EC_CURVE_BRAINPOOLP256R1
                BrainPoolP320R1 -> SecureArea.EC_CURVE_BRAINPOOLP320R1
                BrainPoolP384R1 -> SecureArea.EC_CURVE_BRAINPOOLP384R1
                BrainPoolP512R1 -> SecureArea.EC_CURVE_BRAINPOOLP512R1
            }
        }

        companion object {
            fun fromEcCurve(@EcCurve curve: Int): EphemeralKeyCurveOption {
                return when (curve) {
                    SecureArea.EC_CURVE_P256 -> P256
                    SecureArea.EC_CURVE_P384 -> P384
                    SecureArea.EC_CURVE_P521 -> P521
                    SecureArea.EC_CURVE_BRAINPOOLP256R1 -> BrainPoolP256R1
                    SecureArea.EC_CURVE_BRAINPOOLP320R1 -> BrainPoolP320R1
                    SecureArea.EC_CURVE_BRAINPOOLP384R1 -> BrainPoolP384R1
                    SecureArea.EC_CURVE_BRAINPOOLP512R1 -> BrainPoolP512R1
                    else -> throw IllegalStateException("Unknown EcCurve")
                }
            }
        }
    }
}