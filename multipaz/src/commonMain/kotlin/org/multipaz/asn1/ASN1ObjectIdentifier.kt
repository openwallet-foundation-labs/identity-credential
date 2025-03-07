package org.multipaz.asn1

import kotlinx.io.bytestring.ByteStringBuilder
import kotlin.math.max

class ASN1ObjectIdentifier(val oid: String): ASN1PrimitiveValue(tag = TAG_NUMBER) {

    override fun encode(builder: ByteStringBuilder) {
        val bsb = ByteStringBuilder()
        val components = oid.split(".").map { it.toInt() }
        if (components.size < 2) {
            throw IllegalStateException("OID must have at least two components")
        }
        val firstOctet = components[0]*40 + components[1]
        // Guaranteed to fit in one byte as per spec
        bsb.append(firstOctet.toByte())
        for (component in components.subList(2, components.size)) {
            val bitLength = Int.SIZE_BITS - component.countLeadingZeroBits()
            val bytesNeeded = max((bitLength + 6) / 7, 1)
            for (n in IntRange(0, bytesNeeded - 1).reversed()) {
                var digit = component.shr(n * 7).and(0x7f)
                if (n > 0) {
                    digit = digit.or(0x80)
                }
                bsb.append(digit.toByte())
            }
        }
        val componentsEncoded = bsb.toByteString().toByteArray()
        ASN1.appendUniversalTagEncodingLength(builder, TAG_NUMBER, enc, componentsEncoded.size)
        builder.append(componentsEncoded)
    }

    override fun equals(other: Any?): Boolean = other is ASN1ObjectIdentifier && oid == other.oid

    override fun hashCode(): Int = oid.hashCode()

    override fun toString(): String {
        return "ASN1ObjectIdentifier($oid)"
    }

    companion object {
        const val TAG_NUMBER = 0x06

        fun parse(content: ByteArray): ASN1ObjectIdentifier {
            if (content.size < 1) {
                throw IllegalStateException("Content must be at least a single byte")
            }
            val sb = StringBuilder()
            sb.append("${content[0].toInt()/40}.${content[0].toInt()%40}")
            var currentComponent = 0
            for (n in IntRange(1, content.size - 1)) {
                val digit = content[n].toInt()
                currentComponent = currentComponent.shl(7).or(digit.and(0x7f))
                if (digit.and(0x80) == 0) {
                    sb.append(".$currentComponent")
                    currentComponent = 0
                }
            }
            return ASN1ObjectIdentifier(sb.toString())
        }
    }
}