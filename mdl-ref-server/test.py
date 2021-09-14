#!/usr/bin/python3

import cbor
import hashlib
import tornado.testing
import unittest
from cryptography.hazmat.backends import default_backend
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec

import server
import util


class coseTest(unittest.TestCase):

    def test_cose_key(self):
        private_key = ec.generate_private_key(ec.SECP256R1(), default_backend())
        public_key = private_key.public_key()

        cose_key = util.to_cose_key(public_key)
        extracted_public_key = util.from_cose_key(cose_key)

        # Can't just compare the objects, we have to get the raw numbers/bytes
        # and compare them
        public_key_bytes = public_key.public_bytes(
            serialization.Encoding.DER,
            serialization.PublicFormat.SubjectPublicKeyInfo)
        extracted_public_key_bytes = extracted_public_key.public_bytes(
            serialization.Encoding.DER,
            serialization.PublicFormat.SubjectPublicKeyInfo)
        self.assertEqual(public_key_bytes, extracted_public_key_bytes)

    def test_cose_sign(self):
        private_key = ec.generate_private_key(ec.SECP256R1(), default_backend())
        public_key = private_key.public_key()
        data = b"some bytes to sign"
        sig = util.cose_sign1_sign(private_key, data)

        self.assertEqual(data, util.cose_sign1_get_data(sig))

        self.assertTrue(util.cose_sign1_verify(public_key, sig, data))


class certTest(unittest.TestCase):

    def test_credential_key_cert(self):
        credential_key_private = ec.generate_private_key(ec.SECP256R1(), default_backend())
        credential_key_public = credential_key_private.public_key()

        cert_chain = util.generate_x509_cert_for_credential_key(credential_key_private)

        # Can't just compare the objects, we have to get the raw numbers/bytes
        # and compare them
        extracted_public_key = util.cert_chain_get_public_key(cert_chain)
        credential_key_public_bytes = credential_key_public.public_bytes(
            serialization.Encoding.DER,
            serialization.PublicFormat.SubjectPublicKeyInfo)
        extracted_public_key_bytes = extracted_public_key.public_bytes(
            serialization.Encoding.DER,
            serialization.PublicFormat.SubjectPublicKeyInfo)
        self.assertEqual(credential_key_public_bytes, extracted_public_key_bytes)

        # TODO: check Android Attestation Extension stuff

    def test_auth_key_cert(self):
        credential_key_private = ec.generate_private_key(ec.SECP256R1(), default_backend())
        credential_key_public = credential_key_private.public_key()

        auth_key_private = ec.generate_private_key(ec.SECP256R1(), default_backend())
        auth_key_public = auth_key_private.public_key()

        pop_sha256 = b"123"
        cert = util.generate_x509_cert_for_auth_key(auth_key_public, credential_key_private,
                                                    pop_sha256)

        # TODO: check ProofOfBinding at OID
        # TODO: check signature


