package org.multipaz.mdoc.zkp

import kotlinx.io.bytestring.ByteString
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
                id = "othername_one",
                system = "othername",
                params = mapOf(
                    "specname" to "one",
                    "intVal" to 1,
                    "boolVal" to true
                )
            ),
            ZkSystemSpec(
                id = "${system.name}_one",
                system = system.name,
                params = mapOf(
                    "specname" to "two",
                    "intVal" to 2,
                    "boolVal" to false
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
                id = "othername_one",
                system = "othername",
                params = mapOf(
                    "specname" to "one",
                    "intVal" to 1,
                    "boolVal" to true
                )
            ),
            ZkSystemSpec(
                id = "${system.name}_one",
                system = system.name,
                params = mapOf(
                    "specname" to "two",
                    "intVal" to 2,
                    "boolVal" to false
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
        zkDoc = ZkDocument.create(
            zkSystemSpec = zkSystemSpecs[0],
            docType = zkDoc.zkDocumentData.docType,
            timestamp = zkDoc.zkDocumentData.timestamp,
            issuerSignedItems = zkDoc.zkDocumentData.issuerSignedItems,
            msoX5chain = zkDoc.zkDocumentData.msoX5chain,
            proof = zkDoc.proof
        )

        // Zk System will not be found.
        assertFailsWith<SystemNotFoundException> {
            ZkSystemRepository.verifyZkDocumentProof(zkDoc, sessionTranscript)
        }
    }
}