package com.android.mdl.appreader.issuerauth.vical

import co.nstant.`in`.cbor.model.Array
import co.nstant.`in`.cbor.model.ByteString
import co.nstant.`in`.cbor.model.DataItem
import co.nstant.`in`.cbor.model.UnicodeString
import com.android.mdl.appreader.issuerauth.vical.CertificateInfo.Builder
import com.android.mdl.appreader.issuerauth.vical.CertificateInfo.Decoder
import com.android.mdl.appreader.issuerauth.vical.CertificateInfo.Encoder
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1Primitive
import org.bouncycastle.asn1.DERBitString
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x500.style.IETFUtils
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.util.Arrays
import org.bouncycastle.util.encoders.Hex
import java.io.ByteArrayInputStream
import java.io.IOException
import java.math.BigInteger
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.cert.*
import java.time.Instant
import java.util.*

/**
 * CertificateInfo represents the structure with the same name from From 18013-5 C.1.7.
 * This class contains a [Builder] to create a `CertificateInfo` instance.
 *
 *
 * In addition the class contains an [Encoder] and a [Decoder] to create a CBOR DataItem
 * out of the various fields in the class, which can then be translated to/from the binary representation.
 *
 *
 *
 * <pre>
 * CertificateInfo = {
 * "certificate" : bstr ; DER-encoded X.509 certificate
 * "serialNumber" : biguint ; value of the serial number field of the certificate
 * "ski" : bstr ; value of the Subject Key Identifier field of the certificate
 * "docType" : [+ DocType] ; DocType for which the certificate may be used as a trust point
 * ? "certificateProfile" : [+ CertificateProfile] ; Type of certificate
 * ? "issuingAuthority" : tstr ; Name of the certificate issuing authority
 * ? "issuingCountry" : tstr ; ISO3166-1 or ISO3166-2 depending on the issuing authority
 * ? "stateOrProvinceName" : tstr ; State or province name of the certificate issuing authority
 * ? "issuer" : bstr ; DER-encoded Issuer field of the certificate (i.e. the complete Name structure)
 * ? "subject" : bstr ; DER-encoded Subject field of the certificate (i.e. the complete Name structure)
 * ? "notBefore" : tdate ; value of the notBefore field of the certificate
 * ? "notAfter" : tdate ; value of the notAfter field of the certificate
 * ? "extensions" : Extensions ; Can be used for proprietary extensions
 * * tstr => any ; To be used for future extensions, all values are RFU
 * }
 *
</pre> *
 *
 * It is possible to add extensions as key /value pairs, where the key must be a CBOR UnicodeString.
 * Adding RFU is not supported, but any encoded RFU's will be made available and contained.
 * An extension or RFU that doesn't have a UnicodeString as key will result in a decoder exception.
 *
 * @author UL Solutions
 */
// NOTE class currently not made Serializable due to the extensions (CBOR DataItem) not being serializable
// equals and hashCode have been implemented though
class CertificateInfo private constructor(private val fields: Fields) {

    /**
     * Data class used for the various fields of CertificateInfo.
     * This is a mutable class that can be used in the CertificateInfo class as well as the
     * builder, encoder & decoder classes.
     */
    data class Fields (
        val certificate: X509Certificate,
        val serialNumber: BigInteger,
        val ski: ByteArray,
        val docTypes: Set<String>) {
        // var? issuingAuthority: String


        var version: String? = null

        // optional (nullable) fields, copied from certificate
        var certificateProfile: MutableSet<String> = HashSet()
        var issuingCountry: String? = null
        var stateOrProvinceName: String? = null
        var issuer: ByteArray? = null
        var subject: ByteArray? = null
        var notBefore: Instant? = null
        var notAfter: Instant? = null

        // optional (nullable) field, provided separately
        var issuingAuthority: String? = null

        // lazy instantiation, null means no extensions (as it is an optional keyed field)
        var extensions: co.nstant.`in`.cbor.model.Map? = null

        // always instantiated, empty means no RFU
        var rfu: co.nstant.`in`.cbor.model.Map = co.nstant.`in`.cbor.model.Map()
    }

