
import binascii
import cbor
import datetime
import hashlib
import random

from cryptography.hazmat.backends import default_backend
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.exceptions import InvalidSignature

from cryptography import x509
from cryptography.x509.oid import NameOID

from asn1crypto.core import Sequence, SequenceOf, Integer

from Crypto.Util.asn1 import DerOctetString

# From "COSE Algorithms" registry
COSE_LABEL_ALG = -7
COSE_ALG_ECDSA_256 = 5

def generate_x509_cert_for_credential_key(credential_key_private):
    subject = issuer = x509.Name([
        x509.NameAttribute(NameOID.COMMON_NAME, u"Android Identity Credential Key"),
    ])
    builder = (x509.CertificateBuilder()
               .subject_name(subject)
               .issuer_name(issuer)
               .public_key(credential_key_private.public_key())
               .serial_number(x509.random_serial_number())
               .not_valid_before(datetime.datetime.utcnow())
               # Our certificate will be valid for 365 days
               .not_valid_after(datetime.datetime.utcnow() + datetime.timedelta(days=365)))
    # TODO: include Android Attestation Extension as per
    # https://source.android.com/security/keystore/attestation
    #
    cert = builder.sign(credential_key_private, hashes.SHA256())
    cert_bytes = cert.public_bytes(serialization.Encoding.DER)
    #print("cert_bytes: %s" % binascii.hexlify(cert_bytes))
    return cert_bytes

def generate_x509_cert_for_auth_key(auth_key_public, credential_key_private, pop_sha256):
    issuer = x509.Name([
        x509.NameAttribute(NameOID.COMMON_NAME, u"Android Identity Credential Key"),
    ])
    subject = x509.Name([
        x509.NameAttribute(NameOID.COMMON_NAME, u"Android Identity Credential Authentication Key"),
    ])
    proof_of_binding_cbor = cbor.dumps(["ProofOfBinding",
                                        pop_sha256,
                                        ])
    der_encoded_proof_of_binding_cbor = DerOctetString(proof_of_binding_cbor).encode()
    cert = (x509.CertificateBuilder()
            .subject_name(subject)
            .issuer_name(issuer)
            .public_key(auth_key_public)
            .serial_number(1)
            .not_valid_before(datetime.datetime.utcnow())
            # Our certificate will be valid for 365 days
            .not_valid_after(datetime.datetime.utcnow() + datetime.timedelta(days=365))
            .add_extension(
                x509.UnrecognizedExtension(x509.ObjectIdentifier("1.3.6.1.4.1.11129.2.1.26"),
                                           der_encoded_proof_of_binding_cbor),
                critical=False)
            ).sign(credential_key_private, hashes.SHA256())
    cert_bytes = cert.public_bytes(serialization.Encoding.DER)
    return cert_bytes


COSE_KEY_KTY = 1
COSE_KEY_TYPE_EC2 = 2
COSE_KEY_EC2_CRV = -1
COSE_KEY_EC2_X = -2
COSE_KEY_EC2_Y = -3
COSE_KEY_EC2_CRV_P256 = 1

def to_cose_key(public_key):
    numbers = public_key.public_numbers()
    cose_key = {COSE_KEY_KTY: COSE_KEY_TYPE_EC2,
                COSE_KEY_EC2_CRV: COSE_KEY_EC2_CRV_P256,
                COSE_KEY_EC2_X: numbers.x.to_bytes(length=32, byteorder="big"),
                COSE_KEY_EC2_Y: numbers.y.to_bytes(length=32, byteorder="big"),
                }
    return cose_key

