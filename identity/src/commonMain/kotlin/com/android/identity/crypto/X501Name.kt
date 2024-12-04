package com.android.identity.crypto

import com.android.identity.asn1.ASN1
import com.android.identity.asn1.ASN1Sequence
import com.android.identity.asn1.ASN1String
import com.android.identity.util.toHex

/**
 * This represents a X.501 Name as used for X.509 certificates.
 *
 * @property components a map from OID to the value.
 */
class X501Name(val components: Map<String, ASN1String>) {

    override fun equals(other: Any?): Boolean = other is X501Name && name == other.name

    override fun hashCode(): Int = name.hashCode()

    // TODO: deal with escaping/unescaping ',' and '='

    /**
     *  The X.501 Name encoded according to
     *  [RFC 2253](https://datatracker.ietf.org/doc/html/rfc2253).
     */
    val name: String
        get() {
            val sb = StringBuilder()
            for (oid in components.keys.reversed()) {
                if (!sb.isEmpty()) {
                    sb.append(',')
                }
                val value = components[oid]!!
                val oidName = knownOids.get(oid)
                if (oidName != null) {
                    sb.append("$oidName=${value.value}")
                } else {
                    // From https://datatracker.ietf.org/doc/html/rfc2253#section-2.4
                    //
                    //   If the AttributeValue is of a type which does not have a string
                    //   representation defined for it, then it is simply encoded as an
                    //   octothorpe character ('#' ASCII 35) followed by the hexadecimal
                    //   representation of each of the bytes of the BER encoding of the X.500
                    //   AttributeValue.  This form SHOULD be used if the AttributeType is of
                    //   the dotted-decimal form.
                    //
                    sb.append("$oid=#${ASN1.encode(value).toHex()}")
                }
            }
            return sb.toString()
        }

    companion object {
        // From RFC 5280 Annex A, could add more as needed.
        private val knownOids = mapOf<String, String>(
            "2.5.4.3" to "CN",
            "2.5.4.6" to "C",
            "2.5.4.7" to "L",
            "2.5.4.8" to "ST",
            "2.5.4.10" to "O",
            "2.5.4.11" to "OU",
        )

        private val knownNames: Map<String, String> by lazy {
            knownOids.entries.associateBy({it.value}, {it.key})
        }

        /**
         * Builds a [X501Name] from the encoded form.
         *
         * For example, if passing the string `CN=David,ST=US-MA,O=Google,OU=Android,C=US` a
         * [X501Name] instance with the the [X501Name.components] property containing the
         * following entries
         *
         * - 2.5.4.6 -> ASN1UTF8String("US")
         * - 2.5.4.11 -> ASN1UTF8String("Android")
         * - 2.5.4.10 -> ASN1UTF8String("Google")
         * - 2.5.4.8 -> ASN1UTF8String("US-MA")
         * - 2.5.4.3 -> ASN1UTF8String("David")
         *
         * @param name an encoded form of a X.501 Name according to
         *   [RFC 2253](https://datatracker.ietf.org/doc/html/rfc2253).
         * @throws IllegalArgumentException if one of the keys isn't known.
         */
        fun fromName(name: String): X501Name {
            val components = mutableMapOf<String, ASN1String>()
            for (part in name.split(",").reversed()) {
                val pos = part.indexOf('=')
                if (pos < 0) {
                    throw IllegalArgumentException("No equal sign found in component")
                }
                val key = part.substring(0, pos)
                val value = part.substring(pos + 1)
                val oidForKey = knownNames[key]
                if (oidForKey == null) {
                    throw IllegalArgumentException("Unknown OID for $key")
                }
                components.put(oidForKey, ASN1String(value))
            }
            return X501Name(components)
        }
    }
}