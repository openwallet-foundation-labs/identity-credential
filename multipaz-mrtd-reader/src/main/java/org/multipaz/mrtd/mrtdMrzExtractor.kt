package org.multipaz.mrtd

import java.util.Locale

/**
 * Extracts [MrtdAccessDataMrz] from text that was captured by OCR of a passport page.
 *
 * It is expected that OCRed text contains many errors. This function does a lot of validation
 * and returns non-null result only if everything checks out.
 *
 * There are three types of MRTD (Machine-Readable Travel Documents): T1 - T3. T1 and T2 are
 * ID cards and T3 are passports. They are described by ICAO in the following standards:
 *
 * - T1: https://www.icao.int/publications/Documents/9303_p5_cons_en.pdf
 * - T2: https://www.icao.int/publications/Documents/9303_p6_cons_en.pdf
 * - T3: https://www.icao.int/publications/Documents/9303_p4_cons_en.pdf
 */
public fun extractMrtdMrzData(text: String): MrtdAccessDataMrz? {
    val lines = fixCommonMistakes(text).lines()

    for (i in 0 until lines.lastIndex) {
        val firstLine = lines[i]
        if (firstLine.length < 10) {
            continue;
        }
        val secondLine = lines[i + 1]
        val firstChar = firstLine[0]
        if (firstChar == 'P') {
            if (validateT3Text(secondLine)) {
                return extractT23Text(secondLine)
            }
        } else if (isIDChar(firstChar)) {
            if (validateT2Text(secondLine)) {
                // ID card (type T2).
                return extractT23Text(secondLine)
            }
            if (secondLine.length == 30) {
                val firstTwoLines = firstLine + secondLine;
                if (validateT1Text(firstTwoLines)) {
                    return extractT1Text(firstTwoLines)
                }
            }
        }
    }
    return null
}

private val td1DocNumberValidator = MrtdMrzChecksumValidator(listOf(MrtdMrzChecksumValidator.Range(5, 13)), 14)
private val td1DateOfBirthValidator = MrtdMrzChecksumValidator(listOf(
    MrtdMrzChecksumValidator.Range(
        30,
        35
    )
), 36)
private val td1ExpirationValidator = MrtdMrzChecksumValidator(listOf(
    MrtdMrzChecksumValidator.Range(
        38,
        43
    )
), 44)
private val td1CompositeValidator = MrtdMrzChecksumValidator(
    listOf(
        MrtdMrzChecksumValidator.Range(5, 29),
        MrtdMrzChecksumValidator.Range(30, 36),
        MrtdMrzChecksumValidator.Range(38, 44),
        MrtdMrzChecksumValidator.Range(48, 58)
    ), 59)

private val td2CompositeValidator = MrtdMrzChecksumValidator(
    listOf(
        MrtdMrzChecksumValidator.Range(0, 9),
        MrtdMrzChecksumValidator.Range(13, 19),
        MrtdMrzChecksumValidator.Range(21, 34)
    ), 35)

private val td23DocNumberValidator = MrtdMrzChecksumValidator(listOf(MrtdMrzChecksumValidator.Range(0, 8)), 9)
private val td23DateOfBirthValidator = MrtdMrzChecksumValidator(listOf(
    MrtdMrzChecksumValidator.Range(
        13,
        18
    )
), 19)
private val td23ExpirationValidator = MrtdMrzChecksumValidator(listOf(
    MrtdMrzChecksumValidator.Range(
        21,
        26
    )
), 27)
private val td3CompositeValidator = MrtdMrzChecksumValidator(
    listOf(
        MrtdMrzChecksumValidator.Range(0, 9),
        MrtdMrzChecksumValidator.Range(13, 19),
        MrtdMrzChecksumValidator.Range(21, 42)
    ), 43)

private fun isIDChar(firstChar: Char): Boolean {
    return firstChar == 'A' || firstChar == 'I' || firstChar == 'C'
}

internal fun validateT1Text(text: String): Boolean {
    if (text.length != 60) {  // first and second lines
        return false
    }
    return isIDChar(text[0]) &&
            td1DocNumberValidator.validate(text) &&
            td1DateOfBirthValidator.validate(text) &&
            td1ExpirationValidator.validate(text) &&
            td1CompositeValidator.validate(text)
}

internal fun validateT2Text(text: String): Boolean {
    if (text.length != 36) {  // last line
        return false
    }
    return td23DocNumberValidator.validate(text) &&
            td23DateOfBirthValidator.validate(text) &&
            td23ExpirationValidator.validate(text) &&
            td2CompositeValidator.validate(text)
}

internal fun validateT3Text(line: String): Boolean {
    if (line.length != 44) {  // last line
        return false
    }
    return td23DocNumberValidator.validate(line) &&
            td23DateOfBirthValidator.validate(line) &&
            td23ExpirationValidator.validate(line) &&
            td3CompositeValidator.validate(line)
}

internal fun fixCommonMistakes(input: String): String {
    return input.replace("«", "<").replace(" ", "").uppercase(Locale.ROOT)
}

private fun extractT1Text(firstTwoLines: String): MrtdAccessDataMrz {
    return MrtdAccessDataMrz(
        firstTwoLines.substring(5, 14),
        firstTwoLines.substring(30, 36),
        firstTwoLines.substring(38, 44)
    )
}

private fun extractT23Text(secondLine: String): MrtdAccessDataMrz {
    return MrtdAccessDataMrz(
        secondLine.substring(0, 9),
        secondLine.substring(13, 19),
        secondLine.substring(21, 27)
    )
}
