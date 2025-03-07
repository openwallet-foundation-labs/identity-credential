package org.multipaz.mrtd

import org.junit.Assert
import org.junit.Test

class MrtdMrzExtractorTest {

    @Test
    fun testT1TextValidation_succeeds() {
        Assert.assertTrue(
            validateT1Text(
                "I<UTOD231458907<<<<<<<<<<<<<<<7408122F1204159UTO<<<<<<<<<<<6"
            )
        )
    }

    @Test
    fun testT1TextValidation_fails() {
        Assert.assertFalse(
            validateT1Text(
                "I<UTOD231458907<<<<<<<<<<<<<<<7408121F1204159UTO<<<<<<<<<<<6"
            )
        )
        Assert.assertFalse(
            validateT1Text(
                "I<UTOD231458907<<<<<<<<<<<<<<<7408122F1204159UTO<<<<<<<<<<<0"
            )
        )
    }

    @Test
    fun testT2TextValidation_succeeds() {
        Assert.assertTrue(validateT2Text("D231458907UTO7408122F1204159<<<<<<<6"))
    }

    @Test
    fun testT2TextValidation_fails() {
        Assert.assertFalse(validateT2Text("D231758907UTO7408122F1204159<<<<<<<6"))
        Assert.assertFalse(validateT2Text("D231458907UTO7408122F1204159<<<<<<<5"))
    }

    @Test
    fun testT3TextValidation_succeeds() {
        Assert.assertTrue(validateT3Text("L898902C36UTO7408122F1204159ZE184226B<<<<<10"))
    }

    @Test
    fun testT3TextValidation_fails() {
        Assert.assertFalse(validateT3Text("L899902C36UTO7408122F1204159ZE184226B<<<<<10"))
        Assert.assertFalse(validateT3Text("L898902C36UTO7408122F1204159ZE184226B<<<<<11"))
    }

    @Test
    fun testExtractMrz_T1() {
        val data = extractMrtdMrzData(
            "Foobar12567 some other text on the card\n" +
                    "I<UTOD231458907<<<<<<<<<<<<<<<\n" +
                    "7408122F1204159UTO<<<<<<<<<<<6\n" +
                    "ERIKSSON<<ANNA<MARIA<<<<<<<<<<\n" +
                    "Some random garbage text 234"
        )
        Assert.assertNotNull(data)
        Assert.assertEquals("D23145890", data!!.documentNumber)
        Assert.assertEquals("740812", data.dateOfBirth)
        Assert.assertEquals("120415", data.dateOfExpiration)
    }

    @Test
    fun testExtractMrz_T2() {
        val data = extractMrtdMrzData(
            "Foobar12567 some other text on the card\n" +
                    "P<UToERiKSsON<<ANNA< MARIA <<<<<<<<<<<<<<<<<<<\r\n" +
                    "L898902c36UTO7408122F1204159ZE184226B<<<<<10\r" +
                    "Some random garbage text 234"
        )
        Assert.assertNotNull(data)
        Assert.assertEquals("L898902C3", data!!.documentNumber)
        Assert.assertEquals("740812", data.dateOfBirth)
        Assert.assertEquals("120415", data.dateOfExpiration)
    }

    @Test
    fun testExtractMrz_T3() {
        val data = extractMrtdMrzData("UTOPIA\n" +
                "Passport/ Passeport Type/ Type " +
                "Country code/ Code du pays Passport No./ N° de passeport\n" +
                "P UTO L898902C3\n" +
                "Surname/ Nom ERIKSSON\n" +
                "Given names/ Prénoms ANNA MARIA\n" +
                "Nationality/ Nationalité UTOPIAN\n" +
                "Date of Birth/ Date de naissance Personal No./ N° personnel\n" +
                "12 AUG/AOÛT 1974 Z E 184226 B\n" +
                "Sex/ Sexe Place of birth/ Lieu de naissance\n" +
                "F ZENITH\n" +
                "Date of issue/ Date de délivrance Authority/ Autorité\n" +
                "16 APR/AVR 2007 PASSPORT OFFICE\n" +
                "Date of expiry/ Date d’expiration Holder’s signature/ Signature du titulaire\n" +
                "15 APR/AVR 2012\n" +
                "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<\n" +
                "L898902C36UTO7408122F1204159ZE184226B<<<<<10\n"
        )
        Assert.assertNotNull(data)
        Assert.assertEquals("L898902C3", data!!.documentNumber)
        Assert.assertEquals("740812", data.dateOfBirth)
        Assert.assertEquals("120415", data.dateOfExpiration)
    }
}