    /**
     * The builder to create a CertificateInfo instance.
     *
     * The resulting `CertificateInfo` instances can be used as input to [Builder].
     */
    class Builder(
        cert: X509Certificate,
        docTypes: Set<String>,
        optionalCertificateFields: Set<OptionalCertificateInfoKey>
    ) : InstanceBuilder<CertificateInfo?> {

        private lateinit var fields: Fields
        private lateinit var certHolder: X509CertificateHolder

        init {
            val ski = getSubjectPublicKeyIdentifier(cert)
            val serialNumber = cert.serialNumber
            this.fields = Fields(cert, serialNumber, ski, docTypes)
            this.certHolder = X509CertificateHolder(cert.getEncoded())
            optionalCertificateFields.forEach{copyCertificateInformation(it)}
        }

        private fun copyCertificateInformation(certificateInfoKey: OptionalCertificateInfoKey) {
            when (certificateInfoKey) {
                OptionalCertificateInfoKey.CERTIFICATE_PROFILE -> copyCertificateProfile()
                OptionalCertificateInfoKey.ISSUING_AUTHORITY -> copyIssuingAuthority()
                OptionalCertificateInfoKey.ISSUING_COUNTRY -> copyIssuingCountry()
                OptionalCertificateInfoKey.STATE_OR_PROVINCE_NAME -> copyStateOrProvince()
                OptionalCertificateInfoKey.ISSUER -> copyIssuer()
                OptionalCertificateInfoKey.SUBJECT -> copySubject()
                OptionalCertificateInfoKey.NOT_BEFORE -> copyNotBefore()
                OptionalCertificateInfoKey.NOT_AFTER -> copyNotAfter()
                else -> throw RuntimeException(
                    "Don't know how to copy the field $certificateInfoKey from the certificate"
                )
            }
        }

        /**
         * Can be used to indicate the issuing authority of the certificate if it is different from the certificate.
         *
         * @param issuingAuthority the issuing authority, not null
         */
        fun indicateIssuingAuthority(issuingAuthority: String?) {
            requireNotNull(issuingAuthority) { "The indicated issuingAuthority should not be null" }
            this.fields.issuingAuthority = issuingAuthority
        }

        /**
         * Can be used to indicate the issuing country of the certificate if it is different from the certificate.
         *
         * @param issuingCountry the issuing country, not null
         */
        fun indicateIssuingCountry(issuingCountry: String?) {
            requireNotNull(issuingCountry) { "The indicated issuingCountry should not be null" }
            this.fields.issuingCountry = issuingCountry
        }

        /**
         * Can be used to indicate the state or province name of the issuing authority.
         *
         * @param stateOrProvinceName the state or province name of the issuing authority, not null
         */
        fun indicateStateOrProvinceName(stateOrProvinceName: String?) {
            requireNotNull(stateOrProvinceName) { "The indicated stateOrProvinceName should not be null" }
            this.fields.stateOrProvinceName = stateOrProvinceName
        }

        /**
         * Adds an extension. If no extensions are indicated then the field will not be present.
         * @param key the key of the extension
         * @param value the value of the extension
         */
        fun addExtension(key: UnicodeString?, value: DataItem?) {
            if (this.fields.extensions == null) {
                this.fields.extensions = co.nstant.`in`.cbor.model.Map()
            }
            this.fields.extensions!!.put(key, value)
        }

        /**
         * Adds a possibly unknown key / value to the CertificateInfo.
         * This method does not perform any verification and it allows overwriting of existing, known key / value pairs.
         *
         * @param key the key
         * @param value the value
         */
        fun addRFU(key: UnicodeString?, value: DataItem?) {
            this.fields.rfu.put(key, value)
        }

        /*
         * Should probably not be used; the Extended Key Usage extension should not be present in IACA certificates.
         */
        private fun copyCertificateProfile() {
            // WARNING: specification is unclear w.r.t. how certificateProfile is formatted,
            // or how to use this key / value pair
            val keyUsages: List<String>? = try {
                this.fields.certificate.extendedKeyUsage
            } catch (e: CertificateParsingException) {
                // TODO provide better runtime exception
                throw RuntimeException(
                    "Could not decode extended key usage for certificate profile",
                    e
                )
            }
            if (keyUsages != null) {
                this.fields.certificateProfile.addAll(keyUsages)
                for (keyUsage in keyUsages) {
                    this.fields.certificateProfile.add("urn:oid:$keyUsage")
                }
            }

            // certInfo.certificateProfile = certificateProfiles;
        }

        private fun copyIssuingAuthority() {
            this.fields.issuingAuthority = this.certHolder.issuer.toString()
        }

        private fun copyIssuingCountry() {
            val issuer = certHolder.issuer
            val rdns = certHolder.issuer.getRDNs(BCStyle.C)
            if (rdns.size != 1) {
                throw RuntimeException(
                    "No country or multiple countries indicated for issuer: $issuer"
                )
            }
            val countryTypesAndValues = rdns[0].typesAndValues
            if (countryTypesAndValues.size != 1) {
                throw RuntimeException(
                    "No country types or values or multiple country types and values indicated for issuer: "
                            + issuer.toString()
                )
            }
            this.fields.issuingCountry = IETFUtils.valueToString(
                countryTypesAndValues[0].value
            )
        }

        private fun copyStateOrProvince() {
            val issuer = certHolder.issuer
            val rdns = certHolder.issuer.getRDNs(BCStyle.ST)
            if (rdns.size != 1) {
                // TODO think of ways to make this "copy if present" in settings
                //                throw new RuntimeException(
                //                        "No state/province or multiple state/provinces indicated for issuer: " + issuer.toString());
                return
            }
            val stateOrProvinceTypesAndValues = rdns[0].typesAndValues
            if (stateOrProvinceTypesAndValues.size != 1) {
                throw RuntimeException(
                    "No state/province types or values or multiple state/province types and values indicated for issuer: "
                            + issuer.toString()
                )
            }
            this.fields.stateOrProvinceName = IETFUtils.valueToString(
                stateOrProvinceTypesAndValues[0].value
            )
        }

        // TODO check if this is indeed a "binary copy"
        private fun copyIssuer() {
            try {
                this.fields.issuer = certHolder.issuer.encoded
            } catch (e: IOException) {
                throw RuntimeException("Could not re-encode issuer", e)
            }
        }

        // TODO check if this is indeed a "binary copy"
        private fun copySubject() {
            try {
                this.fields.subject = certHolder.subject.encoded
            } catch (e: IOException) {
                throw RuntimeException("Could not re-encode subject", e)
            }
        }

        private fun copyNotBefore() {
            this.fields.notBefore = certHolder.notBefore.toInstant()
        }

        private fun copyNotAfter() {
            this.fields.notAfter = certHolder.notAfter.toInstant()
        }

        override fun build(): CertificateInfo {
            return CertificateInfo(this.fields)
        }

        /**
         * Creates a CertificateInfo by copying the indicated fields from the certificate
         * as well as the certificate itself.
         * It is highly recommended to set the docTypes for which the certificate is valid.
         * If this is not indicated then the certificate is valid for any document type.
         *
         * @param cert                      the certificate described by this `CertificateInfo` instance.
         * @param docTypes                  the docTypes that this certificate can be used for; at least one has to be provided
         * @param optionalCertificateFields any optional fields in a table, see [CertificateInfoKey]
         * for more information
         */
        init {
            require(fields.docTypes.isNotEmpty()) { "At least one docType has to be provided for each certificate" }
            // fields.certInfo = CertificateInfo(fields.cert, fields.docTypes)
            try {
                certHolder = X509CertificateHolder(fields.certificate.encoded)
            } catch (e: CertificateEncodingException) {
                throw RuntimeException(
                    "Error re-coding the certificate to X509CertificateHolder",
                    e
                )
            } catch (e: IOException) {
                throw RuntimeException(
                    "Error re-coding the certificate to X509CertificateHolder",
                    e
                )
            }
            for (certificateInfoKey in optionalCertificateFields) {
                copyCertificateInformation(certificateInfoKey)
            }
        }
    }

