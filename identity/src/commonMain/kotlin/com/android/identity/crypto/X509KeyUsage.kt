package com.android.identity.crypto

import com.android.identity.asn1.ASN1BitString

enum class X509KeyUsage(val bitNumber: Int) {
    DIGITAL_SIGNATURE(0),
    NON_REPUDIATION(1),
    KEY_ENCIPHERMENT(2),
    DATA_ENCIPHERMENT(3),
    KEY_AGREEMENT(4),
    KEY_CERT_SIGN(5),
    CRL_SIGN(6),
    ENCIPHER_ONLY(7),
    DECIPHER_ONLY(8)

    ;

    companion object {

        fun encodeSet(usages: Set<X509KeyUsage>): ASN1BitString {
            // Because the definitions is
            //
            //       KeyUsage ::= BIT STRING {
            //           digitalSignature        (0),
            //           nonRepudiation          (1), -- recent editions of X.509 have
            //                                -- renamed this bit to contentCommitment
            //           keyEncipherment         (2),
            //           dataEncipherment        (3),
            //           keyAgreement            (4),
            //           keyCertSign             (5),
            //           cRLSign                 (6),
            //           encipherOnly            (7),
            //           decipherOnly            (8) }
            //
            // we need to drop trailing zero-bits.
            //
            val booleans = (entries.map { usages.contains(it) })
            val idx = booleans.indexOfLast( { it == true } )
            val booleansReduced = if (idx < 0) {
                emptyList<Boolean>()
            } else {
                booleans.slice(IntRange(0, idx))
            }
            return ASN1BitString(booleansReduced.toBooleanArray())
        }

        fun decodeSet(encodedValue: ASN1BitString): Set<X509KeyUsage> {
            val booleans = encodedValue.asBooleans()
            val result = mutableSetOf<X509KeyUsage>()
            for (usage in entries) {
                if (usage.bitNumber < booleans.size) {
                    if (booleans[usage.bitNumber]) {
                        result.add(usage)
                    }
                }
            }
            return result
        }

    }
}
