package com.android.identity.wallet_wear.presentation

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import com.android.identity.android.mdoc.engagement.QrEngagementHelper
import com.android.identity.android.mdoc.transport.DataTransport
import com.android.identity.android.mdoc.transport.DataTransportOptions
import com.android.identity.internal.Util
import com.android.identity.mdoc.connectionmethod.ConnectionMethodBle
import com.android.identity.securearea.SecureArea
import com.android.identity.util.Logger
import com.android.identity.wallet_wear.presentation.theme.IdentityCredentialTheme
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import java.util.UUID

class ShowQrCodeActivity : ComponentActivity() {
    private val TAG = "ShowQrCodeActivity"

    private lateinit var transferHelper: TransferHelper

    override fun onPause() {
        Logger.d(TAG, "onPause")
        super.onPause()
    }

    override fun onResume() {
        Logger.d(TAG, "onResume")
        super.onResume()
    }

    override fun onDestroy() {
        Logger.d(TAG, "onDestroy")
        super.onDestroy()
        qrEngagementHelper.close()
    }

    override fun onStop() {
        Logger.d(TAG, "onStop")
        super.onStop()
    }

    override fun onRestart() {
        Logger.d(TAG, "onRestart")
        super.onRestart()
    }

    private var qrBitmap: Bitmap? = null
    private lateinit var qrEngagementHelper: QrEngagementHelper
    private var credentialId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Logger.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val extras = intent.extras
        if (extras != null) {
            credentialId = extras.getString("credentialId")
        }
        Logger.d(TAG, "credentialId: $credentialId")

        transferHelper = TransferHelper.getInstance(applicationContext)

        buildUi()

        val eDeviceKeyPair = Util.createEphemeralKeyPair(SecureArea.EC_CURVE_P256)
        val options = DataTransportOptions.Builder().build()
        val connectionMethods = listOf(
            ConnectionMethodBle(
                true,
                false,
                UUID.randomUUID(),
                null
            )
        )
        qrEngagementHelper = QrEngagementHelper.Builder(applicationContext,
            eDeviceKeyPair.public,
            options,
            object : QrEngagementHelper.Listener {
                override fun onDeviceEngagementReady() {
                    Logger.d(TAG, "onDeviceEngagementReady")
                    qrBitmap = encodeQRCodeAsBitmap(qrEngagementHelper.deviceEngagementUriEncoded)
                    buildUi()
                }

                override fun onDeviceConnecting() {
                    transferHelper.setConnecting()

                    Logger.d(TAG, "onDeviceConnecting")
                }

                override fun onDeviceConnected(transport: DataTransport) {
                    Logger.d(TAG, "onDeviceConnected")
                    transferHelper.setConnecting()
                    val launchAppIntent = Intent(applicationContext, PresentationStartingActivity::class.java)
                    launchAppIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_NO_HISTORY or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    if (credentialId != null) {
                        launchAppIntent.putExtra("credentialId", credentialId)
                    }
                    applicationContext.startActivity(launchAppIntent)

                    transferHelper.setConnected(
                        eDeviceKeyPair,
                        transport,
                        qrEngagementHelper.deviceEngagement,
                        qrEngagementHelper.handover,
                    )
                }

                override fun onError(error: Throwable) {
                    Logger.w(TAG, "Error with QrEngagementHelper", error)
                }
            },
            applicationContext.mainExecutor
        )
            .setConnectionMethods(connectionMethods)
            .build()
    }

    private fun buildUi() {
        setContent {
            IdentityCredentialTheme {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                )
                {
                    qrBitmap?.asImageBitmap()?.let {
                        Image(
                            bitmap = it,
                            contentDescription = "QR Code",
                            modifier = Modifier.fillMaxWidth(0.7f)
                        )
                    }
                }
            }
        }
    }

    private fun encodeQRCodeAsBitmap(str: String): Bitmap {
        val width = 800
        val result: BitMatrix = try {
            MultiFormatWriter().encode(
                str,
                BarcodeFormat.QR_CODE, width, width, null
            )
        } catch (e: WriterException) {
            throw java.lang.IllegalArgumentException(e)
        }
        val w = result.width
        val h = result.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val offset = y * w
            for (x in 0 until w) {
                pixels[offset + x] = if (result[x, y]) Color.BLACK else Color.WHITE
            }
        }
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, w, h)
        return bitmap
    }

}