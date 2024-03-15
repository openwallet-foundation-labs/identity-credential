package com.android.identity.issuance.evidence

import org.junit.Assert
import org.junit.Test


class EvidenceRequestTest {
    @Test
    fun cborSerialization_IcaoPassiveAuthentication() {
        val orig = EvidenceRequestIcaoPassiveAuthentication(listOf(3, 7, 1))
        val copy = EvidenceRequest.fromCbor(orig.toCbor())
        Assert.assertEquals(orig, copy)
    }

    @Test
    fun cborSerialization_IcaoNfcTunnel() {
        val orig = EvidenceRequestIcaoNfcTunnel(
            EvidenceRequestIcaoNfcTunnelType.READING, 15, byteArrayOf(3, 7, 1))
        val copy = EvidenceRequest.fromCbor(orig.toCbor())
        Assert.assertEquals(orig, copy)
    }

    @Test
    fun cborSerialization_Message_null_field() {
        val orig = EvidenceRequestMessage(
            "Lorem ipsum",
            mapOf("three" to byteArrayOf(3), "seven" to byteArrayOf(7), "ace" to byteArrayOf(1)),
            "foobar",
            null)
        val copy = EvidenceRequest.fromCbor(orig.toCbor())
        Assert.assertEquals(orig, copy)
    }

    @Test
    fun cborSerialization_Message_nonnull_field() {
        val orig = EvidenceRequestMessage(
            "Lorem ipsum",
            mapOf("three" to byteArrayOf(3), "seven" to byteArrayOf(7), "ace" to byteArrayOf(1)),
            "foo",
            "bar")
        val copy = EvidenceRequest.fromCbor(orig.toCbor())
        Assert.assertEquals(orig, copy)
    }

    @Test
    fun cborSerialization_QuestionMultipleChoice() {
        val orig = EvidenceRequestQuestionMultipleChoice(
            "Lorem ipsum",
            mapOf("three" to "3", "seven" to "7", "ace" to "1"),
            "foobar")
        val copy = EvidenceRequest.fromCbor(orig.toCbor())
        Assert.assertEquals(orig, copy)
    }

    @Test
    fun cborSerialization_QuestionString() {
        val orig = EvidenceRequestQuestionString(
            "The meaning of life, the universe, and everything?",
            "42",
            "continue")
        val copy = EvidenceRequest.fromCbor(orig.toCbor())
        Assert.assertEquals(orig, copy)
    }
}