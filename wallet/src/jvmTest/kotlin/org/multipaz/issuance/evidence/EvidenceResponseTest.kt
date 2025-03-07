package org.multipaz.issuance.evidence

import kotlinx.io.bytestring.ByteString
import org.junit.Assert
import org.junit.Test
import java.nio.charset.Charset

class EvidenceResponseTest {
    @Test
    fun cborSerialization_IcaoPassiveAuthentication() {
        val orig = EvidenceResponseIcaoPassiveAuthentication(
            mapOf(
                3 to ByteString("three".toByteArray(Charset.forName("UTF-8"))),
                7 to ByteString("seven".toByteArray(Charset.forName("UTF-8"))),
                1 to ByteString("ace".toByteArray(Charset.forName("UTF-8")))
            ),
            ByteString(byteArrayOf(3, 7, 1)))
        val copy = EvidenceResponse.fromCbor(orig.toCbor())
        Assert.assertEquals(orig, copy)
    }

    @Test
    fun cborSerialization_IcaoNfcTunnel() {
        val orig = EvidenceResponseIcaoNfcTunnel(ByteString(byteArrayOf(3, 7, 1)))
        val copy = EvidenceResponse.fromCbor(orig.toCbor())
        Assert.assertEquals(orig, copy)
    }

    @Test
    fun cborSerialization_IcaoNfcTunnelResult() {
        val orig = EvidenceResponseIcaoNfcTunnelResult(
            EvidenceResponseIcaoNfcTunnelResult.AdvancedAuthenticationType.ACTIVE,
            mapOf(
                3 to ByteString("three".toByteArray(Charset.forName("UTF-8"))),
                7 to ByteString("seven".toByteArray(Charset.forName("UTF-8"))),
                1 to ByteString("ace".toByteArray(Charset.forName("UTF-8")))
            ),
            ByteString(byteArrayOf(3, 7, 1))
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