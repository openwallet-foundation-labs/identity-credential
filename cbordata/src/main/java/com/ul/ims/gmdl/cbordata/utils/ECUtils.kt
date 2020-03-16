/*
 * Copyright (C) 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package com.ul.ims.gmdl.cbordata.utils

import com.ul.ims.gmdl.cbordata.security.CoseKey
import com.ul.ims.gmdl.cbordata.security.EC2Curve
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.ECPointUtil
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec
import org.bouncycastle.math.ec.ECCurve
import org.bouncycastle.math.ec.custom.sec.SecP256R1Curve
import org.bouncycastle.math.ec.custom.sec.SecP384R1Curve
import org.bouncycastle.math.ec.custom.sec.SecP521R1Curve
import java.math.BigInteger
import java.security.Security
import java.security.spec.ECFieldFp
import java.security.spec.EllipticCurve

object ECUtils {
    private val CURVE_P256: ECCurve
    private val CURVE_P384: ECCurve
    private val CURVE_P521: ECCurve
    private val CURVE_BRAINPOOL_P256: ECCurve
    private val CURVE_BRAINPOOL_P320: ECCurve
    private val CURVE_BRAINPOOL_P384: ECCurve
    private val CURVE_BRAINPOOL_P512: ECCurve

    init {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }

        CURVE_P256 = SecP256R1Curve()
        CURVE_P384 = SecP384R1Curve()
        CURVE_P521 = SecP521R1Curve()

        CURVE_BRAINPOOL_P256 = ECNamedCurveTable.getParameterSpec("brainpoolP256r1").curve
        CURVE_BRAINPOOL_P320 = ECNamedCurveTable.getParameterSpec("brainpoolP320r1").curve
        CURVE_BRAINPOOL_P384 = ECNamedCurveTable.getParameterSpec("brainpoolP384r1").curve
        CURVE_BRAINPOOL_P512 = ECNamedCurveTable.getParameterSpec("brainpoolP512r1").curve
    }

    /**
     * Given a EC2Curve containing an id and x-coordinate, as well as the boolean corresponding to
     * the y-coordinate in compressed format, will return the uncompressed byte array representation
     * of the y-coordinate. Returns null if this cannot be determined.
     */
    fun deriveUncompressedYcoordinateFromCurve(curve: EC2Curve?, signBit: Boolean) : ByteArray? {
        curve?.id?.let { id ->
            curve.xCoordinate?.let { x ->
                val ecPointHeader =
                    if (signBit) {
                        byteArrayOf(0x03)
                    } else {
                        byteArrayOf(0x02)
                    }

                ec2CurveAndCoordinateLengthFromId(id)?.let { (ellipticCurve, coordinateLength) ->
                    val compressedPoint = ecPointHeader.plus(normaliseLength(x, coordinateLength))
                    val ecPoint = ECPointUtil.decodePoint(ellipticCurve, compressedPoint)

                    return normaliseLength(ecPoint.affineY.toByteArray(), coordinateLength)
                }
            }
        }

        return null
    }

    /**
     * Given an id of type ECCurve or ECNamedCurveParameterSpec, will return a pair
     * containing the EllipticCurve object and corresponding coordinate length (in bytes) for
     * this curve. Returns null if this cannot be determined.
     */
    private fun ec2CurveAndCoordinateLengthFromId(id: Any): Pair<EllipticCurve, Int>? {
        (id as? Int)?.let {
            ecCurveFromId(id)?.let {
                coordinateLengthFromCurve(it)?.let { length ->
                    return Pair(ecCurveToEllipticCurve(it), length)
                }
            }
        }

        return null
    }

    /**
     * Converts a curve ID as an integer to the corresponding ECCurve object
     */
    private fun ecCurveFromId(id: Int): ECCurve? {
        return when (id) {
            CoseKey.P256.value.toInt() -> CURVE_P256
            CoseKey.P384.value.toInt() -> CURVE_P384
            CoseKey.P521.value.toInt() -> CURVE_P521
            CoseKey.brainpoolP256r1.value.toInt() -> CURVE_BRAINPOOL_P256
            CoseKey.brainpoolP320r1.value.toInt() -> CURVE_BRAINPOOL_P320
            CoseKey.brainpoolP384r1.value.toInt() -> CURVE_BRAINPOOL_P384
            CoseKey.brainpoolP512r1.value.toInt() -> CURVE_BRAINPOOL_P512
            else -> null
        }
    }

    /**
     * Retrieves the expected length of coordinates (in bytes) given an elliptic curve matching one of:
     *  - secp256r1
     *  - secp384r1
     *  - secp521r1
     *  - brainpoolP256r1
     *  - brainpoolP320r1
     *  - brainpoolP384r1
     *  - brainpoolP512r1
     * Returns null if this cannot be determined.
     */
    private fun coordinateLengthFromCurve(curve: ECCurve): Int? {
        return when(curve) {
            CURVE_P256 -> 32
            CURVE_P384 -> 48
            CURVE_P521 -> 66
            CURVE_BRAINPOOL_P256 -> 32
            CURVE_BRAINPOOL_P320 -> 40
            CURVE_BRAINPOOL_P384 -> 48
            CURVE_BRAINPOOL_P512 -> 64
            else -> null
        }
    }

    /**
     * Converts between an ECCurve object and an EllipticCurve object
     */
    private fun ecCurveToEllipticCurve(curve: ECCurve): EllipticCurve {
        val a = BigInteger(1, curve.a.encoded)
        val b = BigInteger(1, curve.b.encoded)

        val field = ECFieldFp(curve.field.characteristic)

        return EllipticCurve(field, a, b)
    }

    /**
     * Takes a byte array and expected length and will return a byte array of that expected length
     * with the following conditions:
     *  - If input array is shorter than expected length, will be left padded with 0x00
     *  - If input array is longer, trim off any leading zeros and pad to expectedLength
     */
    private fun normaliseLength(input: ByteArray, expectedLength: Int) : ByteArray {
        var padded = input.copyOf()

        // Trim any leading zeros
        var leadingZeroCount = 0
        for (byte in input) {
            if (byte == 0x00.toByte()) {
                leadingZeroCount++
            } else {
                break
            }
        }
        padded = padded.copyOfRange(leadingZeroCount, padded.size)

        // If input is too short, pad most significant bytes with zeroes until it is expectedLength
        while (padded.size < expectedLength) {
            padded = byteArrayOf(0x00).plus(padded)
        }

        return padded
    }
}