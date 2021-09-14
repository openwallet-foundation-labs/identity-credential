import cbor
import datetime
import hashlib
import logging
import random
import tornado.gen
import tornado.web

import database
import util

logger = logging.getLogger("mdl-ref-server.server")

from cryptography.hazmat.backends import default_backend
from cryptography.hazmat.primitives.asymmetric import ec


class SessionError(Exception):
    def __init__(self, message):
        Exception.__init__(self, message)


class Session:
    def __init__(self, server, session_id):
        self.server = server
        self.session_id = session_id


class ProvisioningSession(Session):
    STATE_NONE = 0
    STATE_GENERIC_START_PROVISIONING_CALLED = 1
    STATE_START_PROVISIONING_CALLED = 2
    STATE_SET_CERTIFICATE_CHAIN_CALLED = 3
    STATE_SET_PROOF_OF_PROVISIONING_CALLED = 4

    def __init__(self, server, session_id):
        super().__init__(server, session_id)
        self.state = ProvisioningSession.STATE_NONE

    # ----

    def generic_flow_start_provisioning(self, handler, data):
        if self.state != ProvisioningSession.STATE_NONE:
            raise SessionError("called from invalid state (%d)" % (self.state))
        self.state = ProvisioningSession.STATE_GENERIC_START_PROVISIONING_CALLED

        provisioning_code = data.get("provisioningCode")
        if not provisioning_code:
            raise SessionError("No provisioning code")

        self.issued_document = self.server.database.lookup_issued_document_by_provisioning_code(
            provisioning_code)
        if not self.issued_document:
            raise SessionError("No issued document for provisioning code")

        self.document = self.server.database.lookup_document_by_document_id(
            self.issued_document.document_id)
        if not self.document:
            raise SessionError("No document for issued document")

        handler.write(cbor.dumps({"messageType": "ReadyToProvisionMessage",
                                  "eSessionId": self.session_id,
                                  }))

    # ----

    def start_provisioning(self, handler, data):
        if self.state != ProvisioningSession.STATE_GENERIC_START_PROVISIONING_CALLED:
            raise SessionError("called from invalid state (%d)" % (self.state))
        self.state = ProvisioningSession.STATE_START_PROVISIONING_CALLED

        self.challenge = b"FixedChallenge"
        handler.write(
            cbor.dumps({"messageType": "com.android.identity_credential.ProvisioningResponse",
                        "eSessionId": self.session_id,
                        "challenge": self.challenge,
                        "docType": self.document.doc_type,
                        }))

    # ----

    def set_certificate_chain(self, handler, data):
        if self.state != ProvisioningSession.STATE_START_PROVISIONING_CALLED:
            raise SessionError("called from invalid state (%d)" % (self.state))
        self.state = ProvisioningSession.STATE_SET_CERTIFICATE_CHAIN_CALLED

        cert_chain = data["credentialKeyCertificateChain"]
        if not cert_chain:
            raise SessionError("No certificate chain")
        if not util.credential_key_cert_chain_validate(cert_chain, self.challenge):
            raise SessionError("Certificate chain did not validate")
        self.credential_key_x509_cert_chain = cert_chain
        self.credential_key = util.cert_chain_get_public_key(cert_chain)
        access_control_profiles = cbor.loads(self.document.access_control_profiles)
        name_spaces = cbor.loads(self.document.name_spaces)
        handler.write(
            cbor.dumps({"messageType": "com.android.identity_credential.DataToProvisionMessage",
                        "eSessionId": self.session_id,
                        "accessControlProfiles": access_control_profiles,
                        "nameSpaces": name_spaces,
                        }))

    # ----

    def set_proof_of_provisioning(self, handler, data):
        if self.state != ProvisioningSession.STATE_SET_CERTIFICATE_CHAIN_CALLED:
            raise SessionError("called from invalid state (%d)" % (self.state))
        self.state = ProvisioningSession.STATE_SET_PROOF_OF_PROVISIONING_CALLED

        pop_signature = data["proofOfProvisioningSignature"]
        if not pop_signature:
            raise SessionError("No proofOfProvisioningSignature")
        pop = util.cose_sign1_get_data(pop_signature)
        if not util.cose_sign1_verify(self.credential_key, pop_signature, pop):
            raise SessionError("Error verifying proofOfProvisioningSignature")

        # get 'now(UTC)' to create a new timestamp for 'last_updated_timestamp'
        now = datetime.datetime.now(datetime.timezone.utc)
        last_updated_timestamp = datetime.datetime.timestamp(now)

        # Now that it's provisioned, create a new record in the |configured_documents| table.
        self.server.database.add_configured_documents_entry(
            self.issued_document.issued_document_id,
            self.credential_key_x509_cert_chain,
            pop,
            last_updated_timestamp,
            self.document.data_timestamp)

        self.server.database.commit()

        self.server.end_session(self, handler, "Success", "")
    # ----