    /**
     * An encoder to encode instances of the `CertificateInfo` class.
     *
     * This class is usually called by [com.android.mdl.appreader.issuerauth.vical.Vical.Encoder] directly.
     */
    class Encoder: DataItemEncoder<co.nstant.`in`.cbor.model.Map, CertificateInfo> {

        /**
         * Encodes a CertificateInfo into a CBOR structure, which is part of the overall VICAL structure.
         * The CBOR structure can be encoded to binary using [CborEncoder.encode].
         * Usually the VICAL structure is converted to binary as a whole instead.
         *
         * @param certificateInfo The `CertificateInfo` instance to encode
         * @return the encoded `CertificateInfo` instance as CBOR structure
         */
        override fun encode(t: CertificateInfo): co.nstant.`in`.cbor.model.Map {
            val certificateInfo = t
            val map = co.nstant.`in`.cbor.model.Map(4)
            try {
                map.put(
                    RequiredCertificateInfoKey.CERTIFICATE.getUnicodeString(), ByteString(
                        certificateInfo.fields.certificate.encoded
                    )
                )
            } catch (e: CertificateEncodingException) {
                // TODO provide better runtime exception
                throw RuntimeException("Uncaught exception, blame developer", e)
            }
            val string = ByteString(
                toUnsigned(
                    // TODO skip if not present instead of crashing
                    certificateInfo.fields.serialNumber!!
                )
            )
            string.setTag(TAG_BIGUINT.toLong())
            map.put(RequiredCertificateInfoKey.SERIAL_NUMBER.getUnicodeString(), string)
            map.put(
                RequiredCertificateInfoKey.SKI.getUnicodeString(), ByteString(
                    certificateInfo.fields.ski
                )
            )
            val profiles = certificateInfo.fields.certificateProfile
            if (profiles.isNotEmpty()) {
                val profileArray = Array()
                for (profile in profiles) {
                    profileArray.add(UnicodeString(profile))
                }
                map.put(
                    OptionalCertificateInfoKey.CERTIFICATE_PROFILE.getUnicodeString(),
                    profileArray
                )
            }
            val issuingAuthority = certificateInfo.fields.issuingAuthority
            if (issuingAuthority != null) {
                map.put(
                    OptionalCertificateInfoKey.ISSUING_AUTHORITY.getUnicodeString(),
                    UnicodeString(issuingAuthority)
                )
            }
            val issuingCountry = certificateInfo.fields.issuingCountry
            if (issuingCountry != null) {
                map.put(
                    OptionalCertificateInfoKey.ISSUING_COUNTRY.getUnicodeString(),
                    UnicodeString(issuingCountry)
                )
            }
            val stateOrProvinceName = certificateInfo.fields.stateOrProvinceName
            if (stateOrProvinceName != null) {
                map.put(
                    OptionalCertificateInfoKey.STATE_OR_PROVINCE_NAME.getUnicodeString(),
                    UnicodeString(stateOrProvinceName)
                )
            }
            val issuer = certificateInfo.fields.issuer
            if (issuer != null) {
                map.put(
                    OptionalCertificateInfoKey.ISSUER.getUnicodeString(),
                    ByteString(issuer)
                )
            }
            val subject = certificateInfo.fields.subject
            if (subject != null) {
                map.put(
                    OptionalCertificateInfoKey.SUBJECT.getUnicodeString(),
                    ByteString(subject)
                )
            }
            val notBefore = certificateInfo.fields.notBefore
            if (notBefore != null) {
                map.put(
                    OptionalCertificateInfoKey.NOT_BEFORE.getUnicodeString(),
                    Util.createTDate(notBefore)
                )
            }
            val notAfter = certificateInfo.fields.notAfter
            if (notAfter != null) {
                map.put(
                    OptionalCertificateInfoKey.NOT_AFTER.getUnicodeString(),
                    Util.createTDate(notAfter)
                )
            }
            val docTypes = certificateInfo.fields.docTypes
            val docTypeArray = Array(docTypes.size)
            for (docType in docTypes) {
                docTypeArray.add(UnicodeString(docType))
            }
            map.put(RequiredCertificateInfoKey.DOC_TYPE.getUnicodeString(), docTypeArray)

            // extensions is directly put in; it should contain a map in all probability, but it is defined as any
            val extensions = certificateInfo.fields.extensions
            if (extensions != null) {
                map.put(OptionalCertificateInfoKey.EXTENSIONS.getUnicodeString(), extensions)
            }
            val rfu = certificateInfo.fields.rfu
            val entrySet: Set<Map.Entry<UnicodeString, DataItem>> = Util.getEntrySet(rfu)
            for ((key, value) in entrySet) {
                map.put(key, value)
            }
            return map
        }

