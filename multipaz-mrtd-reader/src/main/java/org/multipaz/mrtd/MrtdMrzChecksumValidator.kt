package org.multipaz.mrtd

private val checksumWeights = arrayOf(7, 3, 1)

/**
 * Validates that a data field in Machine Readable Zone (MRZ) was scanned and OCRed correctly from
 * an ICAO Machine-readable Travel Document (MRTD).
 *
 * See [ICAO Spec](https://www.icao.int/publications/Documents/9303_p3_cons_en.pdf) for details.
 */
class MrtdMrzChecksumValidator(
    private val checksumRanges: List<Range>,
    private val checksumDigitIndex: Int) {

    data class Range(val start: Int, val end: Int)

    fun validate(line: String): Boolean {
        val checkChar = line[checksumDigitIndex]
        if (checkChar < '0' || checkChar > '9') {
            return false
        }
        var checksum = 0
        var i = 0
        for (range in checksumRanges) {
            for (index in range.start..range.end) {
                val value = when (val character = line[index]) {
                    in '0'..'9' -> character.code - '0'.code
                    in 'A'..'Z' -> character.code - 'A'.code + 10
                    '<' -> 0
                    else -> return false
                }
                checksum += value * checksumWeights[i % checksumWeights.size]
                i++
            }
        }
        return checkChar.code - '0'.code == checksum % 10
    }
}