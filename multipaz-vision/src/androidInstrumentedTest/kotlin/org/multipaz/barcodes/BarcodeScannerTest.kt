package org.multipaz.barcodes

import androidx.compose.ui.test.ExperimentalTestApi
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.junit.Test
import org.multipaz.compose.decodeImage
import org.multipaz.multipaz_vision.generated.resources.Res
import kotlin.test.assertEquals


@OptIn(ExperimentalResourceApi::class, ExperimentalTestApi::class)
class BarcodeScannerTests {
    @Test
    fun testBarcodeScannerRawQrCode() = runTest {
        val iso18013_5_qr_code = decodeImage(Res.readBytes("files/iso-18013-5-qr-code.png"))
        scanBarcode(iso18013_5_qr_code).let { barcodes ->
            assertEquals(1, barcodes.size)
            assertEquals(BarcodeFormat.QR_CODE, barcodes[0].format)
            assertEquals(
                "mdoc:owBjMS4wAYIB2BhYS6QBAiABIVggYKAEQ2FyoaRFdHmJZKeKnd2RvAM16GrOwv6H735ZaWAiWCDT7bTMioRN9MBOuYeAYIxECNhqk4Fz4ma8EMMTbJPZEQKBgwIBowD0AfULUH4xxz-9wkU2vVgxPAaJXZw",
                barcodes[0].text
            )
        }
    }

    @Test
    fun testBarcodeScannerPhotoOfQrCode() = runTest {
        val iso18013_5_qr_code_photo = decodeImage(Res.readBytes("files/iso-18013-5-photo-of-qr-code.jpg"))
        scanBarcode(iso18013_5_qr_code_photo).let { barcodes ->
            assertEquals(1, barcodes.size)
            assertEquals(BarcodeFormat.QR_CODE, barcodes[0].format)
            assertEquals(
                "mdoc:owBjMS4wAYIB2BhYS6QBAiABIVggYKAEQ2FyoaRFdHmJZKeKnd2RvAM16GrOwv6H735ZaWAiWCDT7bTMioRN9MBOuYeAYIxECNhqk4Fz4ma8EMMTbJPZEQKBgwIBowD0AfULUH4xxz-9wkU2vVgxPAaJXZw",
                barcodes[0].text
            )
        }
    }

    @Test
    fun testBarcodeScannerNoBarCode() = runTest {
        val no_bar_code = decodeImage(Res.readBytes("files/no-bar-code.jpg"))
        assertEquals(0, scanBarcode(no_bar_code).size)
    }
}