package org.multipaz.zkp.mdoc.longfellow

import kotlinx.io.bytestring.ByteString
import kotlin.jvm.JvmStatic
import org.multipaz.zkp.util.NativeLoader
import org.multipaz.zkp.ZkSpec
import org.multipaz.zkp.ProofGenerationException
import org.multipaz.zkp.mdoc.Attribute
import org.multipaz.zkp.mdoc.NativeAttribute
import org.multipaz.zkp.util.toNativeArray

internal object LongFellowNatives {
    init {
        NativeLoader.loadLibrary("zkp")
    }

    @JvmStatic
    external fun getZkSpec(numAttributes: Int): ZkSpec

    fun generateCircuit(jzkSpec: ZkSpec): ByteString {
        val circuit = generateCircuitNative(jzkSpec)
        return ByteString(circuit)
    }

    @JvmStatic
    private external fun generateCircuitNative(jzkSpec: ZkSpec): ByteArray

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
        zkSpec: ZkSpec,
        statements: Array<Attribute>
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
            statements.toNativeArray()
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
        zkSpec: ZkSpec,
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
        zkSpec: ZkSpec,
        statements: Array<Attribute>
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
            statements.toNativeArray()
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
        zkSpec: ZkSpec,
        statements: Array<NativeAttribute>
    ): Int
}
