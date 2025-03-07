package org.multipaz.jpeg2k

import org.junit.Test

import org.junit.Assert.assertEquals
import java.io.File

class Jpeg2kConverterTest {
    @Test
    fun flavor1() {
        val bytes = javaClass.classLoader!!.getResource("flavor1.j2").readBytes()
        val pdf = Jpeg2kConverter(File(".")).convertToPdfData(bytes)
        assertEquals(520, pdf.width)
        assertEquals(390, pdf.height)
        // uncomment to examine file manually
        // val out = File("flavor1.pdf").outputStream()
        // out.write(pdf.bytes)
        // out.close()
    }

    @Test
    fun flavor2() {
        val bytes = javaClass.classLoader!!.getResource("flavor2.j2").readBytes()
        val pdf = Jpeg2kConverter(File(".")).convertToPdfData(bytes)
        assertEquals(480, pdf.width)
        assertEquals(360, pdf.height)
        // uncomment to examine file manually
        // val out = File("flavor2.pdf").outputStream()
        // out.write(pdf.bytes)
        // out.close()
    }
}