def from_cose_key(cose_key):
    key_type = cose_key.get(COSE_KEY_KTY)
    if key_type != COSE_KEY_TYPE_EC2:
        raise ValueError("Expected COSE_KEY_TYPE_EC2")
    curve = cose_key.get(COSE_KEY_EC2_CRV)
    if curve != COSE_KEY_EC2_CRV_P256:
        raise ValueError("Expected COSE_KEY_EC2_CRV_P256")
    x_bytes = cose_key.get(COSE_KEY_EC2_X)
    if not isinstance(x_bytes, bytes):
        raise ValueError("Expected bytes at COSE_KEY_EC2_X")
    x = int.from_bytes(bytes=x_bytes, byteorder="big")
    y_bytes = cose_key.get(COSE_KEY_EC2_Y)
    if not isinstance(y_bytes, bytes):
        raise ValueError("Expected bytes at COSE_KEY_EC2_Y")
    y = int.from_bytes(bytes=y_bytes, byteorder="big")
    public_key_numbers = ec.EllipticCurvePublicNumbers(x, y, ec.SECP256R1())
    return public_key_numbers.public_key()

def cose_sign1_sign(private_key, data, data_is_detached = False):
    unprotected_headers = {}
    protected_headers = {COSE_LABEL_ALG: COSE_ALG_ECDSA_256}
    encoded_protected_headers = cbor.dumps(protected_headers)
    external_aad = b""
    to_be_signed = cbor.dumps(["Signature1",
                               encoded_protected_headers,
                               external_aad,
                               data])
    der_signature = private_key.sign(to_be_signed,
                                     ec.ECDSA(hashes.SHA256()))
    #print("sign:   der_signature: %s" % binascii.hexlify(der_signature))
    parsed_der_signature = Sequence.load(der_signature)
    r = parsed_der_signature[0]
    s = parsed_der_signature[1]
    #print("sign:   r=%d s=%d" % (r, s))
    encoded_r_and_s = (int(r).to_bytes(length=32, byteorder="big") +
                       int(s).to_bytes(length=32, byteorder="big"))
    cose_sign1 = [encoded_protected_headers,
                  unprotected_headers,
                  bytes(data) if not data_is_detached else b"",
                  encoded_r_and_s]
    return cose_sign1
    #print("cose_sign1: %s" % binascii.hexlify(cose_sign1))

def cose_sign1_get_data(signature):
    if len(signature) != 4:
        raise ValueError("Expected four elements, had %d" % len(signature))
    if type(signature[2]) != bytes:
        raise ValueError("Wrong type for third element")
    return signature[2]


def cose_sign1_verify(public_key, signature, data):
    if len(signature) != 4:
        raise ValueError("Expected four elements, had %d" % len(signature))
    if type(signature[2]) != bytes:
        raise ValueError("Wrong type for third element")
    if type(signature[3]) != bytes:
        raise ValueError("Wrong type for fourth element")
    encoded_r_and_s = signature[3]
    if len(encoded_r_and_s) != 64:
        raise ValueError("Expected 64 bytes for signature, was %d" % len(encoded_r_and_s))
    r = int.from_bytes(bytes=encoded_r_and_s[0:32], byteorder="big")
    s = int.from_bytes(bytes=encoded_r_and_s[32:64], byteorder="big")
    #print("verify: r=%d s=%d" % (r, s))
    seq = SequenceOf(spec=Integer)
    seq.append(r)
    seq.append(s)
    der_signature = seq.dump()
    #print("verify: der_signature: %s" % binascii.hexlify(der_signature))
    unprotected_headers = {}
    protected_headers = {COSE_LABEL_ALG: COSE_ALG_ECDSA_256}
    encoded_protected_headers = cbor.dumps(protected_headers)
    external_aad = b""
    to_be_signed = cbor.dumps(["Signature1",
                               encoded_protected_headers,
                               external_aad,
                               data])

    try:
        public_key.verify(der_signature,
                          to_be_signed,
                          ec.ECDSA(hashes.SHA256()))
    except InvalidSignature:
        return False
    return True

