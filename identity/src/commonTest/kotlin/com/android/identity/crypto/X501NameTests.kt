package com.android.identity.crypto

import com.android.identity.asn1.ASN1String
import kotlin.test.Test
import kotlin.test.assertEquals

class X501NameTests {
    @Test
    fun testX501NameSimple() {
        assertEquals("CN=Foo", X501Name.fromName("CN=Foo").name)
        assertEquals(mapOf("2.5.4.3" to ASN1String("Foo")), X501Name.fromName("CN=Foo").components)
    }

    @Test
    fun testX501NameComplicated() {
        val str = "CN=David,ST=US-MA,O=Google,OU=Android,C=US"
        assertEquals(str, X501Name.fromName(str).name)
        assertEquals(
            mapOf(
                "2.5.4.6" to ASN1String("US"),
                "2.5.4.11" to ASN1String("Android"),
                "2.5.4.10" to ASN1String("Google"),
                "2.5.4.8" to ASN1String("US-MA"),
                "2.5.4.3" to ASN1String("David"),
            ),
            X501Name.fromName(str).components
        )
        // assertEquals() on a map doesn't check the order... make sure that the parsed keys
        // are the reverse order of their appearance in the string as per RFC 2293 section 2.1
        assertEquals(
            listOf(
                "2.5.4.6",  // C:  US
                "2.5.4.11", // OU: Android
                "2.5.4.10", // O:  Google
                "2.5.4.8",  // ST: US-MA
                "2.5.4.3",  // CN: David
            ),
            X501Name.fromName(str).components.keys.toList()
        )
    }

    @Test
    fun testX501NameFromOID() {
        assertEquals("CN=Foo", X501Name(mapOf("2.5.4.3" to ASN1String("Foo"))).name)
    }

    @Test
    fun testX501NameFromUnknownOID() {
        assertEquals("2.5.4.999=#0c03466f6f", X501Name(mapOf("2.5.4.999" to ASN1String("Foo"))).name)
    }
}