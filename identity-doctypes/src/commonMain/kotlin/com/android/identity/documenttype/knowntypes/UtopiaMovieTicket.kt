package com.android.identity.documenttype.knowntypes

import com.android.identity.cbor.toDataItem
import com.android.identity.cbor.toDataItemFullDate
import com.android.identity.documenttype.DocumentAttributeType
import com.android.identity.documenttype.DocumentType
import com.android.identity.documenttype.Icon
import kotlinx.datetime.LocalDate

/**
 * Object containing the metadata of the Utopia Movie Ticket Document Type.
 */
object UtopiaMovieTicket {
    const val VCTYPE = "http://utopia.example.com/vct/movieticket"

    /**
     * Build the Movie Ticket Document Type.
     */
    fun getDocumentType(): DocumentType {
        return DocumentType.Builder("Movie Ticket")
            .addVcDocumentType(VCTYPE)
            .addVcAttribute(
                DocumentAttributeType.Number,
                "ticket_number",
                "Ticket Number",
                "Ticket identification/reference number issued at the purchase time.",
                Icon.NUMBERS,
                SampleData.TICKET_NUMBER.toDataItem()
            )
            .addVcAttribute(
                DocumentAttributeType.String,
                "cinema_id",
                "Cinema Theater",
                "Cinema theater name, and/or address/location of the admission.",
                Icon.PLACE,
                SampleData.CINEMA_ID.toDataItem()
            )
            .addVcAttribute(
                DocumentAttributeType.String,
                "movie_id",
                "Movie Title",
                "Movie name, title, and any other show identification information.",
                Icon.TODAY,
                SampleData.MOVIE_ID.toDataItem()
            )
            .addVcAttribute(
                DocumentAttributeType.String,
                "movie_rating",
                "Age Rating Code",
                "Movie rating code for age restrictions.",
                Icon.TODAY,
                SampleData.MOVIE_RATING.toDataItem()
            )
            .addVcAttribute(
                DocumentAttributeType.DateTime,
                "movie_date",
                "Date",
                "Year-month-day of the admission purchased.",
                Icon.DATE_RANGE,
                LocalDate.parse(SampleData.MOVIE_DATE).toDataItemFullDate()
            )
            .addVcAttribute(
                DocumentAttributeType.String,
                "movie_time",
                "Time",
                "Hour and minute of the show start (theater clock).",
                Icon.CALENDAR_CLOCK,
               SampleData.MOVIE_TIME.toDataItem()
            )
            .addVcAttribute(
                DocumentAttributeType.String,
                "theater_id",
                "Theater",
                "Name or number of the theater in a multi-theater cinema building.",
                Icon.TODAY,
                SampleData.THEATRE_NAME.toDataItem()
            )
            .addVcAttribute(
                DocumentAttributeType.String,
                "seat_id",
                "Seat",
                "Seat number or code (e.g. row/seat).",
                Icon.NUMBERS,
                SampleData.THEATRE_SEAT.toDataItem()
            )
            .addVcAttribute(
                DocumentAttributeType.Boolean,
                "parking_option",
                "Parking",
                "Flag if car parking is prepaid with the ticket purchase.",
                Icon.DIRECTIONS_CAR,
                SampleData.CINEMA_PARKING.toDataItem()
            )
            .addSampleRequest(
                id = "is_parking_prepaid",
                displayName = "Prepaid Parking",
                vcClaims = listOf("parking_option")
            )
            .addSampleRequest(
                id = "ticket_id",
                displayName = "Ticket Number",
                vcClaims = listOf(
                    "ticket_number",
                )
            )
            .addSampleRequest(
                id = "full",
                displayName = "All Data Elements",
                vcClaims = listOf()
            )
            .build()
    }
}