class CertifyAuthKeysSession(Session):
    STATE_NONE = 0
    STATE_CERTIFY_AUTH_KEYS_CALLED = 1
    STATE_CERTIFY_AUTH_KEYS_CHALLENGE_RESPONSE_CALLED = 2
    STATE_CERTIFY_AUTH_KEYS_SEND_CERTS_CALLED = 3

    def __init__(self, server, session_id):
        super().__init__(server, session_id)
        self.state = CertifyAuthKeysSession.STATE_NONE

    def certify_auth_keys(self, handler, data):
        if self.state != CertifyAuthKeysSession.STATE_NONE:
            raise SessionError("called from invalid state (%d)" % (self.state))
        self.state = CertifyAuthKeysSession.STATE_CERTIFY_AUTH_KEYS_CALLED

        cose_credential_key = data["credentialKey"]
        if not cose_credential_key:
            raise SessionError("No credentialKey")
        credential_key = util.from_cose_key(cose_credential_key)
        if not credential_key:
            raise SessionError("Error decoding COSE_Key for CredentialKey")

        self.configured_document = self.server.database.lookup_configured_document_by_credential_key(
            credential_key)
        self.credential_key = util.cert_chain_get_public_key(
            self.configured_document.credential_key_x509_cert_chain)
        self.issued_document = self.server.database.lookup_issued_document_by_issued_document_id(
            self.configured_document.issued_document_id)
        self.document = self.server.database.lookup_document_by_document_id(
            self.issued_document.document_id)

        self.prove_ownership_challenge = b"FixedChallenge"
        handler.write(cbor.dumps(
            {"messageType": "com.android.identity_credential.CertifyAuthKeysProveOwnership",
             "eSessionId": self.session_id,
             "challenge": self.prove_ownership_challenge,
             }))

    # ----
    def certify_auth_keys_prove_ownership_response(self, handler, data):
        if self.state != CertifyAuthKeysSession.STATE_CERTIFY_AUTH_KEYS_CALLED:
            raise SessionError("called from invalid state (%d)" % (self.state))
        self.state = CertifyAuthKeysSession.STATE_CERTIFY_AUTH_KEYS_CHALLENGE_RESPONSE_CALLED

        poo_signature = data["proofOfOwnershipSignature"]
        if not poo_signature:
            raise SessionError("No proofOfOwnershipSignature")
        poo = util.cose_sign1_get_data(poo_signature)
        if not util.cose_sign1_verify(self.credential_key, poo_signature, poo):
            raise SessionError("Error verifying proofOfOwnershipSignature")
        # TODO: check challenge
        handler.write(
            cbor.dumps({"messageType": "com.android.identity_credential.CertifyAuthKeysReady",
                        "eSessionId": self.session_id,
                        }))

    # ----
    def certify_auth_keys_send_certs(self, handler, data):
        if self.state != CertifyAuthKeysSession.STATE_CERTIFY_AUTH_KEYS_CHALLENGE_RESPONSE_CALLED:
            raise SessionError("called from invalid state (%d)" % (self.state))
        self.state = CertifyAuthKeysSession.STATE_CERTIFY_AUTH_KEYS_SEND_CERTS_CALLED

        # TODO: take from server so it's shared with tests
        issuer_key = ec.generate_private_key(ec.SECP256R1(), default_backend())
        issuer_cert = util.generate_x509_cert_issuer_auth(issuer_key)

        auth_key_certs = data["authKeyCerts"]
        static_auth_datas = []
        pop_sha256 = hashlib.sha256(self.configured_document.proof_of_provisioning).digest()
        for cert in auth_key_certs:
            if not util.auth_key_cert_validate(cert,
                                               self.credential_key,
                                               pop_sha256):
                raise SessionError("Auth key cert not valid")
            auth_key = util.cert_chain_get_public_key(cert)
            static_auth_data = util.generate_static_auth_data_for_auth_key(self.document.doc_type,
                                                                           cbor.loads(
                                                                               self.document.name_spaces),
                                                                           self.credential_key,
                                                                           auth_key,
                                                                           issuer_key,
                                                                           issuer_cert)
            static_auth_datas.append(static_auth_data)

        handler.write(
            cbor.dumps({"messageType": "com.android.identity_credential.CertifyAuthKeysResponse",
                        "eSessionId": self.session_id,
                        "staticAuthDatas": static_auth_datas,
                        }))
    # ----