def auth_key_cert_validate(cert_bytes,
                           credential_key_public,
                           proof_of_provisioning_sha256):
    cert = x509.load_der_x509_certificate(cert_bytes)
    # First, verify this was signed by CredentialKey
    #
    try:
        credential_key_public.verify(
            cert.signature,
            cert.tbs_certificate_bytes,
            ec.ECDSA(hashes.SHA256()))
    except InvalidSignature:
        return False
    # Second, inspect the values in the certificate
    #
    if cert.subject.rfc4514_string() != "CN=Android Identity Credential Authentication Key":
        return False
    pob_extension = cert.extensions.get_extension_for_oid(
        x509.ObjectIdentifier("1.3.6.1.4.1.11129.2.1.26"))
    if not pob_extension:
        # TODO: older versions of IC (prior to v202101) do not have ProofOfBinding.
        # Support those as well
        return False
    der_encoded_proof_of_binding = pob_extension.value.value
    dos = DerOctetString()
    dos.decode(der_encoded_proof_of_binding)
    encoded_proof_of_binding = dos.payload
    proof_of_binding = cbor.loads(encoded_proof_of_binding)
    if (len(proof_of_binding) < 2 or
        proof_of_binding[0] != "ProofOfBinding" or
        proof_of_binding[1] != proof_of_provisioning_sha256):
        return False
    # TODO: add other checks
    return True

def credential_key_cert_chain_validate(cert_chain_bytes, challenge):
    # TODO: see test.py
    #cert = x509.load_der_x509_certificate(cert_chain_bytes)
    # TODO: validate it's a chain of certs with a well-known root certificate
    #
    # TODO: extract Android Attestation Extension and check challenge is as passed
    #
    return True

def cert_chain_get_public_key(cert_chain):
    cert = x509.load_der_x509_certificate(cert_chain)
    return cert.public_key()

def generate_static_auth_data_for_auth_key(doc_type, name_spaces, credential_key, auth_key, issuer_key):
    # First, randomize the order the digest IDs are used
    num_elems = 0
    for ns_name in name_spaces.keys():
        num_elems += len(name_spaces[ns_name])
        digest_ids = list(range(num_elems))
        random.shuffle(digest_ids)

    # Along with value_digests for the MSO, generate digest_id_mapping which is sent
    # to the mDL along with the MSO
    #
    #   DigestIdMapping = {
    #     + NameSpace => DigestIdMappingPerNamespace
    #   }
    #
    #   DigestIdMappingPerNamespace = {
    #     + Name => DigestIdMappingPerElement
    #   }
    #
    #   DigestIdMappingPerElement = [
    #     DigestID,
    #     Random,
    #   ]
    #
    #   DigestID = uint
    #   Random = bstr
    #
    value_digests = {}
    digest_id_mapping = {}
    digest_id_index = 0
    for ns_name in name_spaces.keys():
        value_digests_for_ns = {}
        digest_id_mapping_for_ns = {}
        for elem in name_spaces[ns_name]:
            digest_id = digest_ids[digest_id_index]
            elem_random = bytes(random.randint(0, 255) for i in range(32))
            digest_id_mapping_for_ns[elem["name"]] = [digest_id, elem_random]
            digest_id_index += 1
            # Calculate the digest
            encoded_issuer_signed_item = cbor.dumps({
                "digestID": digest_id,
                "random": elem_random,
                "elementIdentifier": elem["name"],
                "elementValue": elem["value"]
            })
            digest = hashlib.sha256(encoded_issuer_signed_item).digest()
            value_digests_for_ns[digest_id] = digest
            value_digests[ns_name] = value_digests_for_ns
            digest_id_mapping[ns_name] = digest_id_mapping_for_ns

    now = datetime.datetime.now(datetime.timezone.utc)
    signed_time = now
    valid_from = now
    # MSO is valid for one year
    valid_to = now + datetime.timedelta(days=365)
    validity_info = {
        "signed": cbor.Tag(6, signed_time.isoformat()),
        "validFrom": cbor.Tag(6, valid_from.isoformat()),
        "validTo": cbor.Tag(6, valid_to.isoformat()),
        # expectedUpdate not set
    }

    device_key_info = {
        "deviceKey" : to_cose_key(credential_key),
        # keyAuthorizations and keyInfo not set
    }

    mobile_security_object = {
        "version" : "1",
        "digestAlgorithm" : "SHA-256",
        "valueDigests" : value_digests,
        "deviceKeyInfo" : device_key_info,
        "docType" : doc_type,
        "validityInfo" : validity_info,
    }

    mobile_security_object_bytes = cbor.dumps(mobile_security_object)
    signature_with_mso = cose_sign1_sign(issuer_key, mobile_security_object_bytes)

    static_auth_data = {
        "digestIdMapping": digest_id_mapping,
        "issuerAuth" : signature_with_mso,
    }
    encoded_static_auth_data = cbor.dumps(static_auth_data)
    return encoded_static_auth_data


