package com.android.mdl.appreader.issuerauth.vical

import co.nstant.`in`.cbor.model.*
import co.nstant.`in`.cbor.model.Map
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAccessor
import java.util.regex.Pattern

object Util {
    val TAG_TDATE = Tag(0)

    fun createTDate(instant: Instant?): DataItem {
        val tdateString = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC).format(instant)
        val tdate = UnicodeString(tdateString)
        tdate.setTag(TAG_TDATE)
        return tdate
    }

    fun parseTDate(tdateDI: DataItem): Instant {
        if (tdateDI !is UnicodeString) {
            // TODO think of better exception
            throw RuntimeException()
        }
        val tdate: UnicodeString = tdateDI
        if (!(tdate.hasTag() || tdate.getTag() == TAG_TDATE)) {
            // TODO think of better exception
            throw RuntimeException()
        }
        val instant: Instant
        instant = try {
            DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC)
                .parse(tdate.getString()) { temporal: TemporalAccessor? -> Instant.from(temporal) }
        } catch (e: DateTimeParseException) {
            // TODO think of better exception
            throw RuntimeException(e)
        }
        return instant
    }

    fun visualTDate(instant: Instant?): String {
        return DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC).format(instant)
    }

    /**
     * Returns a Map of UnicodeString to DataItem after testing if the object given is such a thing.
     *
     * @param expectedField the field we're trying to cast to a Map
     * @param obj           the object to cast to `Map<UnicodeString></UnicodeString>, DataItem>`
     * @return the object as `Map<UnicodeString></UnicodeString>, DataItem>`
     * @throws DataItemDecoderException if object is not a DataItem, a Map or particularly a Map with Unicode strings as
     * keys
     */
    @Throws(DataItemDecoderException::class)
    fun toVicalCompatibleMap(
        expectedField: String,
        obj: Any
    ): Map {
        // this method presumes that the object can be cast to the right type if the CBOR majortype is correct
        if (obj !is DataItem) {
            throw DataItemDecoderException("$expectedField structure is not a DataItem")
        }
        val di: DataItem = obj
        if (!(di.getMajorType() == MajorType.MAP && di is Map)) {
            throw DataItemDecoderException("$expectedField structure is not a Map")
        }
        val map = di
        val keys: Collection<DataItem> = map.keys
        for (key in keys) {
            if (!(key.getMajorType() == MajorType.UNICODE_STRING && key is UnicodeString)) {
                throw DataItemDecoderException("$expectedField contains a key that is not a Unicode string")
            }
        }
        return di
    }

    fun oidStringToURN(dotNotationOid: String): String {
        if (!dotNotationOid.matches("[1-9]\\d*(?:[.][1-9]\\d*)*".toRegex())) {
            throw RuntimeException("Input is not a short / dot notation OID")
        }
        return "urn:oid:$dotNotationOid"
    }

    fun urnStringToOid(oidUrn: String): String {
        val matcher = Pattern.compile(
            "urn:oid:([1-9]\\d*(?:[.][1-9]\\d*)*)",
            Pattern.CASE_INSENSITIVE
        ).matcher(oidUrn)
        if (!matcher.matches()) {
            throw RuntimeException("Input is not a OID URN")
        }
        return matcher.group(1)!!
    }

    fun getEntrySet(cborMap: Map): Set<kotlin.collections.Map.Entry<UnicodeString, DataItem>> {
        val ret: MutableSet<kotlin.collections.Map.Entry<UnicodeString, DataItem>> =
            HashSet<kotlin.collections.Map.Entry<UnicodeString, DataItem>>()
        for (keyDI in cborMap.keys) {
            // TODO test type
            val key: UnicodeString = keyDI as UnicodeString
            val value: DataItem = cborMap[key]
            val entry: kotlin.collections.Map.Entry<UnicodeString, DataItem> =
                object : MutableMap.MutableEntry<UnicodeString, DataItem> {
                    override val key: UnicodeString
                        get() = key
                    override val value: DataItem
                        get() = value

                    override fun setValue(dataItem: DataItem): DataItem {
                        throw RuntimeException("The set of Entry represented by the CBOR map is read only")
                    }
                }
            ret.add(entry)
        }
        return ret
    }
}