class UpdateCredentialSession(Session):
    STATE_NONE = 0
    STATE_UPDATE_CREDENTIAL_CALLED = 1
    STATE_UPDATE_CREDENTIAL_PROOF_OF_OWNERSHIP_CALLED = 2
    STATE_UPDATE_CREDENTIAL_NO_UPDATE = 3
    STATE_UPDATE_CREDENTIAL_DELETE = 4
    STATE_UPDATE_CREDENTIAL_UPDATE = 5
    STATE_UPDATE_CREDENTIAL_GET_UPDATE_DATA_CALLED = 6
    STATE_UPDATE_CREDENTIAL_SET_PROOF_OF_PROVISIONING_CALLED = 7

    def __init__(self, server, session_id):
        super().__init__(server, session_id)
        self.state = UpdateCredentialSession.STATE_NONE

    def update_credential(self, handler, data):
        if self.state != UpdateCredentialSession.STATE_NONE:
            raise SessionError("called from invalid state (%d)" % (self.state))
        self.state = UpdateCredentialSession.STATE_UPDATE_CREDENTIAL_CALLED

        cose_credential_key = data["credentialKey"]
        if not cose_credential_key:
            raise SessionError("No credentialKey")
        credential_key = util.from_cose_key(cose_credential_key)
        if not credential_key:
            raise SessionError("Error decoding COSE_Key for CredentialKey")

        self.configured_document = self.server.database.lookup_configured_document_by_credential_key(
            credential_key)
        self.credential_key = util.cert_chain_get_public_key(
            self.configured_document.credential_key_x509_cert_chain)
        self.issued_document = self.server.database.lookup_issued_document_by_issued_document_id(
            self.configured_document.issued_document_id)
        self.document = self.server.database.lookup_document_by_document_id(
            self.issued_document.document_id)

        self.prove_ownership_challenge = b"FixedChallengeUpdate"
        handler.write(cbor.dumps(
            {"messageType": "com.android.identity_credential.UpdateCredentialProveOwnership",
             "eSessionId": self.session_id,
             "challenge": self.prove_ownership_challenge,
             }))

    # ----
    def update_credential_prove_ownership_response(self, handler, data):
        if self.state != UpdateCredentialSession.STATE_UPDATE_CREDENTIAL_CALLED:
            raise SessionError("called from invalid state (%d)" % (self.state))
        self.state = UpdateCredentialSession.STATE_UPDATE_CREDENTIAL_PROOF_OF_OWNERSHIP_CALLED

        poo_signature = data["proofOfOwnershipSignature"]
        if not poo_signature:
            raise SessionError("No proofOfOwnershipSignature")
        poo = util.cose_sign1_get_data(poo_signature)
        if not util.cose_sign1_verify(self.credential_key, poo_signature, poo):
            raise SessionError("Error verifying proofOfOwnershipSignature")
        # TODO: check challenge

        # Check if there is an update
        if self.configured_document.status == "TO_DELETE":
            self.state = UpdateCredentialSession.STATE_UPDATE_CREDENTIAL_DELETE
            handler.write(cbor.dumps(
                {"messageType": "com.android.identity_credential.UpdateCredentialResponse",
                 "eSessionId": self.session_id,
                 "updateCredentialResult": "delete",
                 }))
        elif self.document.data_timestamp == self.configured_document.data_timestamp:
            self.state = UpdateCredentialSession.STATE_UPDATE_CREDENTIAL_NO_UPDATE
            handler.write(cbor.dumps(
                {"messageType": "com.android.identity_credential.UpdateCredentialResponse",
                 "eSessionId": self.session_id,
                 "updateCredentialResult": "no_update",
                 }))
        else:
            self.state = UpdateCredentialSession.STATE_UPDATE_CREDENTIAL_UPDATE
            handler.write(cbor.dumps(
                {"messageType": "com.android.identity_credential.UpdateCredentialResponse",
                 "eSessionId": self.session_id,
                 "updateCredentialResult": "update",
                 }))

    # ----
    def update_credential_get_updated_data(self, handler):
        if self.state != UpdateCredentialSession.STATE_UPDATE_CREDENTIAL_UPDATE:
            raise SessionError("called from invalid state (%d)" % (self.state))
        self.state = UpdateCredentialSession.STATE_UPDATE_CREDENTIAL_GET_UPDATE_DATA_CALLED

        access_control_profiles = cbor.loads(self.document.access_control_profiles)
        name_spaces = cbor.loads(self.document.name_spaces)
        handler.write(cbor.dumps({
            "messageType": "com.android.identity_credential.UpdateCredentialDataToProvisionMessage",
            "eSessionId": self.session_id,
            "accessControlProfiles": access_control_profiles,
            "nameSpaces": name_spaces,
        }))

    # ----
    def update_credential_set_proof_of_provisioning(self, handler, data):
        if self.state != UpdateCredentialSession.STATE_UPDATE_CREDENTIAL_GET_UPDATE_DATA_CALLED:
            raise SessionError("called from invalid state (%d)" % (self.state))
        self.state = UpdateCredentialSession.STATE_UPDATE_CREDENTIAL_SET_PROOF_OF_PROVISIONING_CALLED

        pop_signature = data["proofOfProvisioningSignature"]
        if not pop_signature:
            raise SessionError("No proofOfProvisioningSignature")
        pop = util.cose_sign1_get_data(pop_signature)
        if not util.cose_sign1_verify(self.credential_key, pop_signature, pop):
            raise SessionError("Error verifying proofOfProvisioningSignature")

        # get 'now(UTC)' to create a new timestamp for 'last_updated_timestamp'
        now = datetime.datetime.now(datetime.timezone.utc)
        last_updated_timestamp = datetime.datetime.timestamp(now)

        # Now that it's provisioned, create a new record in the |configured_documents| table.
        self.server.database.update_configured_documents_entry(
            self.configured_document.configured_document_id,
            pop,
            last_updated_timestamp,
            self.document.data_timestamp)

        self.server.database.commit()

        self.server.end_session(self, handler, "Success", "")
    # ----


