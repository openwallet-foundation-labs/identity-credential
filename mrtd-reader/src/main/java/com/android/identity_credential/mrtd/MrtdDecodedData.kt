package com.android.identity_credential.mrtd

/**
 * Data read from the passport or ID card.
 *
 * This data is typically produced from [MrtdNfcData] using [MrtdNfcDataDecoder].
 */
data class MrtdDecodedData(
    val firstName: String,
    val firstNameComponents: List<String>,
    val lastName: String,
    val issuingState: String,
    val nationality: String,
    val gender: String,
    val documentCode: String,
    val documentNumber: String,
    val dateOfBirth: String, // YYMMDD
    val dateOfExpiry: String, // YYMMDD
    val personalNumber: String?,
    val optionalData1: String?,
    val optionalData2: String?,
    val photoMediaType: String?,
    val photo: ByteArray?, // JPEG or JPEG2000 bytes
    val signatureMediaType: String?,
    val signature: ByteArray?, // JPEG or JPEG2000 bytes
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MrtdDecodedData

        if (firstName != other.firstName) return false
        if (firstNameComponents != other.firstNameComponents) return false
        if (lastName != other.lastName) return false
        if (issuingState != other.issuingState) return false
        if (nationality != other.nationality) return false
        if (gender != other.gender) return false
        if (documentCode != other.documentCode) return false
        if (documentNumber != other.documentNumber) return false
        if (dateOfBirth != other.dateOfBirth) return false
        if (dateOfExpiry != other.dateOfExpiry) return false
        if (personalNumber != other.personalNumber) return false
        if (optionalData1 != other.optionalData1) return false
        if (optionalData2 != other.optionalData2) return false
        if (photoMediaType != other.photoMediaType) return false
        if (photo != null) {
            if (other.photo == null) return false
            if (!photo.contentEquals(other.photo)) return false
        } else if (other.photo != null) {
            return false
        }
        if (signatureMediaType != other.signatureMediaType) return false
        if (signature != null) {
            if (other.signature == null) return false
            if (!signature.contentEquals(other.signature)) return false
        } else if (other.signature != null) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = firstName.hashCode()
        result = 31 * result + firstNameComponents.hashCode()
        result = 31 * result + lastName.hashCode()
        result = 31 * result + issuingState.hashCode()
        result = 31 * result + nationality.hashCode()
        result = 31 * result + gender.hashCode()
        result = 31 * result + documentCode.hashCode()
        result = 31 * result + documentNumber.hashCode()
        result = 31 * result + dateOfBirth.hashCode()
        result = 31 * result + dateOfExpiry.hashCode()
        result = 31 * result + (personalNumber?.hashCode() ?: 0)
        result = 31 * result + (optionalData1?.hashCode() ?: 0)
        result = 31 * result + (optionalData2?.hashCode() ?: 0)
        result = 31 * result + (photoMediaType?.hashCode() ?: 0)
        result = 31 * result + (photo?.contentHashCode() ?: 0)
        result = 31 * result + (signatureMediaType?.hashCode() ?: 0)
        result = 31 * result + (signature?.contentHashCode() ?: 0)
        return result
    }
}
