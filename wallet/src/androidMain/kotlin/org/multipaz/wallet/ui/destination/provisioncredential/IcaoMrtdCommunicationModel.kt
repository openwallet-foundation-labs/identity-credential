package org.multipaz_credential.wallet.ui.destination.provisioncredential

import androidx.activity.ComponentActivity
import androidx.camera.core.Preview.SurfaceProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import org.multipaz.util.Logger
import org.multipaz.mrtd.MrtdAccessData
import org.multipaz.mrtd.MrtdMrzScanner
import org.multipaz.mrtd.MrtdNfc
import org.multipaz.mrtd.MrtdNfcReader
import org.multipaz.mrtd.MrtdNfcScanner
import org.multipaz_credential.wallet.util.getActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.lang.IllegalStateException

private const val TAG = "EvidenceRequestIcaoModel"

class IcaoMrtdCommunicationModel<ResultT>(
    private val reader: MrtdNfcReader<ResultT>,
    val status: MutableState<MrtdNfc.Status>,
    private val navController: NavController,
    private val onResult: (ResultT) -> Unit,
    private val activity: ComponentActivity,
    private val coroutineScope: CoroutineScope
) {
    enum class Route(val route: String) {
        CAMERA_SCAN("camera"),
        NFC_SCAN("nfc")
    }

    private var launched = false

    fun launchCameraScan(surfaceProvider: SurfaceProvider) {
        if (navController.currentDestination!!.route != Route.CAMERA_SCAN.route) {
            throw IllegalStateException("Camera scanning launched when UI is in not ready")
        }
        launched = true
        coroutineScope.launch {
            try {
                val accessData = cameraScan(surfaceProvider)
                status.value = MrtdNfc.Initial
                navController.navigate(Route.NFC_SCAN.route)
                nfcScan(accessData)
            } catch (err: Exception) {
                Logger.e(TAG, "Error scanning MRTD: $err")
            }
        }
    }

    internal fun maybeLaunchNfcScan() {
        if (navController.currentDestination!!.route != Route.NFC_SCAN.route || launched) {
            return
        }
        launched = true
        coroutineScope.launch {
            try {
                status.value = MrtdNfc.Initial
                nfcScan(null)
            } catch (err: Exception) {
                Logger.e(TAG, "Error scanning MRTD: $err")
            }
        }
    }

    private suspend fun cameraScan(surfaceProvider: SurfaceProvider): MrtdAccessData {
        val passportCapture = MrtdMrzScanner(activity)
        return passportCapture.readFromCamera(surfaceProvider)
    }

    private suspend fun nfcScan(accessData: MrtdAccessData?) {
        val passportNfcScanner = MrtdNfcScanner(activity)
        val result =
            passportNfcScanner.scanCard(accessData, reader) { updatedStatus ->
                status.value = updatedStatus
            }
        onResult(result)
    }
}

@Composable
fun <ResultT>rememberIcaoMrtdCommunicationModel(
    reader: MrtdNfcReader<ResultT>,
    navController: NavController,
    onResult: (ResultT) -> Unit
): IcaoMrtdCommunicationModel<ResultT> {
    val activity = LocalContext.current.getActivity()!!
    val scope = rememberCoroutineScope()
    val model = remember {
        IcaoMrtdCommunicationModel(
            reader,
            mutableStateOf(MrtdNfc.Initial),
            navController,
            onResult,
            activity,
            scope
        )}
    SideEffect {
        model.maybeLaunchNfcScan()
    }
    return model
}