class mdlServerTest(tornado.testing.AsyncHTTPTestCase):

    def get_app(self):
        self.s = server.Server(":memory:")
        util.setup_test_data(self.s.database)
        return self.s.get_app()

    def test_happy_path(self):
        path = "/mdlServer"
        response = self.fetch(method="POST", path=path,
                              body=cbor.dumps({
                                  "messageType": "StartProvisioning",
                                  "provisioningCode": "1001",
                                  # TODO: MCD
                              }))
        self.assertEqual(response.code, 200)
        cbor_response = cbor.loads(response.body)
        self.assertEqual(cbor_response["messageType"], "ReadyToProvisionMessage")
        session_id = cbor_response["eSessionId"]
        self.assertTrue(len(session_id) > 0)

        # TODO: insert AdditionalInformationRequired steps here

        response = self.fetch(method="POST", path=path, body=cbor.dumps({
            "messageType": "com.android.identity_credential.StartProvisioning",
            "eSessionId": session_id,
        }))
        self.assertEqual(response.code, 200)
        cbor_response = cbor.loads(response.body)
        self.assertEqual(cbor_response["messageType"],
                         "com.android.identity_credential.ProvisioningResponse")
        self.assertEqual(session_id, cbor_response["eSessionId"])
        challenge = cbor_response["challenge"]
        self.assertTrue(isinstance(challenge, bytes) and len(challenge) > 0)
        doc_type = cbor_response["docType"]
        self.assertEqual(doc_type, "org.iso.18013.5.1.mDL")

        credential_key = ec.generate_private_key(ec.SECP256R1(), default_backend())
        # TODO: pass challenge and other Android-specific things to
        # be included in the Android Attestation Extension
        #
        cert_chain = util.generate_x509_cert_for_credential_key(credential_key)
        response = self.fetch(method="POST", path=path,
                              body=cbor.dumps({
                                  "messageType": "com.android.identity_credential.SetCertificateChain",
                                  "eSessionId": session_id,
                                  "credentialKeyCertificateChain": cert_chain,
                              }))
        self.assertEqual(response.code, 200)
        cbor_response = cbor.loads(response.body)
        self.assertEqual(cbor_response["messageType"],
                         "com.android.identity_credential.DataToProvisionMessage")
        self.assertEqual(session_id, cbor_response["eSessionId"])
        access_control_profiles = cbor_response["accessControlProfiles"]
        self.assertTrue(access_control_profiles and isinstance(access_control_profiles, list))
        name_spaces = cbor_response["nameSpaces"]
        self.assertTrue(name_spaces and isinstance(name_spaces, dict))

        # Build ProofOfProvisioning
        #
        proof_of_provisioning = cbor.dumps(["ProofOfProvisioning",
                                            doc_type,
                                            access_control_profiles,
                                            name_spaces,
                                            False])
        pop_sha256 = hashlib.sha256(proof_of_provisioning).digest()
        pop_signature = util.cose_sign1_sign(credential_key, proof_of_provisioning)
        response = self.fetch(method="POST", path=path,
                              body=cbor.dumps({
                                  "messageType": "com.android.identity_credential.SetProofOfProvisioning",
                                  "eSessionId": session_id,
                                  "proofOfProvisioningSignature": pop_signature,
                              }))
        self.assertEqual(response.code, 200)
        cbor_response = cbor.loads(response.body)
        self.assertEqual(cbor_response["messageType"], "EndSessionMessage")
        self.assertEqual(session_id, cbor_response["eSessionId"])
        self.assertEqual("Success", cbor_response["reason"])

        # --------------------------------------------------------------------------------

        # Now get some auth keys. This is a new flow.
        #
        response = self.fetch(method="POST", path=path,
                              body=cbor.dumps({
                                  "messageType": "com.android.identity_credential.CertifyAuthKeys",
                                  "credentialKey": util.to_cose_key(credential_key.public_key()),
                              }))
        self.assertEqual(response.code, 200)
        cbor_response = cbor.loads(response.body)
        self.assertEqual(cbor_response["messageType"],
                         "com.android.identity_credential.CertifyAuthKeysProveOwnership")
        session_id = cbor_response["eSessionId"]
        self.assertTrue(len(session_id) > 0)
        challenge = cbor_response["challenge"]
        self.assertTrue(len(challenge) > 0)

        # Identify ourselves
        #
        proof_of_ownership = cbor.dumps(["ProofOfOwnership",
                                         doc_type,
                                         challenge,
                                         False])
        poo_signature = util.cose_sign1_sign(credential_key, proof_of_ownership)
        response = self.fetch(method="POST", path=path,
                              body=cbor.dumps({
                                  "messageType": "com.android.identity_credential.CertifyAuthKeysProveOwnershipResponse",
                                  "eSessionId": session_id,
                                  "proofOfOwnershipSignature": poo_signature,
                              }))
        self.assertEqual(response.code, 200)
        cbor_response = cbor.loads(response.body)
        self.assertEqual(cbor_response["messageType"],
                         "com.android.identity_credential.CertifyAuthKeysReady")
        self.assertEqual(session_id, cbor_response["eSessionId"])

        # Create some auth keys and send them
        #
        auth_key_certs = []
        for n in range(3):
            auth_key = ec.generate_private_key(ec.SECP256R1(), default_backend())
            cert = util.generate_x509_cert_for_auth_key(auth_key.public_key(), credential_key,
                                                        pop_sha256)
            auth_key_certs.append(cert)

        response = self.fetch(method="POST", path=path,
                              body=cbor.dumps({
                                  "messageType": "com.android.identity_credential.CertifyAuthKeysSendCerts",
                                  "eSessionId": session_id,
                                  "authKeyCerts": auth_key_certs,
                              }))
        self.assertEqual(response.code, 200)
        cbor_response = cbor.loads(response.body)
        self.assertEqual(cbor_response["messageType"],
                         "com.android.identity_credential.CertifyAuthKeysResponse")
        self.assertEqual(session_id, cbor_response["eSessionId"])
        static_auth_datas = cbor_response["staticAuthDatas"]
        # TODO: inspect |staticAuthDatas|

        response = self.fetch(method="POST", path=path,
                              body=cbor.dumps({
                                  "messageType": "RequestEndSession",
                                  "eSessionId": session_id,
                              }))
        self.assertEqual(response.code, 200)
        cbor_response = cbor.loads(response.body)
        self.assertEqual(cbor_response["messageType"], "EndSessionMessage")
        self.assertEqual(session_id, cbor_response["eSessionId"])
        self.assertEqual("Success", cbor_response["reason"])

        # --------------------------------------------------------------------------------

        # Check update. This is a new flow.
        #
        response = self.fetch(method="POST", path=path,
                              body=cbor.dumps({
                                  "messageType": "com.android.identity_credential.UpdateCredential",
                                  "credentialKey": util.to_cose_key(credential_key.public_key()),
                              }))
        self.assertEqual(response.code, 200)
        cbor_response = cbor.loads(response.body)
        self.assertEqual(cbor_response["messageType"],
                         "com.android.identity_credential.UpdateCredentialProveOwnership")
        session_id = cbor_response["eSessionId"]
        self.assertTrue(len(session_id) > 0)
        challenge = cbor_response["challenge"]
        self.assertTrue(len(challenge) > 0)

        # Identify ourselves expected no updates
        #
        proof_of_ownership = cbor.dumps(["ProofOfOwnership",
                                         doc_type,
                                         challenge,
                                         False])
        poo_signature = util.cose_sign1_sign(credential_key, proof_of_ownership)
        response = self.fetch(method="POST", path=path,
                              body=cbor.dumps({
                                  "messageType": "com.android.identity_credential.UpdateCredentialProveOwnershipResponse",
                                  "eSessionId": session_id,
                                  "proofOfOwnershipSignature": poo_signature,
                              }))
        self.assertEqual(response.code, 200)
        cbor_response = cbor.loads(response.body)
        self.assertEqual(cbor_response["messageType"],
                         "com.android.identity_credential.UpdateCredentialResponse")
        self.assertEqual(session_id, cbor_response["eSessionId"])
        # self.assertEqual("no_update", cbor_response["updateCredentialResult"])

        # --------------------------------------------------------------------------------

        # Change the document data in the database.
        # document_id 11 - Erika
        util.update_document_test_data(self.s.database, 11)

        # Check update. This is a new flow.
        #
        response = self.fetch(method="POST", path=path,
                              body=cbor.dumps({
                                  "messageType": "com.android.identity_credential.UpdateCredential",
                                  "credentialKey": util.to_cose_key(credential_key.public_key()),
                              }))
        self.assertEqual(response.code, 200)
        cbor_response = cbor.loads(response.body)
        self.assertEqual(cbor_response["messageType"],
                         "com.android.identity_credential.UpdateCredentialProveOwnership")
        session_id = cbor_response["eSessionId"]
        self.assertTrue(len(session_id) > 0)
        challenge = cbor_response["challenge"]
        self.assertTrue(len(challenge) > 0)

        # Identify ourselves expected update
        #
        proof_of_ownership = cbor.dumps(["ProofOfOwnership",
                                         doc_type,
                                         challenge,
                                         False])
        poo_signature = util.cose_sign1_sign(credential_key, proof_of_ownership)
        response = self.fetch(method="POST", path=path,
                              body=cbor.dumps({
                                  "messageType": "com.android.identity_credential.UpdateCredentialProveOwnershipResponse",
                                  "eSessionId": session_id,
                                  "proofOfOwnershipSignature": poo_signature,
                              }))
        self.assertEqual(response.code, 200)
        cbor_response = cbor.loads(response.body)
        self.assertEqual(cbor_response["messageType"],
                         "com.android.identity_credential.UpdateCredentialResponse")
        self.assertEqual(session_id, cbor_response["eSessionId"])
        self.assertEqual("update", cbor_response["updateCredentialResult"])

        # Get data to update (new provisioning)
        #
        response = self.fetch(method="POST", path=path,
                              body=cbor.dumps({
                                  "messageType": "com.android.identity_credential.UpdateCredentialGetDataToUpdate",
                                  "eSessionId": session_id,
                              }))
        self.assertEqual(response.code, 200)
        cbor_response = cbor.loads(response.body)
        self.assertEqual(cbor_response["messageType"],
                         "com.android.identity_credential.UpdateCredentialDataToProvisionMessage")
        self.assertEqual(session_id, cbor_response["eSessionId"])
        access_control_profiles = cbor_response["accessControlProfiles"]
        self.assertTrue(access_control_profiles and isinstance(access_control_profiles, list))
        name_spaces = cbor_response["nameSpaces"]
        self.assertTrue(name_spaces and isinstance(name_spaces, dict))

        # Build ProofOfProvisioning for updated data
        #
        proof_of_provisioning = cbor.dumps(["ProofOfProvisioning",
                                            doc_type,
                                            access_control_profiles,
                                            name_spaces,
                                            False])
        pop_sha256 = hashlib.sha256(proof_of_provisioning).digest()
        pop_signature = util.cose_sign1_sign(credential_key, proof_of_provisioning)
        response = self.fetch(method="POST", path=path,
                              body=cbor.dumps({
                                  "messageType": "com.android.identity_credential.UpdateCredentialSetProofOfProvisioning",
                                  "eSessionId": session_id,
                                  "proofOfProvisioningSignature": pop_signature,
                              }))
        self.assertEqual(response.code, 200)
        cbor_response = cbor.loads(response.body)
        self.assertEqual(cbor_response["messageType"], "EndSessionMessage")
        self.assertEqual(session_id, cbor_response["eSessionId"])
        self.assertEqual("Success", cbor_response["reason"])

        # --------------------------------------------------------------------------------

        # Delete credential. This is a new flow.
        #
        response = self.fetch(method="POST", path=path,
                              body=cbor.dumps({
                                  "messageType": "com.android.identity_credential.DeleteCredential",
                                  "credentialKey": util.to_cose_key(credential_key.public_key()),
                              }))
        self.assertEqual(response.code, 200)
        cbor_response = cbor.loads(response.body)
        self.assertEqual(cbor_response["messageType"],
                         "com.android.identity_credential.DeleteCredentialProveOwnership")
        session_id = cbor_response["eSessionId"]
        self.assertTrue(len(session_id) > 0)
        challenge = cbor_response["challenge"]
        self.assertTrue(len(challenge) > 0)

        # Identify ourselves expected update
        #
        proof_of_ownership = cbor.dumps(["ProofOfOwnership",
                                         doc_type,
                                         challenge,
                                         False])
        poo_signature = util.cose_sign1_sign(credential_key, proof_of_ownership)
        response = self.fetch(method="POST", path=path,
                              body=cbor.dumps({
                                  "messageType": "com.android.identity_credential.DeleteCredentialProveOwnershipResponse",
                                  "eSessionId": session_id,
                                  "proofOfOwnershipSignature": poo_signature,
                              }))
        self.assertEqual(response.code, 200)
        cbor_response = cbor.loads(response.body)
        self.assertEqual(cbor_response["messageType"],
                         "com.android.identity_credential.DeleteCredentialReadyForDeletion")
        self.assertEqual(session_id, cbor_response["eSessionId"])
        challenge = cbor_response["challenge"]
        self.assertTrue(len(challenge) > 0)

        # Get data to update (new provisioning)
        #
        proof_of_deletion = cbor.dumps(["ProofOfDeletion",
                                        doc_type,
                                        challenge,
                                        False])
        pod_signature = util.cose_sign1_sign(credential_key, proof_of_deletion)
        response = self.fetch(method="POST", path=path,
                              body=cbor.dumps({
                                  "messageType": "com.android.identity_credential.DeleteCredentialDeleted",
                                  "eSessionId": session_id,
                                  "proofOfDeletionSignature": pod_signature,
                              }))
        self.assertEqual(response.code, 200)
        cbor_response = cbor.loads(response.body)
        self.assertEqual(cbor_response["messageType"], "EndSessionMessage")
        self.assertEqual(session_id, cbor_response["eSessionId"])
        self.assertEqual("Success", cbor_response["reason"])


if __name__ == '__main__':
    unittest.main()
