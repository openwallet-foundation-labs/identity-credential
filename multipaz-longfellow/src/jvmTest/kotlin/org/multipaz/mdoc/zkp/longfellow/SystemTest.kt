package org.multipaz.mdoc.zkp.longfellow

import org.junit.Test
import org.multipaz.mdoc.zkp.ZkSystemRepository
import org.multipaz.mdoc.zkp.ZkSystemSpec
import kotlinx.datetime.toInstant
import kotlinx.datetime.TimeZone
import kotlinx.io.bytestring.ByteString
import org.multipaz.mdoc.response.DeviceResponseGenerator
import org.multipaz.mdoc.response.DeviceResponseParser

class SystemTest {
    @Test
    fun testProofFullFlow_success() {
        val system = TestableLongfellowZkSystem()
        system.testTime = MdocTestDataProvider.getProofGenerationDate().toInstant(TimeZone.UTC)
        val zkRepository = ZkSystemRepository()
        zkRepository.add(system)

        val spec = ZkSystemSpec(
            id = "one_${system.name}",
            system = system.name,
        ).apply {
            addParam(
                "circuitHash",
                "e6074e6f3838708f4c11321ab2759fc8e1bd05a047152b8a67e99507d569304c"
            )
            addParam("numAttributes", 1)
            addParam("version", 1)
        }

        val sessionTranscript = MdocTestDataProvider.getTranscript()
        val docBytes = MdocTestDataProvider.getMdocBytes().toByteArray()

        val zkDoc = zkRepository.lookup(system.name)?.generateProof(spec, ByteString(docBytes), sessionTranscript)

        val responseGenerator = DeviceResponseGenerator(0)
        responseGenerator.addZkDocument(zkDoc!!)
        val responseBytes = responseGenerator.generate()
        val responseParser = DeviceResponseParser(responseBytes, sessionTranscript.toByteArray())
        val response = responseParser.parse()

        val zkDocResponse = response.zkDocuments[0]

        zkRepository.lookup(system.name)?.verifyProof(zkDocResponse, spec, sessionTranscript)
    }
}