class DeleteCredentialSession(Session):
    STATE_NONE = 0
    STATE_DELETE_CREDENTIAL_CALLED = 1
    STATE_DELETE_CREDENTIAL_PROOF_OF_OWNERSHIP_CALLED = 2
    STATE_DELETE_CREDENTIAL_DELETED_CALLED = 3

    def __init__(self, server, session_id):
        super().__init__(server, session_id)
        self.state = DeleteCredentialSession.STATE_NONE

    def delete_credential(self, handler, data):
        if self.state != DeleteCredentialSession.STATE_NONE:
            raise SessionError("called from invalid state (%d)" % (self.state))
        self.state = DeleteCredentialSession.STATE_DELETE_CREDENTIAL_CALLED

        cose_credential_key = data["credentialKey"]
        if not cose_credential_key:
            raise SessionError("No credentialKey")
        credential_key = util.from_cose_key(cose_credential_key)
        if not credential_key:
            raise SessionError("Error decoding COSE_Key for CredentialKey")

        self.configured_document = self.server.database.lookup_configured_document_by_credential_key(
            credential_key)
        self.credential_key = util.cert_chain_get_public_key(
            self.configured_document.credential_key_x509_cert_chain)
        self.issued_document = self.server.database.lookup_issued_document_by_issued_document_id(
            self.configured_document.issued_document_id)
        self.document = self.server.database.lookup_document_by_document_id(
            self.issued_document.document_id)

        self.prove_ownership_challenge = b"FixedChallengeDelete"
        handler.write(cbor.dumps(
            {"messageType": "com.android.identity_credential.DeleteCredentialProveOwnership",
             "eSessionId": self.session_id,
             "challenge": self.prove_ownership_challenge,
             }))

    # ----
    def delete_credential_prove_ownership_response(self, handler, data):
        if self.state != DeleteCredentialSession.STATE_DELETE_CREDENTIAL_CALLED:
            raise SessionError("called from invalid state (%d)" % (self.state))
        self.state = DeleteCredentialSession.STATE_DELETE_CREDENTIAL_PROOF_OF_OWNERSHIP_CALLED

        poo_signature = data["proofOfOwnershipSignature"]
        if not poo_signature:
            raise SessionError("No proofOfOwnershipSignature")
        poo = util.cose_sign1_get_data(poo_signature)
        if not util.cose_sign1_verify(self.credential_key, poo_signature, poo):
            raise SessionError("Error verifying proofOfOwnershipSignature")
        # TODO: check challenge

        self.prove_delete_challenge = b"FixedProveDeleteChallenge"
        handler.write(cbor.dumps(
            {"messageType": "com.android.identity_credential.DeleteCredentialReadyForDeletion",
             "eSessionId": self.session_id,
             "challenge": self.prove_delete_challenge,
             }))

    # ----
    def delete_credential_deleted(self, handler, data):
        if self.state != DeleteCredentialSession.STATE_DELETE_CREDENTIAL_PROOF_OF_OWNERSHIP_CALLED:
            raise SessionError("called from invalid state (%d)" % (self.state))
        self.state = DeleteCredentialSession.STATE_DELETE_CREDENTIAL_DELETED_CALLED

        pop_deletion = data["proofOfDeletionSignature"]
        if not pop_deletion:
            raise SessionError("No proofOfDeletionSignature")
        pod = util.cose_sign1_get_data(pop_deletion)
        if not util.cose_sign1_verify(self.credential_key, pop_deletion, pod):
            raise SessionError("Error verifying proofOfDeletionSignature")

        # Now that it's deleted, delete the record from |configured_documents| table.
        self.server.database.delete_configured_documents_entry(
            self.configured_document.configured_document_id)

        self.server.database.commit()

        self.server.end_session(self, handler, "Success", "")
    # ----


