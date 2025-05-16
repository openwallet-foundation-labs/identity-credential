package org.multipaz.mdoc.zkp.longfellow

import kotlinx.io.bytestring.ByteString
import kotlin.jvm.JvmStatic
import org.multipaz.mdoc.zkp.longfellow.util.NativeLoader

internal object LongfellowNatives {
    init {
        NativeLoader.loadLibrary("zkp")
    }

    @JvmStatic
    external fun getZkSpec(numAttributes: Int): LongfellowZkSystemSpec

    fun generateCircuit(jzkSpec: LongfellowZkSystemSpec): ByteString {
        val circuit = generateCircuitNative(jzkSpec)
        return ByteString(circuit)
    }

    @JvmStatic
    private external fun generateCircuitNative(jzkSpec: LongfellowZkSystemSpec): ByteArray

    fun runMdocProver(
        circuit: ByteString,
        circuitSize: Int,
        mdoc: ByteString,
        mdocSize: Int,
        pkx: String,
        pky: String,
        transcript: ByteString,
        transcriptSize: Int,
        now: String,
        zkSpec: LongfellowZkSystemSpec,
        statements: List<NativeAttribute>
    ): ByteArray {
        return runMdocProverNative(
            circuit.toByteArray(),
            circuitSize,
            mdoc.toByteArray(),
            mdocSize,
            pkx,
            pky,
            transcript.toByteArray(),
            transcriptSize,
            now,
            zkSpec,
            statements.toTypedArray()
        )
    }

    @Throws(ProofGenerationException::class)
    @JvmStatic
    private external fun runMdocProverNative(
        circuit: ByteArray,
        circuitSize: Int,
        mdoc: ByteArray,
        mdocSize: Int,
        pkx: String,
        pky: String,
        transcript: ByteArray,
        transcriptSize: Int,
        now: String,
        zkSpec: LongfellowZkSystemSpec,
        statements: Array<NativeAttribute>
    ): ByteArray

    fun runMdocVerifier(
        circuit: ByteString,
        circuitSize: Int,
        pkx: String,
        pky: String,
        transcript: ByteString,
        transcriptSize: Int,
        now: String,
        proof: ByteString,
        proofSize: Int,
        docType: String,
        zkSpec: LongfellowZkSystemSpec,
        statements: Array<NativeAttribute>
    ): Int {
        return runMdocVerifierNative(
            circuit.toByteArray(),
            circuitSize,
            pkx,
            pky,
            transcript.toByteArray(),
            transcriptSize,
            now,
            proof.toByteArray(),
            proofSize,
            docType,
            zkSpec,
            statements
        )
    }

    @JvmStatic
    private external fun runMdocVerifierNative(
        circuit: ByteArray,
        circuitSize: Int,
        pkx: String,
        pky: String,
        transcript: ByteArray,
        transcriptSize: Int,
        now: String,
        proof: ByteArray,
        proofSize: Int,
        docType: String,
        zkSpec: LongfellowZkSystemSpec,
        statements: Array<NativeAttribute>
    ): Int
}
