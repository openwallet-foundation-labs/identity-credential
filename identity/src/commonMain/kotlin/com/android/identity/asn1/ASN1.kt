package com.android.identity.asn1

import com.android.identity.util.toHex
import kotlinx.io.bytestring.ByteStringBuilder

internal data class IdentifierOctets(
    val cls: ASN1TagClass,
    val encoding: ASN1Encoding,
    val tagNumber: Int,
)

object ASN1 {

    internal fun appendIdentifierAndLength(
        builder: ByteStringBuilder,
        cls: ASN1TagClass,
        encoding: ASN1Encoding,
        tagNum: Int,
        length: Int
    ) {
        if (tagNum <= 0x1e) {
            builder.append((cls.value or encoding.value or tagNum).toByte())
        } else {
            TODO()
        }

        if (length < 0x80) {
            builder.append(length.toByte())
        } else {
            val lengthFieldSize = ((Int.SIZE_BITS - length.countLeadingZeroBits()) + 7)/8
            builder.append((0x80 + lengthFieldSize).toByte())
            val encodedLength = byteArrayOf(
                (length shr 24).and(0xff).toByte(),
                (length shr 16).and(0xff).toByte(),
                (length shr 8).and(0xff).toByte(),
                (length shr 0).and(0xff).toByte(),
            )
            for (b in IntRange(4 - lengthFieldSize, 3)) {
                builder.append(encodedLength[b])
            }
        }
    }

    internal fun appendUniversalTagEncodingLength(
        builder: ByteStringBuilder,
        tagNum: Int,
        encoding: ASN1Encoding,
        length: Int
    ) {
        appendIdentifierAndLength(builder, ASN1TagClass.UNIVERSAL, encoding, tagNum, length)
    }

    internal fun decodeIdentifierOctets(derEncoded: ByteArray, offset: Int): Pair<Int, IdentifierOctets> {
        var o = offset
        val idOctet0 = derEncoded[o++]
        val tagClass = ASN1TagClass.parse(idOctet0)
        val encoding = ASN1Encoding.parse(idOctet0)
        val tag0 = idOctet0.toInt().and(0x1f)

        val tagNum = if (tag0 <= 0x1e) {
            tag0
        } else {
            val read0 = derEncoded[o++].toInt().and(0xff)
            if (read0.and(0x7f) == 0) {
                throw IllegalArgumentException("Invalid first byte in non-low tag number")
            }
            var result = read0.and(0x7f)
            do {
                val read = derEncoded[o++].toInt().and(0xff)
                result = result shl 7
                result = result or read.and(0x7f)
            } while (read.and(0x80) != 0)
            result
        }
        return Pair(o, IdentifierOctets(tagClass, encoding, tagNum))
    }

    internal fun decodeLength(derEncoded: ByteArray, offset: Int): Pair<Int, Int> {
        val len0 = derEncoded[offset].toInt().and(0xff)
        if (len0.and(0x80) == 0) {
            // Short-form
            return Pair(offset + 1, len0)
        }
        val numOctets = len0.and(0x7f)
        if (numOctets == 0) {
            throw IllegalArgumentException("Indeterminate length not supported")
        }
        var value = 0
        for (n in IntRange(offset + 1, offset + 1 + numOctets - 1)) {
            value = value.shl(8).or(derEncoded[n].toInt().and(0xff))
        }
        return Pair(offset + 1 + numOctets, value)
    }