class MainHandler(tornado.web.RequestHandler):

    def initialize(self, server_object):
        self.server = server_object

    def get(self):
        self.write("Hello, world")

    def post(self):
        data = cbor.loads(self.request.body)
        messageType = str(data.get("messageType"))
        if not messageType:
            raise tornado.web.HTTPError(500)
        # ----
        if messageType == "RequestEndSession":
            session = self.server.lookup_session(data["eSessionId"])
            if not session:
                raise tornado.web.HTTPError(500)
            self.server.end_session(session, self, "Success", "")
        # ----
        elif messageType == "StartProvisioning":
            session = self.server.start_session(ProvisioningSession)
            try:
                session.generic_flow_start_provisioning(self, data)
            except Exception as e:
                self.server.end_session(session, self, "Failed", str(e))
        elif messageType == "com.android.identity_credential.StartProvisioning":
            session = self.server.lookup_session(data["eSessionId"])
            if not session:
                raise tornado.web.HTTPError(500)
            try:
                session.start_provisioning(self, data)
            except Exception as e:
                self.server.end_session(session, self, "Failed", str(e))
        elif messageType == "com.android.identity_credential.SetCertificateChain":
            session = self.server.lookup_session(data["eSessionId"])
            if not session:
                raise tornado.web.HTTPError(500)
            try:
                session.set_certificate_chain(self, data)
            except Exception as e:
                self.server.end_session(session, self, "Failed", str(e))
        elif messageType == "com.android.identity_credential.SetProofOfProvisioning":
            session = self.server.lookup_session(data["eSessionId"])
            if not session:
                raise tornado.web.HTTPError(500)
            try:
                session.set_proof_of_provisioning(self, data)
            except Exception as e:
                self.server.end_session(session, self, "Failed", str(e))
        # ----
        elif messageType == "com.android.identity_credential.CertifyAuthKeys":
            session = self.server.start_session(CertifyAuthKeysSession)
            try:
                session.certify_auth_keys(self, data)
            except Exception as e:
                self.server.end_session(session, self, "Failed", str(e))
        elif messageType == "com.android.identity_credential.CertifyAuthKeysProveOwnershipResponse":
            session = self.server.lookup_session(data["eSessionId"])
            if not session:
                raise tornado.web.HTTPError(500)
            try:
                session.certify_auth_keys_prove_ownership_response(self, data)
            except Exception as e:
                self.server.end_session(session, self, "Failed", str(e))
        elif messageType == "com.android.identity_credential.CertifyAuthKeysSendCerts":
            session = self.server.lookup_session(data["eSessionId"])
            if not session:
                raise tornado.web.HTTPError(500)
            try:
                session.certify_auth_keys_send_certs(self, data)
            except Exception as e:
                self.server.end_session(session, self, "Failed", str(e))
        # ----
        elif messageType == "com.android.identity_credential.UpdateCredential":
            session = self.server.start_session(UpdateCredentialSession)
            try:
                session.update_credential(self, data)
            except Exception as e:
                self.server.end_session(session, self, "Failed", str(e))
        elif messageType == "com.android.identity_credential.UpdateCredentialProveOwnershipResponse":
            session = self.server.lookup_session(data["eSessionId"])
            if not session:
                raise tornado.web.HTTPError(500)
            try:
                session.update_credential_prove_ownership_response(self, data)
            except Exception as e:
                self.server.end_session(session, self, "Failed", str(e))
        elif messageType == "com.android.identity_credential.UpdateCredentialGetDataToUpdate":
            session = self.server.lookup_session(data["eSessionId"])
            if not session:
                raise tornado.web.HTTPError(500)
            try:
                session.update_credential_get_updated_data(self)
            except Exception as e:
                self.server.end_session(session, self, "Failed", str(e))
        elif messageType == "com.android.identity_credential.UpdateCredentialSetProofOfProvisioning":
            session = self.server.lookup_session(data["eSessionId"])
            if not session:
                raise tornado.web.HTTPError(500)
            try:
                session.update_credential_set_proof_of_provisioning(self, data)
            except Exception as e:
                self.server.end_session(session, self, "Failed", str(e))
        # ----
        elif messageType == "com.android.identity_credential.DeleteCredential":
            session = self.server.start_session(DeleteCredentialSession)
            try:
                session.delete_credential(self, data)
            except Exception as e:
                self.server.end_session(session, self, "Failed", str(e))
        elif messageType == "com.android.identity_credential.DeleteCredentialProveOwnershipResponse":
            session = self.server.lookup_session(data["eSessionId"])
            if not session:
                raise tornado.web.HTTPError(500)
            try:
                session.delete_credential_prove_ownership_response(self, data)
            except Exception as e:
                self.server.end_session(session, self, "Failed", str(e))
        elif messageType == "com.android.identity_credential.DeleteCredentialDeleted":
            session = self.server.lookup_session(data["eSessionId"])
            if not session:
                raise tornado.web.HTTPError(500)
            try:
                session.delete_credential_deleted(self, data)
            except Exception as e:
                self.server.end_session(session, self, "Failed", str(e))
        # ----
        else:
            logger.warning("Unknown message with type '%s'" % messageType)
            raise tornado.web.HTTPError(500)


