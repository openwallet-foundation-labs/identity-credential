package com.android.mdl.appreader.issuerauth.vical

import co.nstant.`in`.cbor.model.DataItem

/**
 * This interface establishes the contract for the internal [Vical] and [CertificateInfo] decoders.
 * The interface is specific to the co.nstant.in.cbor CBOR package.
 *
 * @param <T> the type of data that will be decoded
 * @param <DI> the type of DataItem to be decoded
</DI></T> */
interface DataItemDecoder<T, DI : DataItem> {
    /**
     * Decodes the provided DataItem of type DI into data of type T.
     *
     * @param di the DataItem to be decoded
     * @return an object of type T
     * @throws Exception when error occurs during decoding process
     */
    @Throws(DataItemDecoderException::class)
    fun decode(di: DI): T
}