        companion object {
            private const val TAG_BIGUINT = 2
            private fun toUnsigned(i: BigInteger): ByteArray {
                val signed = i.toByteArray()
                return if (signed[0] != 0x00.toByte()) {
                    signed
                } else Arrays.copyOfRange(signed, 1, signed.size)
            }
        }
    }

    /**
     *
     * An decoder to decode instances of the `CertificateInfo` class.
     *
     * This class is usually called by [com.android.mdl.appreader.issuerauth.vical.Vical.Decoder] directly
     * after which the `CertificateInfo` instances can be retrieved using [Vical.certificateInfos].
     *
     * Currently the decoder does not support any undefined or RFU fields.
     */
    class Decoder : DataItemDecoder<CertificateInfo, co.nstant.`in`.cbor.model.Map> {
        @Throws(DataItemDecoderException::class)
        override fun decode(di: co.nstant.`in`.cbor.model.Map): CertificateInfo {

            val map = di
            // var fields: CertificateInfoFields

            // === first get the required fields and create the instance
            val fields: Fields
            try {
                val cert = decodeCertificate(map)
                val serialNumber = decodeSerialNumber(map)
                val ski = decodeSki(map)
                val docTypes = decodeDocType(map)
                fields = Fields(cert, serialNumber, ski, docTypes)

            } catch (e: CertificateException) {
                throw DataItemDecoderException("Could not decode certificate")
            }


            // === now get the optional fields
            //     CERTIFICATE_PROFILE, ISSUING_AUTHORITY, ISSUING_COUNTRY, STATE_OR_PROVINCE_NAME, ISSUER, SUBJECT,
            // NOT_BEFORE, NOT_AFTER, EXTENSIONS;
            fields.certificateProfile = decodeCertificateProfile(map)
            fields.issuingAuthority = decodeIssuingAuthority(map)
            fields.issuingCountry = decodeIssuingCountry(map)
            fields.stateOrProvinceName = decodeStateOrProvinceName(map)
            fields.issuer = decodeIssuer(map)
            fields.subject = decodeSubject(map)
            fields.notBefore = decodeNotBefore(map)
            fields.notAfter = decodeNotAfter(map)
            fields.extensions = decodeExtensions(map)
            fields.rfu = decodeRFU(map)


            // TODO more things to decode

            //            Set<Entry<UnicodeString,DataItem>> entrySet = map.getEntrySet();
            //            for (Entry<UnicodeString, DataItem> entry : entrySet) {
            //                String keyName = entry.getKey().getString();
            //                Optional<CertificateInfoKey> knownKey = CertificateInfoKey.forKeyName(keyName);
            //                if (knownKey .isEmpty()) {
            //                    // ignore
            //                    // TODO add to rfu
            //                    continue;
            //                }
            //                CertificateInfoKey key = knownKey.get();
            //                if (key instanceof RequiredCertificateInfoKey) {
            //                    // ignore, already retrieved
            //                    continue;
            //                }
            //
            //                OptionalCertificateInfoKey optKey = (OptionalCertificateInfoKey) key;
            //                switch (optKey) {
            //                case CERTIFICATE_PROFILE:
            //
            //                }
            //            }
            return CertificateInfo(fields)
        }