class AdminHandler(tornado.web.RequestHandler):

    def initialize(self, server_object):
        self.server = server_object

    def get(self):
        self.write("""
<html>
  <head>
  <link rel="stylesheet" href="/static/style.css">
  </head>
  <title>Persons known by Issuing Server</title>
  <h1>Persons known by Issuing Server</h1>
""")
        self.write("<table style='width:100%'>"
                   "<tr>"
                   "<th>Person ID</th>"
                   "<th>Name</th>"
                   "<th>Portrait</th>"
                   "</tr>")
        persons = self.server.database.lookup_persons()
        for person in persons:
            self.write("<tr>");
            self.write("<td>%s</td>" % person.person_id)
            self.write("<td><a href='/admin/person?person_id=%d'>%s</a></td>" % (
                person.person_id, person.name))
            self.write(
                "<td><img src='/admin/portrait?person_id=%d' height=100></td>" % person.person_id)
            self.write("</tr>");
        self.write("</table>")
        self.write("</html>")


class AdminPersonHandler(tornado.web.RequestHandler):

    def initialize(self, server_object):
        self.server = server_object

    def get(self):
        person_id = int(self.get_argument("person_id"))
        # if has update_document_id parameters then changes document data
        update_document_id = self.get_argument("update_document_id", None)
        if update_document_id:
            util.update_document_test_data(self.server.database, int(update_document_id))
        person = self.server.database.lookup_person_by_person_id(person_id)
        self.write("""
<html>
  <head>
  <link rel="stylesheet" href="/static/style.css">
  </head>
  <title>Person: %s (%d)</title>
  <h1>Person: %s (%d)</h1>
""" % (person.name, person_id, person.name, person_id))
        self.write("<img src='/admin/portrait?person_id=%d' height=100>" % person.person_id)
        self.write("<p>")
        self.write("<table style='width:100%'>"
                   "<tr>"
                   "<th>Document ID</th>"
                   "<th>DocType</th>"
                   "<th>Access Control Profiles</th>"
                   "<th>Name Spaces</th>"
                   "<th>Actions</th>"
                   "<th>Issued Documents</th>"
                   "</tr>")
        document_ids = self.server.database.lookup_document_ids_by_person_id(person_id)
        for document_id in document_ids:
            document = self.server.database.lookup_document_by_document_id(document_id)
            self.write("<tr>")
            self.write("<td>%d</td>" % document_id)
            self.write("<td>%s</td>" % document.doc_type)

            name_spaces_text = util.render_name_spaces_in_html(document.name_spaces)
            acps_text = str(cbor.loads(document.access_control_profiles))
            self.write("<td>%s</td>" % acps_text)
            self.write("<td>%s</td>" % name_spaces_text)

            issued_document_ids = self.server.database.lookup_issued_document_ids_by_document_id(
                document_id)
            self.write("<td>")
            self.write(
                "<br><a href='/admin/person?person_id=%d&update_document_id=%d'>Update Data %d</a>"
                % (person_id, document_id, document_id))
            self.write("</td>")
            self.write("<td>")
            for issued_document_id in issued_document_ids:
                self.write(
                    "<br><a href='/admin/issued_document?issued_document_id=%d'>Issued Document %d</a>"
                    % (issued_document_id, issued_document_id))
            self.write("</td>")

            self.write("</tr>")
        self.write("</table>")
        self.write("</html>")


