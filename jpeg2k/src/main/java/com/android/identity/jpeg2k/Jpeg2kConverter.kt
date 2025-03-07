package org.multipaz.jpeg2k

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.min

/**
 * Utility class to parse JPEG2000 classes on Android.
 *
 * This utilizes the fact that PDF rendering is a part of standard Android API and JPEG2000 is
 * a standard part of PDF. JPEG2000 file is wrapped in PDF and rendered using PDF renderer.
 *
 * @param tmpDir a folder for temporary files (PDF can be rendered only from a file).
 */
class Jpeg2kConverter(private val tmpDir: File) {

    /** Parses JPEG2000 file and returns a Bitmap. */
    fun convertToBitmap(j2k: ByteArray): Bitmap {
        val pdf = convertToPdfData(j2k)
        tmpDir.mkdirs()
        val pdfFile = File.createTempFile("tmp_conv", ".pdf", tmpDir)
        val out = pdfFile.outputStream()
        out.write(pdf.bytes)
        out.close()
        val input = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(input)
        val page = renderer.openPage(0)
        val bitmap = Bitmap.createBitmap(pdf.width, pdf.height, Bitmap.Config.ARGB_8888)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        renderer.close()
        pdfFile.delete()
        return bitmap
    }

    internal fun convertToPdfData(j2k: ByteArray): PDFData {
        if (j2k.size < 20) {
            throw IllegalArgumentException("Not Jpeg2K")
        }
        val width: Int
        val height: Int
        val prefix = ByteArrayOutputStream()
        if ((j2k[0].toInt() and 0xFF) == 0xFF && (j2k[1].toInt() and 0xFF) == 0x4F
            && (j2k[2].toInt() and 0xFF) == 0xFF && (j2k[3].toInt() and 0xFF) == 0x51) {
            width = readInt(j2k, 8)
            height = readInt(j2k, 12)
            // signature
            prefix.write(byteArrayOf(0, 0, 0, 0x0C, 0x6A, 0x50, 0x20, 0x20, 0x0D, 0x0A,
                (0x87).toByte(), 0x0A))
            // ftyp
            prefix.write(byteArrayOf(0, 0, 0, 0x14, 0x66, 0x74, 0x79, 0x70, 0x6A, 0x70, 0x32, 0x20,
                0, 0, 0, 0, 0x6A, 0x70, 0x32, 0x20))
            // jp2h
            prefix.write(byteArrayOf(0, 0, 0, 0x2d, 0x6a, 0x70, 0x32, 0x68))
            // ihdr
            prefix.write(byteArrayOf(0, 0, 0, 0x16, 0x69, 0x68, 0x64, 0x72))
            putInt(prefix, height)
            putInt(prefix, width)
            prefix.write(byteArrayOf(0, 3, 7, 7, 1, 0))
            // colr
            prefix.write(byteArrayOf(0, 0, 0, 0x0F, 0x63, 0x6F, 0x6C, 0x72, 1, 0, 0, 0, 0, 0, 0x10))
            // jp2c
            prefix.write(byteArrayOf(0, 0, 0, 0, 0x6a, 0x70, 0x32, 0x63))
        } else {
            // Scan to find dimensions
            val tag = byteArrayOf(105, 104, 100, 114)
            var offset = 0
            var matchOffset = 0
            val size = min(j2k.size, 256)
            while (offset < size) {
                if (j2k[offset] == tag[matchOffset]) {
                    matchOffset++
                    if (matchOffset == tag.size) {
                        offset++
                        break
                    }
                } else {
                    matchOffset = 0
                }
                offset++
            }
            if (matchOffset != tag.size || offset + 8 > j2k.size) {
                throw IllegalArgumentException("Not J2K")
            }
            width = readInt(j2k, offset + 4)
            height = readInt(j2k, offset)
        }
        val widthStr = width.toString().padStart(8, ' ')
        val heightStr = height.toString().padStart(9, ' ')
        val prefixBytes = prefix.toByteArray()
        val length = prefixBytes.size + j2k.size
        val lengthStr = length.toString().padStart(9, ' ')
        val buffer = ByteArrayOutputStream()
        var i = 0
        var xrefOffset = -1
        while (i < PDF_TEMPLATE.length) {
            val c = PDF_TEMPLATE[i++]
            if (c != '@') {
                buffer.write(c.code)
                continue
            }
            if (PDF_TEMPLATE[i++] != '{') {
                throw IllegalStateException("Invalid template")
            }
            val k = PDF_TEMPLATE.indexOf('}', i)
            val name = PDF_TEMPLATE.substring(i, k)
            i = k + 1
            when (name) {
                "width" -> putAscii(buffer, widthStr)
                "height" -> putAscii(buffer, heightStr)
                "length" -> putAscii(buffer, lengthStr)
                "image-bytes" -> {
                    buffer.write(prefixBytes)
                    buffer.write(j2k)
                }

                "xref" -> {
                    xrefOffset = buffer.size()
                    putAscii(buffer, "xref")
                }

                "xref-offset" -> putAscii(buffer, xrefOffset.toString())
                else -> throw IllegalStateException("unknown name: $name")
            }
        }
        return PDFData(buffer.toByteArray(), width, height)
    }