        @Throws(DataItemDecoderException::class)
        private fun decodeCertificate(map: co.nstant.`in`.cbor.model.Map): X509Certificate {
            val certificateDI = map[RequiredCertificateInfoKey.CERTIFICATE.getUnicodeString()]
            if (certificateDI !is ByteString) {
                throw DataItemDecoderException(certificateDI.javaClass.typeName)
            }
            val certData = certificateDI.bytes
            val fact: CertificateFactory = try {
                CertificateFactory.getInstance("X509")
            } catch (e: CertificateException) {
                throw RuntimeException("Required X.509 certificate factory not available", e)
            }
            val signingCert: X509Certificate = try {
                fact
                    .generateCertificate(ByteArrayInputStream(certData)) as X509Certificate
            } catch (e: CertificateException) {
                throw DataItemDecoderException("Uncaught exception, blame developer", e)
            }
            return signingCert
        }

        private fun decodeSerialNumber(map: co.nstant.`in`.cbor.model.Map): BigInteger {
            val certificateDI =
                map[RequiredCertificateInfoKey.SERIAL_NUMBER.getUnicodeString()] as? ByteString
                    ?: // TODO refactor ot better exception
                    throw RuntimeException()
            if (!certificateDI.hasTag() || certificateDI.tag.value != TAG_BIGUINT.toLong()) {
                // TODO refactor to better exception
                throw RuntimeException()
            }
            return BigInteger(1, certificateDI.bytes)
        }

