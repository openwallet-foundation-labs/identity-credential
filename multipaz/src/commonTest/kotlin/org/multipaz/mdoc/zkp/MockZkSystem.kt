package org.multipaz.mdoc.zkp

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
                id = name + "_one",
                system = name,
                params = mapOf(
                    "specname" to "one",
                    "intVal" to 1,
                    "boolVal" to true
                )
            ),
            ZkSystemSpec(
                id = name + "_two",
                system = name,
                params = mapOf(
                    "specname" to "two",
                    "intVal" to 2,
                    "boolVal" to false
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