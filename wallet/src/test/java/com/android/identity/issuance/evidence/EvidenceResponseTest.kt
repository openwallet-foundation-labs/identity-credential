package com.android.identity.issuance.evidence

import org.junit.Assert
import org.junit.Test
import java.nio.charset.Charset

class EvidenceResponseTest {
    @Test
    fun cborSerialization_IcaoPassiveAuthentication() {
        val orig =
            EvidenceResponseIcaoPassiveAuthentication(
                mapOf(
                    3 to "three".toByteArray(Charset.forName("UTF-8")),
                    7 to "seven".toByteArray(Charset.forName("UTF-8")),
                    1 to "ace".toByteArray(Charset.forName("UTF-8")),
                ),
                byteArrayOf(3, 7, 1),
            )
        val copy = EvidenceResponse.fromCbor(orig.toCbor())
        Assert.assertEquals(orig, copy)
    }

    @Test
    fun cborSerialization_IcaoNfcTunnel() {
        val orig = EvidenceResponseIcaoNfcTunnel(byteArrayOf(3, 7, 1))
        val copy = EvidenceResponse.fromCbor(orig.toCbor())
        Assert.assertEquals(orig, copy)
    }

    @Test
    fun cborSerialization_IcaoNfcTunnelResult() {
        val orig =
            EvidenceResponseIcaoNfcTunnelResult(
                EvidenceResponseIcaoNfcTunnelResult.AdvancedAuthenticationType.ACTIVE,
                mapOf(
                    3 to "three".toByteArray(Charset.forName("UTF-8")),
                    7 to "seven".toByteArray(Charset.forName("UTF-8")),
                    1 to "ace".toByteArray(Charset.forName("UTF-8")),
                ),
                byteArrayOf(3, 7, 1),
            )
        val copy = EvidenceResponse.fromCbor(orig.toCbor())
        Assert.assertEquals(orig, copy)
    }

    @Test
    fun cborSerialization_Message() {
        val orig = EvidenceResponseMessage(true)
        val copy = EvidenceResponse.fromCbor(orig.toCbor())
        Assert.assertEquals(orig, copy)
    }

    @Test
    fun cborSerialization_QuestionMultipleChoice() {
        val orig = EvidenceResponseQuestionMultipleChoice("foo")
        val copy = EvidenceResponse.fromCbor(orig.toCbor())
        Assert.assertEquals(orig, copy)
    }

    @Test
    fun cborSerialization_QuestionString() {
        val orig = EvidenceResponseQuestionString("bar")
        val copy = EvidenceResponse.fromCbor(orig.toCbor())
        Assert.assertEquals(orig, copy)
    }
}