        private fun decodeSki(map: co.nstant.`in`.cbor.model.Map): ByteArray {
            val certificateDI =
                map[RequiredCertificateInfoKey.SKI.getUnicodeString()] as? ByteString
                    ?: // TODO refactor or better exception
                    throw RuntimeException()

            // TODO test tag?
            return certificateDI.bytes
        }

        @Throws(CertificateException::class)
        private fun decodeDocType(map: co.nstant.`in`.cbor.model.Map): Set<String> {
            val docTypeDI = map[RequiredCertificateInfoKey.DOC_TYPE.getUnicodeString()] as Array
            val docTypes: MutableSet<String> = HashSet()
            val dataItems: List<*> = docTypeDI.dataItems
            for (dataItem in dataItems) {
                if (dataItem !is UnicodeString) {

                    // DEBUG don't skip, used to avoid AAMVA BREAK of indefinite length array
                    continue
                    // TODO refactor to better exception
                    // throw new RuntimeException();
                }
                val docType: UnicodeString = dataItem
                docTypes.add(docType.string)
            }
            return docTypes
        }

        private fun decodeCertificateProfile(map: co.nstant.`in`.cbor.model.Map): MutableSet<String> {
            val certificateProfiles: MutableSet<String> = HashSet()
            val certificateProfileDI =
                map[OptionalCertificateInfoKey.CERTIFICATE_PROFILE.getUnicodeString()]
                    ?: return certificateProfiles
            if (certificateProfileDI !is Array) {
                // TODO refactor to better exception
                throw RuntimeException(certificateProfileDI.javaClass.typeName)
            }
            val dataItems: List<*> = certificateProfileDI.dataItems
            for (dataItem in dataItems) {
                if (dataItem !is UnicodeString) {
                    // TODO refactor to better exception
                    throw RuntimeException()
                }
                val docType: UnicodeString = dataItem
                certificateProfiles.add(docType.string)
            }
            return certificateProfiles
        }

        private fun decodeIssuingAuthority(map: co.nstant.`in`.cbor.model.Map): String? {
            val issuingAuthorityDI =
                (map[OptionalCertificateInfoKey.ISSUING_AUTHORITY.getUnicodeString()]
                    ?: return null) as? UnicodeString
                    ?: // TODO refactor ot better exception
                    throw RuntimeException()
            val issuingAuthority: UnicodeString = issuingAuthorityDI
            return issuingAuthority.string
        }

        private fun decodeIssuingCountry(map: co.nstant.`in`.cbor.model.Map): String? {
            val issuingCountryDI =
                (map[OptionalCertificateInfoKey.ISSUING_COUNTRY.getUnicodeString()]
                    ?: return null) as? UnicodeString
                    ?: // TODO refactor ot better exception
                    throw RuntimeException()
            val issuingCountry: UnicodeString = issuingCountryDI
            val issuingCountryStr: String = issuingCountry.string
            if (issuingCountryStr.length < 2 || issuingCountryStr.length > 3) {
                // TODO refactor ot better exception
                throw RuntimeException()
            }
            return issuingCountryStr
        }

        private fun decodeStateOrProvinceName(map: co.nstant.`in`.cbor.model.Map): String? {
            val issuingStateOrProvinceNameDI = (map
                .get(OptionalCertificateInfoKey.STATE_OR_PROVINCE_NAME.getUnicodeString())
                ?: return null) as? UnicodeString
                ?: // TODO refactor ot better exception
                throw RuntimeException()
            val issuingStateOrProvinceName: UnicodeString =
                issuingStateOrProvinceNameDI
            return issuingStateOrProvinceName.string
        }

        private fun decodeIssuer(map: co.nstant.`in`.cbor.model.Map): ByteArray? {
            val issuerDI = (map[OptionalCertificateInfoKey.ISSUER.getUnicodeString()]
                ?: return null) as? ByteString
                ?: // TODO refactor ot better exception
                throw RuntimeException()
            return issuerDI.bytes
        }

        private fun decodeSubject(map: co.nstant.`in`.cbor.model.Map): ByteArray? {
            val subjectDI = (map[OptionalCertificateInfoKey.ISSUER.getUnicodeString()]
                ?: return null) as? ByteString
                ?: // TODO refactor ot better exception
                throw RuntimeException()
            return subjectDI.bytes
        }

