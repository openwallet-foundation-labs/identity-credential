package com.android.mdl.appreader.issuerauth.vical

import co.nstant.`in`.cbor.model.DataItem

/**
 * This interface establishes the contract for the internal [Vical] and [CertificateInfo] encoders.
 *
 * The interface is specific to the co.nstant.in.cbor CBOR package.
 * @author UL TS BV
 *
 * @param <DI>
 * @param <T>
</T></DI> */
interface DataItemEncoder<DI : DataItem, T> {
    /**
     * Encodes the provided data of type T into a DataItem of type DI.
     *
     * @param t the data to be encoded
     * @return a DataItem of type DI
     */
    fun encode(t: T): DI

    fun encodeToBytes(t: T): ByteArray {
        val di = encode(t)
        return IdentityUtil.cborEncode(di)
    }
}