    internal fun decode(derEncoded: ByteArray, offset: Int): Pair<Int, ASN1Object?> {
        val (lengthOffset, idOctets) = decodeIdentifierOctets(derEncoded, offset)
        val (contentOffset, length) = decodeLength(derEncoded, lengthOffset)
        val content = derEncoded.sliceArray(IntRange(contentOffset, contentOffset + length - 1))
        val nextOffset = contentOffset + length
        val decodedObject = if (idOctets.cls == ASN1TagClass.UNIVERSAL) {
            when (idOctets.tagNumber) {
                // Note: we currently don't support the following tags and will simply
                // return an ASN1RawObject() instance instead
                //
                //  - ObjectDescriptor (tag 0x07)
                //  - EXTERNAL (tag 0x08)
                //  - REAL (tag 0x09)
                //  - EMBEDDED PDV (tag 0x0b)
                //  - RELATIVE-OID (tag 0x0d)
                //  - DATE (tag 0x1f)
                //  - TIME-OF-DAY (tag 0x20)
                //  - DATE-TIME (tag 0x21)
                //  - DURATION (tag 0x22)
                //
                ASN1Boolean.TAG_NUMBER -> ASN1Boolean.parse(content)
                ASN1Null.TAG_NUMBER -> ASN1Null.parse(content)
                ASN1ObjectIdentifier.TAG_NUMBER -> ASN1ObjectIdentifier.parse(content)
                ASN1OctetString.TAG_NUMBER -> ASN1OctetString.parse(content)
                ASN1BitString.TAG_NUMBER -> ASN1BitString.parse(content)
                ASN1Sequence.TAG_NUMBER -> ASN1Sequence.parse(content)
                ASN1Set.TAG_NUMBER -> ASN1Set.parse(content)
                else -> {
                    if (ASN1IntegerTag.entries.find { it.tagNumber == idOctets.tagNumber } != null ) {
                        ASN1Integer.parse(content, idOctets.tagNumber)
                    } else if (ASN1StringTag.entries.find { it.tagNumber == idOctets.tagNumber } != null) {
                        ASN1String.parse(content, idOctets.tagNumber)
                    } else if (ASN1TimeTag.entries.find { it.tagNumber == idOctets.tagNumber } != null) {
                        ASN1Time.parse(content, idOctets.tagNumber)
                    } else {
                        ASN1RawObject(idOctets.cls, idOctets.encoding, idOctets.tagNumber, content)
                    }
                }
            }
        } else {
            ASN1TaggedObject.parse(idOctets.cls, idOctets.tagNumber, content)
        }
        return Pair(nextOffset, decodedObject)
    }

    fun decode(derEncoded: ByteArray): ASN1Object? {
        val (newOffset, obj) = decode(derEncoded, 0)
        if (newOffset != derEncoded.size) {
            throw IllegalArgumentException(
                "${newOffset - derEncoded.size} bytes leftover after decoding"
            )
        }
        return obj
    }

    fun decodeMultiple(derEncoded: ByteArray): List<ASN1Object> {
        val objects = mutableListOf<ASN1Object>()
        var offset = 0
        do {
            val (newOffset, obj) = decode(derEncoded, offset)
            if (obj != null) {
                objects.add(obj)
            }
            offset = newOffset
        } while (offset < derEncoded.size)
        return objects
    }

    fun encode(obj: ASN1Object): ByteArray {
        val builder = ByteStringBuilder()
        obj.encode(builder)
        return builder.toByteString().toByteArray()
    }