        private fun decodeNotBefore(map: co.nstant.`in`.cbor.model.Map): Instant? {
            val tdateDI = map[OptionalCertificateInfoKey.NOT_BEFORE.getUnicodeString()]
                ?: return null
            return Util.parseTDate(tdateDI)
        }

        private fun decodeNotAfter(map: co.nstant.`in`.cbor.model.Map): Instant? {
            val tdateDI = map[OptionalCertificateInfoKey.NOT_AFTER.getUnicodeString()]
                ?: return null
            return Util.parseTDate(tdateDI)
        }

        @Throws(DataItemDecoderException::class)
        private fun decodeExtensions(map: co.nstant.`in`.cbor.model.Map): co.nstant.`in`.cbor.model.Map? {
            val extensionsDI =
                map[OptionalCertificateInfoKey.EXTENSIONS.getUnicodeString()] ?: return null
            return Util.toVicalCompatibleMap("extensions", extensionsDI)
        }

        @Throws(DataItemDecoderException::class)
        private fun decodeRFU(map: co.nstant.`in`.cbor.model.Map): co.nstant.`in`.cbor.model.Map {
            val rfu = co.nstant.`in`.cbor.model.Map()
            KEYS@ for (key in map.keys) {
                if (key !is UnicodeString) {
                    throw DataItemDecoderException("key in RFU is not of type UnicodeString")
                }

                // TODO this is a bit laborsome, maybe do something
                for (requiredKey in EnumSet.allOf(
                    RequiredCertificateInfoKey::class.java
                )) {
                    if (key == requiredKey.getUnicodeString()) {
                        continue@KEYS
                    }
                }
                for (optionalKey in EnumSet.allOf(
                    OptionalCertificateInfoKey::class.java
                )) {
                    if (key == optionalKey.getUnicodeString()) {
                        continue@KEYS
                    }
                }
                rfu.put(key, map[key])
            }
            return rfu
        }
    }

    //    private var version: String? = null
//    private var certificate: X509Certificate
//    private var serialNumber: BigInteger
//    private var ski: ByteArray
//    private var docTypes: Set<String?>
//
//    // optional (nullable) fields, copied from certificate
//    private var certificateProfile: MutableSet<String> = HashSet()
//    private var issuingCountry: String? = null
//    private var stateOrProvinceName: String? = null
//    private var issuer: ByteArray? = null
//    private var subject: ByteArray? = null
//    private var notBefore: Instant? = null
//    private var notAfter: Instant? = null
//
//    // optional (nullable) field, provided separately
//    private var issuingAuthority: String? = null
//
//    // lazy instantiation, null means no extensions (as it is an optional keyed field)
//    private var extensions: co.nstant.`in`.cbor.model.Map? = null
//
//    // always instantiated, empty means no RFU
//    private var rfu: co.nstant.`in`.cbor.model.Map

    val version: String?
        get() {
            return fields.version
        }

    val certificate: X509Certificate
        get() {
            return fields.certificate
        }

    val serialNumber: BigInteger?
        get() {
            return fields.serialNumber
        }

    /**
     * Returns the subject public key field for the certificate;
     * this should be the same as the SPKI contained within the certificate.
     *
     * @return the SubjectPublicKeyInfo field
     */
    val ski: ByteArray
        get() {
            return fields.ski!!.clone()
        }

    val docTypes: Set<String>
        get() {
            return fields.docTypes
        }

    val certificateProfile: Set<String>
        get() {
            return fields.certificateProfile
        }

    val issuingAuthority: String?
        get() {
            return fields.issuingAuthority
        }

    val issuingCountry: String?
        get() {
            return fields.issuingCountry
        }

    val stateOrProvinceName: String?
        get() {
            return fields.stateOrProvinceName
        }

    val issuer: ByteArray?
        get() {
            return fields.issuer
        }

    val subject: ByteArray?
        get() {
            return fields.subject
        }

    val notBefore: Instant?
        get() {
            return fields.notBefore
        }

    val notAfter: Instant?
        get() {
            return fields.notAfter
        }

