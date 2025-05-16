package org.multipaz.zkp

import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.toDataItem
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ZkSystemRepositoryTest {
    @Test
    fun testZkSystemRepositoryVerifySuccess() {
        val sessionTranscript = ByteString(Random.nextBytes(50))
        val system = MockZkSystem()
        ZkSystemRepository.add(system)

        val zkSystemSpecs = listOf(
            ZkSystemSpec(
                system = "othername",
                params = mapOf(
                    "specname" to "one".toDataItem(),
                    "intVal" to 1.toDataItem(),
                    "boolVal" to true.toDataItem()
                )
            ),
            ZkSystemSpec(
                system = system.name,
                params = mapOf(
                    "specname" to "two".toDataItem(),
                    "intVal" to 2.toDataItem(),
                    "boolVal" to false.toDataItem()
                )
            )
        )

        // Throws if it fails to find the Zk system we added
        val zkDoc = ZkSystemRepository.generateMdocProof(
            zkSystemSpecs,
            ByteString(Random.nextBytes(40)),
            sessionTranscript
        )

        // Throws if the Zk system cannot be found
        ZkSystemRepository.verifyZkDocumentProof(zkDoc, sessionTranscript)
    }

    @Test
    fun testZkSystemRepositoryVerifyNoMatchingSystem() {
        val sessionTranscript = ByteString(Random.nextBytes(50))
        val system = MockZkSystem()
        ZkSystemRepository.add(system)

        val zkSystemSpecs = listOf(
            ZkSystemSpec(
                system = "othername",
                params = mapOf(
                    "specname" to "one".toDataItem(),
                    "intVal" to 1.toDataItem(),
                    "boolVal" to true.toDataItem()
                )
            ),
            ZkSystemSpec(
                system = system.name,
                params = mapOf(
                    "specname" to "two".toDataItem(),
                    "intVal" to 2.toDataItem(),
                    "boolVal" to false.toDataItem()
                )
            )
        )

        var zkDoc = ZkSystemRepository.generateMdocProof(
            zkSystemSpecs,
            ByteString(Random.nextBytes(40)),
            sessionTranscript
        )

        // Create identical ZK Doc, but with a different Zk Spec that
        // doesn't have a registered Zk System.
        zkDoc = ZkDocument(
            zkSystemSpec = zkSystemSpecs[0],
            docType = zkDoc.docType,
            timestamp = zkDoc.timestamp,
            issuerSignedItems = zkDoc.issuerSignedItems,
            deviceSignedItems = zkDoc.deviceSignedItems,
            msoX5chain = zkDoc.msoX5chain,
            proof = zkDoc.proof
        )

        // Zk System will not be found.
        assertFailsWith<SystemNotFoundException> {
            ZkSystemRepository.verifyZkDocumentProof(zkDoc, sessionTranscript)
        }
    }
}