class AdminIssuedDocumentHandler(tornado.web.RequestHandler):

    def initialize(self, server_object):
        self.server = server_object

    def get(self):
        issued_document_id = int(self.get_argument("issued_document_id"))
        issued_document = self.server.database.lookup_issued_document_by_issued_document_id(
            issued_document_id)
        document = self.server.database.lookup_document_by_document_id(issued_document.document_id)
        person = self.server.database.lookup_person_by_person_id(document.person_id)
        # if has update_document_id parameters then changes document data
        delete_configured_document_id = self.get_argument("delete_configured_document_id", None)
        if delete_configured_document_id:
            util.set_configured_document_to_delete(self.server.database,
                                                   int(delete_configured_document_id))
        self.write("""
<html>
  <head>
  <link rel="stylesheet" href="/static/style.css">
  </head>
  <title>Issued Document for person: %s (%d)</title>
  <h1>Issued Document for person: %s (%d)</h1>
""" % (person.name, person.person_id, person.name, person.person_id))
        self.write("<img src='/admin/portrait?person_id=%d' height=100>" % person.person_id)
        self.write("<p><b>Issued Document ID</b>: %s" % issued_document_id)
        self.write("<br><b>Provisioning Code</b>: %s" % issued_document.provisioning_code)
        self.write("<p>Provisoned on the following mdoc/EID applications (WIP):")
        self.write("<p>")
        self.write("<table style='width:100%'>"
                   "<tr>"
                   "<th>Configured Document ID</th>"
                   "<th>CredentialKey X509 certificate</th>"
                   "<th>Proof Of Provisoning</th>"
                   "<th>Last Updated</th>"
                   "<th>Endorsed Auth Keys</th>"
                   "</tr>")
        configured_document_ids = self.server.database.lookup_configured_document_ids_by_issued_document_id(
            issued_document_id)
        for configured_document_id in configured_document_ids:
            self.write("<tr>")
            self.write(
                "<td>%d <a href='/admin/issued_document?issued_document_id=%d&delete_configured_document_id=%d'>Delete</a></td>"
                % (configured_document_id, issued_document_id, configured_document_id,))
            self.write("<td>TODO</td>")
            self.write("<td>TODO</td>")
            self.write("<td>TODO</td>")
            self.write("<td>TODO</td>")
            self.write("</tr>")
        self.write("</table>")
        self.write("</html>")