    /**
     * Returns empty or a map of extensions.
     * This map may still be empty if the CertificateInfo structure was encoded as such.
     * @return empty
     */
    val extensions: co.nstant.`in`.cbor.model.Map?
        get() {
           return fields.extensions
        }

    /**
     * Returns a possibly empty map of RFU values, i.e. any key that is not defined in the current version 1 of the standard.
     * @return a map of all the undefined key / value pairs in the CertificateInfo structure
     */
    val rfu: co.nstant.`in`.cbor.model.Map
        get() {
            return fields.rfu
        }

    /**
     * Returns a multi-line description of this CertificateInfo instance.
     */
    override fun toString(): String {
        val sb = StringBuilder()
        //        sb.append(String.format("Version: %s%n", this.version));
        sb.append(
            String.format(
                "certificate: %s%n", Hex.toHexString(
                    calculateID(
                        certificate
                    )
                )
            )
        )
        sb.append(String.format("serialNumber: %x%n", serialNumber))
        sb.append(String.format("ski: %s%n", Hex.toHexString(ski)))
        sb.append(String.format("docType (set): %s%n", docTypes))
        sb.append(String.format("certificateProfile: %s%n", certificateProfile))
        sb.append(
            String.format(
                "issuingAuthority: %s%n", valueOrNone(
                    issuingAuthority
                )
            )
        )
        sb.append(
            String.format(
                "issuingCountry: %s%n", valueOrNone(
                    issuingCountry
                )
            )
        )
        sb.append(
            String.format(
                "stateOrProvinceName: %s%n", valueOrNone(
                    stateOrProvinceName
                )
            )
        )
        val issuerString = if (issuer == null) "<none>" else Hex.toHexString(issuer)
        sb.append(String.format("issuer: %s%n", issuerString))
        val subjectString = if (subject == null) "<none>" else Hex.toHexString(subject)
        sb.append(String.format("subject: %s%n", subjectString))
        val notBeforeString = if (notBefore == null) "<none>" else Util.visualTDate(notBefore)
        sb.append(String.format("notBefore: %s%n", notBeforeString))
        val notAfterString = if (notAfter == null) "<none>" else Util.visualTDate(notAfter)
        sb.append(String.format("notAfter: %s%n", notAfterString))
        sb.append(String.format("extensions: %s%n", extensions))
        sb.append(String.format("any: %s%n", rfu))
        return sb.toString()
    }

    companion object {
        private const val TAG_BIGUINT = 2
        private fun getSubjectPublicKeyIdentifier(cert: X509Certificate): ByteArray {
            val doubleWrappedSKI = cert.getExtensionValue(Extension.subjectKeyIdentifier.id)
            return try {
                stripEncoding(stripEncoding(doubleWrappedSKI))
            } catch (e: Exception) {
                throw RuntimeException(
                    "Could not remove OCTETSTRING or BITSTRING encoding around SubjectPublicKeyIdentifier",
                    e
                )
            }
        }

        /**
         * Removes an
         *
         * @param encodedValue
         * @return
         */
        private fun stripEncoding(encodedValue: ByteArray): ByteArray {
            val derObject: ASN1Primitive
            try {
                ASN1InputStream(ByteArrayInputStream(encodedValue)).use { asn1InputStream ->
                    derObject = asn1InputStream.readObject()
                }
            } catch (e: IOException) {
                throw RuntimeException("I/O exception when reading from memory stream", e)
            }
            return when (derObject) {
                is DEROctetString -> {
                    derObject.octets
                }

                is DERBitString -> {
                    if (derObject.padBits != 0) {
                        throw RuntimeException("Number of bits in DERBitString is not alligned")
                    }
                    derObject.bytes
                }

                else -> {
                    // TODO check if the ski is always wrapped this way
                    throw RuntimeException("Expected double wrapped octet string for subjectkeyidentifier")
                }
            }
        }

        private fun valueOrNone(value: String?): String {
            return value ?: "<none>"
        }

        private fun calculateID(cert: X509Certificate): ByteArray {
            return try {
                val certData = cert.encoded
                val sha1 = MessageDigest.getInstance("SHA-1")
                sha1.digest(certData)
            } catch (e: CertificateEncodingException) {
                throw RuntimeException("Could not encode certificate while calculating ID", e)
            } catch (e: NoSuchAlgorithmException) {
                throw RuntimeException("Required algorithm not available", e)
            }
        }
    }
}