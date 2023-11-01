package com.android.mdl.appreader.issuerauth.vical

import co.nstant.`in`.cbor.model.UnicodeString
import java.util.Locale

/**
 * Represents a (UnicodeString) key in a CDDL defined MAP.
 * This interface automatically converts the enum identifiers to camelCase identifiers used in the CBOR structures.
 *
 * @author UL TS BV
 */
interface Key {
    /**
     * Always implemented by enums, required to access it using the default methods.
     *
     * @return the name of the enum
     */
    val name: String

    /**
     * Gets the name of the key, also returned as UnicodeString.
     *
     * @return the name of the key in camelCase, e.g. `DOC_TYPE -> docType`
     */
    fun keyName(): String {
        val keyNameSB = StringBuilder()
        val elts = name.split("_").toTypedArray()
        keyNameSB.append(elts[0].lowercase(Locale.getDefault()))
        for (i in 1 until elts.size) {
            val keyName = elts[i]
            keyNameSB.append(keyName[0])
            keyNameSB.append(keyName.substring(1).lowercase(Locale.getDefault()))
        }
        return keyNameSB.toString()
    }

    fun getUnicodeString(): UnicodeString {
        return UnicodeString(keyName())
    }

    companion object {
        fun enumToKeyName(enumName: String): String {
            val keyNameSB = StringBuilder()
            val elts = enumName.split("_").toTypedArray()
            keyNameSB.append(elts[0].lowercase(Locale.getDefault()))
            for (i in 1 until elts.size) {
                val keyName = elts[i]
                keyNameSB.append(keyName[0])
                keyNameSB.append(keyName.substring(1).lowercase(Locale.getDefault()))
            }
            return keyNameSB.toString()
        }

        fun keyNameToEnum(keyName: String): String {
            val enumSB = StringBuilder()
            // positive lookahead for an uppercase character, i.e. not part of the match
            val elts = keyName.split("(?=\\p{Lu})").toTypedArray()
            enumSB.append(elts[0].uppercase(Locale.getDefault()))
            for (i in 1 until elts.size) {
                val enumName = elts[i]
                enumSB.append("_")
                enumSB.append(enumName.uppercase(Locale.getDefault()))
            }
            return enumSB.toString()
        }
    }
}