class AdminPortraitHandler(tornado.web.RequestHandler):

    def initialize(self, server_object):
        self.server = server_object

    def get(self):
        person_id = int(self.get_argument("person_id"))
        person = self.server.database.lookup_person_by_person_id(person_id)
        self.set_header("Content-Type", "image/jpeg")
        self.write(person.portrait)


class Server:
    def __init__(self, database_path):
        # map from session_id (str) to Session object
        self.sessions = {}

        self.database = database.SystemOfRecord(database_path)

        self.tornado_app = tornado.web.Application([
            # (r"/", MainHandler, {"server_object" : self}),
            (r"/mdlServer", MainHandler, {"server_object": self}),
            (r"/admin", AdminHandler, {"server_object": self}),
            (r"/admin/person", AdminPersonHandler, {"server_object": self}),
            (r"/admin/issued_document", AdminIssuedDocumentHandler, {"server_object": self}),
            (r"/admin/portrait", AdminPortraitHandler, {"server_object": self}),
            (r"/static/(.*)", tornado.web.StaticFileHandler, {"path": "static_files"}),
        ], debug=True)

    def get_database(self):
        return self.database

    def start_session(self, session_class):
        hex_digits = "0123456789abcdef"
        while True:
            session_id = "".join(random.choice(hex_digits) for i in range(16))
            if not self.sessions.get(session_id):
                session = session_class(self, session_id)
                self.sessions[session_id] = session
                return session

    def end_session(self, session, handler, reason, message):
        if reason != "Success":
            logger.warning("Terminating session with reason '%s', detail='%s'" % (reason, message))
        handler.write(cbor.dumps({
            "messageType": "EndSessionMessage",
            "eSessionId": session.session_id,
            "reason": reason,
        }))
        del self.sessions[session.session_id]

    def lookup_session(self, session_id):
        return self.sessions.get(session_id, None)

    def get_app(self):
        return self.tornado_app