    fun print(
        sb: StringBuilder,
        indent: Int,
        obj: ASN1Object
    ) {
        for (n in IntRange(1, indent)) {
            sb.append(" ")
        }
        when (obj) {
            is ASN1Boolean -> {
                sb.append("BOOLEAN ${obj.value}\n")
            }
            is ASN1Integer -> {
                val label = when (obj.tagNumber) {
                    ASN1IntegerTag.INTEGER.tagNumber -> "INTEGER"
                    ASN1IntegerTag.ENUMERATED.tagNumber -> "ENUMERATED"
                    else -> throw IllegalArgumentException()
                }
                // TODO: always print as integer when we have BigInteger support
                try {
                    sb.append("$label ${obj.toLong()}\n")
                } catch (e: IllegalStateException) {
                    sb.append("$label ${obj.value.toHex()}\n")
                }
            }
            is ASN1Null -> {
                sb.append("NULL\n")
            }
            is ASN1ObjectIdentifier -> {
                sb.append("OBJECT IDENTIFIER ${obj.oid}")
                OID.lookupByOid(obj.oid)?.let { sb.append(" ${it.description}") }
                sb.append("\n")
            }
            is ASN1OctetString -> {
                sb.append("OCTET STRING (${obj.value.size} byte) ${obj.value.toHex()}\n")
            }
            is ASN1BitString -> {
                sb.append("BIT STRING (${obj.value.size*8 - obj.numUnusedBits} bit) ${obj.renderBitString()}\n")
            }
            is ASN1Time -> {
                val label = when (obj.tagNumber) {
                    ASN1TimeTag.GENERALIZED_TIME.tagNumber -> "GeneralizedTime"
                    ASN1TimeTag.UTC_TIME.tagNumber -> "UTCTime"
                    else -> throw IllegalArgumentException()
                }
                sb.append("$label ${obj.value}\n")
            }
            is ASN1String -> {
                val label = when (obj.tagNumber) {
                    ASN1StringTag.UTF8_STRING.tagNumber -> "UTF8String"
                    ASN1StringTag.NUMERIC_STRING.tagNumber -> "NumericString"
                    ASN1StringTag.PRINTABLE_STRING.tagNumber -> "PrintableString"
                    ASN1StringTag.TELETEX_STRING.tagNumber -> "TeletexString"
                    ASN1StringTag.VIDEOTEX_STRING.tagNumber -> "VideotexString"
                    ASN1StringTag.IA5_STRING.tagNumber -> "IA5String"
                    ASN1StringTag.GRAPHIC_STRING.tagNumber -> "GraphicString"
                    ASN1StringTag.VISIBLE_STRING.tagNumber -> "VisibleString"
                    ASN1StringTag.GENERAL_STRING.tagNumber -> "GeneralString"
                    ASN1StringTag.UNIVERSAL_STRING.tagNumber -> "UniversalString"
                    ASN1StringTag.CHARACTER_STRING.tagNumber -> "CharacterString"
                    ASN1StringTag.BMP_STRING.tagNumber -> "BmpString"
                    else -> throw IllegalArgumentException()
                }
                sb.append("$label ${obj.value}\n")
            }
            is ASN1Sequence -> {
                sb.append("SEQUENCE (${obj.elements.size} elem)\n")
                for (elem in obj.elements) {
                    print(sb, indent + 2, elem)
                }
            }
            is ASN1Set -> {
                sb.append("SET (${obj.elements.size} elem)\n")
                for (elem in obj.elements) {
                    print(sb, indent + 2, elem)
                }
            }
            is ASN1TaggedObject -> {
                when (obj.tagClass) {
                    ASN1TagClass.UNIVERSAL -> {
                        sb.append("[UNIVERSAL ${obj.tagNumber}] (1 elem)\n")
                    }
                    ASN1TagClass.APPLICATION -> {
                        sb.append("[APPLICATION ${obj.tagNumber}] (1 elem)\n")
                    }
                    ASN1TagClass.CONTEXT_SPECIFIC -> {
                        sb.append("[${obj.tagNumber}] (1 elem)\n")
                    }
                    ASN1TagClass.PRIVATE -> {
                        sb.append("[PRIVATE ${obj.tagNumber}] (1 elem)\n")
                    }
                }
                print(sb, indent + 2, obj.child)
            }
            is ASN1RawObject -> {
                sb.append("UNSUPPORTED TAG class=${obj.tagClass} encoding=${obj.encoding} ")
                sb.append("tag=${obj.tagNumber} value=${obj.content.toHex()}")
            }
            is ASN1PrimitiveValue -> {
                //throw IllegalStateException()
            }
        }
    }

    fun print(obj: ASN1Object): String {
        val sb = StringBuilder()
        print(sb, 0, obj)
        return sb.toString()
    }
}