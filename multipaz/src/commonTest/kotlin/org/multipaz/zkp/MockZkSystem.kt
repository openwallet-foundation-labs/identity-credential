package org.multipaz.zkp

import kotlinx.datetime.Clock
import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.toDataItem
import kotlin.random.Random

class MockZkSystem: ZkSystem {
    override val name: String
        get() = "MockZkSystem"

    override fun getSystemSpecs(): List<ZkSystemSpec> {
        return listOf(
            ZkSystemSpec(
                system = name,
                params = mapOf(
                    "specname" to "one".toDataItem(),
                    "intVal" to 1.toDataItem(),
                    "boolVal" to true.toDataItem()
                )
            ),
            ZkSystemSpec(
                system = name,
                params = mapOf(
                    "specname" to "two".toDataItem(),
                    "intVal" to 2.toDataItem(),
                    "boolVal" to false.toDataItem()
                )
            )
        )
    }

    override fun generateProof(
        zkSystemSpec: ZkSystemSpec,
        document: ByteString,
        encodedSessionTranscript: ByteString
    ): ZkDocument {
        return ZkDocument.create(
            zkSystemSpec = zkSystemSpec,
            docType = "doctype",
            timestamp = Clock.System.now(),
            issuerSignedItems = listOf<DataItem>(),
            msoX5chain = null,
            proof = ByteString(Random.nextBytes(40))
        )
    }

    override fun verifyProof(zkDocument: ZkDocument, encodedSessionTranscript: ByteString) {
        // Do nothing. Verify proof will throw on fail.
    }

    override fun getMatchingSystemSpec(zkSystemSpecs: List<ZkSystemSpec>, document: ByteString): ZkSystemSpec? {
        return zkSystemSpecs.firstOrNull() {
            it.system == name
        }
    }
}