package org.multipaz.server.openid4vci

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import javax.imageio.ImageIO

/**
 * Servlet that encodes text string (passed as `q` parameter) into a QR code.
 */
class QrServlet : BaseServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val str = req.getParameter("q") ?: throw IllegalStateException("q parameter required")
        val qr = Encoder.encode(str, ErrorCorrectionLevel.L, null)
        val matrix = qr.matrix
        val width = matrix.width
        val height = matrix.height
        val image = BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY)
        val pixels = (image.raster.dataBuffer as DataBufferByte).data
        var index = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                pixels[index++] = if (matrix[x, y].toInt() == 0) -1 else 0
            }
        }
        resp.contentType = "image/png"
        ImageIO.write(image, "png", resp.outputStream)
    }
}