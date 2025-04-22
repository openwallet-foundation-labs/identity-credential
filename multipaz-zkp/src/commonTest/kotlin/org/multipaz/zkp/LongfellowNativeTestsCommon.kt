package org.multipaz.zkp

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format
import kotlinx.io.bytestring.ByteString
import org.multipaz.zkp.mdoc.Proof
import org.multipaz.zkp.mdoc.longfellow.LongFellowNatives
import org.multipaz.zkp.mdoc.longfellow.VerifierCodeEnum
import kotlin.time.measureTime

object LongfellowNativeTestsCommon {
    internal fun runFullVerification(): VerifierCodeEnum{
        val zkSpec = LongFellowNatives.getZkSpec(1)
        val circuit = LongFellowNatives.generateCircuit(zkSpec)

        val formattedDate = MdocTestDataProvider.getProofGenerationDate().format(LocalDateTime.Formats.ISO)

        var proof: Proof
        val proofTimeTaken = measureTime {
            val proofBytes = LongFellowNatives.runMdocProver(
                circuit,
                circuit.size,
                MdocTestDataProvider.getMdocBytes(),
                MdocTestDataProvider.getMdocBytes().size,
                MdocTestDataProvider.x(),
                MdocTestDataProvider.y(),
                MdocTestDataProvider.getTranscript(),
                MdocTestDataProvider.getTranscript().size,
                formattedDate,
                zkSpec,
                MdocTestDataProvider.getAttributes()
            )

            proof = Proof(formattedDate, ByteString(proofBytes))
        }

        println("--> Time taken to generate proof: $proofTimeTaken")

        val docType = "org.iso.18013.5.1.mDL"
        var verifierCodeEnum: VerifierCodeEnum
        val verifyTimeTaken = measureTime {
            val verifierCode = LongFellowNatives.runMdocVerifier(
                circuit,
                circuit.size,
                MdocTestDataProvider.x(),
                MdocTestDataProvider.y(),
                MdocTestDataProvider.getTranscript(),
                MdocTestDataProvider.getTranscript().size,
                proof.timestamp,
                proof.proof,
                proof.proof.size,
                docType,
                zkSpec,
                MdocTestDataProvider.getAttributes()
            )

            verifierCodeEnum = VerifierCodeEnum.fromInt(verifierCode)!!
        }

        println("--> Time taken to verify proof: $verifyTimeTaken")
        return verifierCodeEnum
    }
}
