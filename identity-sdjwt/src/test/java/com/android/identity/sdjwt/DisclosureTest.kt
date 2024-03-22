package com.android.identity.sdjwt

import com.android.identity.crypto.Algorithm
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class DisclosureTest {

    @Test
    fun test_disclosure_from_disclosure() {
        val disclosure = "WyIyR0xDNDJzS1F2ZUNmR2ZyeU5STjl3IiwgImdpdmVuX25hbWUiLCAiSm9obiJd"
        val d = Disclosure(disclosure, digestAlg = Algorithm.SHA256)

        assertEquals("given_name", d.key)
        assertEquals("John", d.value.jsonPrimitive.content)
        assertEquals("jsu9yVulwQQlhFlM_3JlzMaSFzglhQG0DpfayQwLUK4", d.hash)
    }

    @Test
    fun test_disclosure_from_key_value() {
        val address = buildJsonObject {
            put("street_address",  "123 Main St")
            put("locality", "Anytown")
            put("region",  "Anystate")
            put("country", "US")
        }

        val d = Disclosure("address", address, Algorithm.SHA256, Random(42))
        assertEquals("WyJHc3p2T1VBZ3YyZktQNnZuM0RGSC1TdHhUa1UiLCJhZGRyZXNzIix7InN0cmVldF9hZGRyZXNzIjoiMTIzIE1haW4gU3QiLCJsb2NhbGl0eSI6IkFueXRvd24iLCJyZWdpb24iOiJBbnlzdGF0ZSIsImNvdW50cnkiOiJVUyJ9XQ", d.toString())
        assertEquals("4bqpAAiznBuqENqCmya6tz6kekfjGcm9BZyua8rj-AY", d.hash)
        assertEquals("address", d.key)
        assertEquals(address, d.value)
    }
}