def render_name_spaces_in_html(name_spaces_cbor):
    name_spaces = cbor.loads(name_spaces_cbor)
    ret = ""
    for ns_name in name_spaces.keys():
        if len(ret) > 0:
            ret += "<p>"
        ret += "<b>%s</b>:" % ns_name
        for elem in name_spaces[ns_name]:
            name = elem["name"]
            value = elem["value"]
            if isinstance(value, bytes):
                value_as_str = "<i>bstr of size %d</i>" % len(value)
            else:
                value_as_str = str(value)
            ret += "<br>%s: %s" % (name, value_as_str)
            print("name %s value %s" % (name, value_as_str))
    return ret

def setup_test_data(database):
    db = database.get_sqlite3()
    c = db.cursor()

    # Erika Mustermann
    #
    mdl_acp_cbor = cbor.dumps(
        [
            {
                "id": 0,
                "userAuthenticationRequired": True,
                "timeoutMillis": 1000
            }
        ],)
    with open("erika_portrait.jpg", "rb") as f:
        portrait = f.read()
    mdl_ns_cbor = cbor.dumps(
        {
            "org.iso.18013.5.1": [
                { "name": "family_name", "value": "Mustermann", "accessControlProfiles": [0] },
                { "name": "given_name", "value": "Erika", "accessControlProfiles": [0] },
                { "name": "portrait", "value": portrait, "accessControlProfiles": [0] }
            ],
            "org.aamva.18013.5.1": [
                { "name": "real_id", "value": True, "accessControlProfiles": [0] }
            ]
        })
    c.execute("INSERT INTO persons (person_id, name, portrait) VALUES (10, 'Erika Mustermann', ?);",
              [portrait])
    c.execute("INSERT INTO documents (document_id, person_id, doc_type, access_control_profiles, name_spaces) "
              "VALUES (11, 10, 'org.iso.18013.5.1.mDL', ?, ?);",
              (mdl_acp_cbor, mdl_ns_cbor))
    c.execute("INSERT INTO issued_documents (issued_document_id, document_id, provisioning_code) "
              "VALUES (12, 11, '1001');")

    # John Doe
    #
    with open("john_doe_portrait.jpg", "rb") as f:
        portrait = f.read()
    mdl_ns_cbor = cbor.dumps(
        {
            "org.iso.18013.5.1": [
                { "name": "family_name", "value": "Doe", "accessControlProfiles": [0] },
                { "name": "given_name", "value": "John", "accessControlProfiles": [0] },
                { "name": "portrait", "value": portrait, "accessControlProfiles": [0] }
            ],
            "org.aamva.18013.5.1": [
                { "name": "real_id", "value": True, "accessControlProfiles": [0] }
            ]
        })
    c.execute("INSERT INTO persons (person_id, name, portrait) VALUES (20, 'John Doe', ?);",
              [portrait])
    c.execute("INSERT INTO documents (document_id, person_id, doc_type, access_control_profiles, name_spaces) "
              "VALUES (21, 20, 'org.iso.18013.5.1.mDL', ?, ?);",
              (mdl_acp_cbor, mdl_ns_cbor))
    c.execute("INSERT INTO issued_documents (issued_document_id, document_id, provisioning_code) "
              "VALUES (22, 21, '2001');")

    db.commit()