    companion object {
        /**
         * Replacement for [BitmapFactory.decodeByteArray]
         */
        fun decodeByteArray(context: Context, data: ByteArray): Bitmap? {
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
            if (bitmap != null) {
                return bitmap
            }
            return try {
                Jpeg2kConverter(File(context.cacheDir, "j2k_conv")).convertToBitmap(data)
            } catch (err: IllegalArgumentException) {
                Log.e("JPEG2K_CONV", "JPEFG2000 parsing failed", err)
                null
            }
        }

        private const val PDF_TEMPLATE = """%PDF-1.5
1 0 obj
<< /Pages 2 0 R /Type /Catalog >>
endobj
2 0 obj
<< /Count 1 /Kids [ 3 0 R ] /Type /Pages >>
endobj
3 0 obj
<< /Contents 4 0 R /MediaBox [ 0.0 0.0 @{width} @{height} ]
  /Parent 2 0 R /Resources <<
     /ProcSet [ /PDF /ImageC ]
     /XObject << /J2K 5 0 R >>
     >>
   /Type /Page >>
endobj
4 0 obj
<< /Length 42 >>
stream
q
@{width} 0 0 @{height} 0 0 cm
/J2K Do
Q
endstream
endobj
5 0 obj
<< /Filter /JPXDecode /Subtype /Image /Type /XObject
/Width @{width} /Height @{height} /Length @{length} >>
stream
@{image-bytes}
endstream
endobj
@{xref}
0 6
0000000000 65535 f${'\r'}
0000000009 00000 n${'\r'}
0000000058 00000 n${'\r'}
0000000117 00000 n${'\r'}
0000000310 00000 n${'\r'}
0000000401 00000 n${'\r'}
trailer << /Root 1 0 R /Size 6 >>
startxref
@{xref-offset}
%%EOF
"""
    }

    private fun putAscii(buffer: ByteArrayOutputStream, ascii: String) {
        for (c in ascii) {
            buffer.write(c.code)
        }
    }

    private fun putInt(buffer: ByteArrayOutputStream, v: Int) {
        buffer.write(v ushr 24)
        buffer.write(v ushr 16)
        buffer.write(v ushr 8)
        buffer.write(v)
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int {
        return bytes[offset].toInt() and 0xFF shl 24 or (bytes[offset + 1].toInt() and 0xFF shl 16) or (bytes[offset + 2].toInt() and 0xFF shl 8) or (bytes[offset + 3].toInt() and 0xFF shl 0)
    }

    data class PDFData(
        val bytes: ByteArray, val width: Int, val height: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as PDFData

            if (!bytes.contentEquals(other.bytes)) return false
            if (width != other.width) return false
            return height == other.height
        }

        override fun hashCode(): Int {
            var result = bytes.contentHashCode()
            result = 31 * result + width
            result = 31 * result + height
            return result
        }
    }
}