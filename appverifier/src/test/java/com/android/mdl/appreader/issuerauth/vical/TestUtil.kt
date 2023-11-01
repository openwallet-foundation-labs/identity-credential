package com.android.mdl.appreader.issuerauth.vical

import org.junit.Assert
import org.junit.Test

object TestUtil {

    fun findMagic(input: ByteArray, magic: ByteArray): Boolean {
        var inputOff = 0
        var magicOff = 0
        while (inputOff < input.size) {
            if (input[inputOff] == magic[magicOff]) {
                inputOff++
                magicOff++
                if (magicOff == magic.size) {
                    // immediately return true if
                    return true
                }
            } else {
                // make sure that the
                inputOff = inputOff - magicOff + 1
                magicOff = 0
            }
        }
        return false
    }


    @Test
    fun oidUrnTest() {
        val urn = Util.oidStringToURN("1.2.3")
        println(urn)
        val oid = Util.urnStringToOid(urn)
        println(oid)
        Assert.assertTrue(true)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val input = byteArrayOf(1, 2, 3, 2, 3, 3)
        val magic = byteArrayOf(2, 3, 3)
        val found = findMagic(input, magic)
        System.out.println(found)
    }
}