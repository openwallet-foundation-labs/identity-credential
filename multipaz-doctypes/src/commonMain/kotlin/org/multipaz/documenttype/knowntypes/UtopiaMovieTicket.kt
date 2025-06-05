package org.multipaz.documenttype.knowntypes

import org.multipaz.documenttype.DocumentAttributeType
import org.multipaz.documenttype.DocumentType
import org.multipaz.documenttype.Icon
import kotlinx.serialization.json.JsonPrimitive

/**
 * Object containing the metadata of the Utopia Movie Ticket Document Type.
 */
object UtopiaMovieTicket {
    const val MOVIE_TICKET_VCT = "https://utopia.example.com/vct/movieticket"

    /**
     * Build the Movie Ticket Document Type.
     */
    fun getDocumentType(): DocumentType {
        return DocumentType.Builder("Movie Ticket")
            .addJsonDocumentType(type = MOVIE_TICKET_VCT, keyBound = false)
            .addJsonAttribute(
                DocumentAttributeType.Number,
                "ticket_number",
                "Ticket Number",
                "Ticket identification/reference number issued at the purchase time.",
                Icon.NUMBERS,
                JsonPrimitive(SampleData.TICKET_NUMBER)
            )
            .addJsonAttribute(
                DocumentAttributeType.String,
                "cinema_id",
                "Cinema Theater",
                "Cinema theater name, and/or address/location of the admission.",
                Icon.PLACE,
                JsonPrimitive(SampleData.CINEMA_ID)
            )
            .addJsonAttribute(
                DocumentAttributeType.String,
                "movie_id",
                "Movie Title",
                "Movie name, title, and any other show identification information.",
                Icon.TODAY,
                JsonPrimitive(SampleData.MOVIE_ID)
            )
            .addJsonAttribute(
                DocumentAttributeType.String,
                "movie_rating",
                "Age Rating Code",
                "Movie rating code for age restrictions.",
                Icon.TODAY,
                JsonPrimitive(SampleData.MOVIE_RATING)
            )
            .addJsonAttribute(
                DocumentAttributeType.Date,
                "movie_date",
                "Date",
                "Year-month-day of the admission purchased.",
                Icon.DATE_RANGE,
                JsonPrimitive(SampleData.MOVIE_DATE)
            )
            .addJsonAttribute(
                DocumentAttributeType.String,
                "movie_time",
                "Time",
                "Hour and minute of the show start (theater clock).",
                Icon.CALENDAR_CLOCK,
                JsonPrimitive(SampleData.MOVIE_TIME)
            )
            .addJsonAttribute(
                DocumentAttributeType.String,
                "theater_id",
                "Theater",
                "Name or number of the theater in a multi-theater cinema building.",
                Icon.TODAY,
                JsonPrimitive(SampleData.THEATRE_NAME)
            )
            .addJsonAttribute(
                DocumentAttributeType.String,
                "seat_id",
                "Seat",
                "Seat number or code (e.g. row/seat).",
                Icon.NUMBERS,
                JsonPrimitive(SampleData.THEATRE_SEAT)
            )
            .addJsonAttribute(
                DocumentAttributeType.Boolean,
                "parking_option",
                "Parking",
                "Flag if car parking is prepaid with the ticket purchase.",
                Icon.DIRECTIONS_CAR,
                JsonPrimitive(SampleData.CINEMA_PARKING)
            )
            .addSampleRequest(
                id = "is_parking_prepaid",
                displayName = "Prepaid Parking",
                jsonClaims = listOf("parking_option")
            )
            .addSampleRequest(
                id = "ticket_id",
                displayName = "Ticket Number",
                jsonClaims = listOf(
                    "ticket_number",
                )
            )
            .addSampleRequest(
                id = "full",
                displayName = "All Data Elements",
                jsonClaims = listOf()
            )
            .build()
    }
}
