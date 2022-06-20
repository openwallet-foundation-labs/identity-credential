
# What is it?

This sub-directory contains the Identity Credential provisioning protocol. It is
currently work-in-progress but the goal is for this to be compatible with the
upcoming ISO/IEC TS-23220-3 document.

In addition to the protocol, a reference implementation is provided. Right now
it's very rough around the corners and should only be used for testing and only
on closed networks. No attempt has been made to make this implemention secure or
privacy-focused.

Both the protocol definition and the reference implementation will likely move
to a separate repo at some point. This is all currently a work in progress, use
at your own risk etc. etc.

# How to run the reference server
It is possible to run the server directly and via Docker.

## Directly

Simply run `./mdl-ref-server.py` to get started. The first time you run, you
also want to pass the `--reset-with-testdata` option to create some test
data. By default the server listens at port 18013 and uses plain HTTP (HTTPS
support will be added later).

The server comes with an admin interface at the `/admin` URI and the
provisioning protocol itself is rooted under the `/mdlServer` URI.

## Via Docker

Make sure that Docker and Docker-compose have been installed. Simply open a terminal window in this folder and run: `[sudo] docker-compose up`

# Data Model

The *System of Record* uses a database with the following tables:

- `persons`
  - Primary key is a person identification number
  - Has `name` and `portrait` fields.
  - Is associated with zero or more `documents` rows.
    - example: Erika Mustermann has one Driving License document and
    - two Vehichle Registration documents.

- `documents`
  - Primary key is document-specific identifier e.g. Driving License number
  - A row represents a document in a more abstract sense.
  - Has `doc_type`, `access_control_profiles`, `name_spaces` columns
    which describe the document in detail.
  - Is associated with zero or more `issued_documents` rows.
  - Has `data_timestamp` field with time the data was last updated

- `issued_documents`
  - Primary key is auto-increment.
  - Represents a document which can be issued to one or more *specific*
    devices through a `provisioning_code` which is transmitted out-of-band
    to the device as per ISO/IEC TS 23220-3.
    - In the future will also have fields governing *how* the
      provisioning code can be used, for example what kind of
      user proofing is needed, whether multiple devices can use
      the same provisioning code, whether it can be used for backup/
      restore, and so on.
  - Is associated with zero or more `configured_documents` rows.

- `configured_documents`
  - Primary key is auto-increment.
  - Represent a document configured on a particular device.
  - Has `last_updated_timestamp` field with time of last update of the document
  - Has `data_timestamp` copy of the data_timestamp field from the documents at the time of the document was last provisioned or updated
  - Has `status` for now will only have a value null or `TO_DELETE` to indicate that the mdl app should delete this document in the update check flow
  - Has `credential_key_x509_cert_chain` field which uniquely
    identifies the device
  - Has `proof_of_provisioning` which represents which
    instance of the document was last sent to the device
  - Is associated with zero or more `endorsed_authentication_keys` rows.

- `endorsed_authentication_keys`
  - Primary key is auto-increment.
  - Represents an MSO used for document presentation.
  - Has `authentication_key_x509_cert` which uniquely identifies the
    authentication key from the device that was endorsed, including
    the `ProofOfBinding` extension.
  - Has `static_auth_datas` which contains the MSO and digest_id mapping.
  - Has `generated_at_timestamp` and `expires_at_timestamp` which describe
    the life time of the MSO

# Protocol

## Provisioning

- Request:  `StartProvisioning`
  - provisioningCode: tstr
- Response: `ReadyToProvision`
    - eSessionId: bstr

- Request:  `com.android.identity_credential.StartProvisioning`
  - eSessionId: bstr
- Response: `com.android.identity_credential.ProvisioningResponse`
  - challenge: bstr
  - eSessionId: bstr

- Request:  `com.android.identity_credential.SetCertificateChain`
  - eSessionId: bstr
  - credentialKeyCertificateChain: bstr
    - contains a chain of X509 certificates
    - (TODO: details)
- Response: `com.android.identity_credential.DataToProvision`
  - challenge: bstr
  - eSessionId: bstr
  - accessControlProfiles: AccessControlProfiles
  - nameSpaces: Namespaces

where

    AccessControlProfiles = [ * AccessControlProfile ]

    AccessControlProfile = {
        "id": uint,
        ? "readerCertificate" : bstr,
        ? (
            "userAuthenticationRequired" : bool,
            "timeoutMillis" : uint,
        )
    }

    Namespaces = {
        * Namespace => [ + Entry ]
    },

    Namespace = tstr

    Entry = {
        "name" : tstr,
        "value" : any,
        "accessControlProfiles" : [ * uint ],
    }

- Request:  `com.android.identity_credential.SetProofOfProvisioning`
  - eSessionId: bstr
  - proofOfProvisioningSignature: COSE_Sign1
    - payload in signature set are set to bytes of ProofOfProvisioning
    - signature is made with CredentialKey
- Response: `EndSessionMessage`
  - eSessionId: bstr
  - reason: "Success"

## Auth Key Refresh

- Request:  `com.android.identity_credential.CertifyAuthKeys`
  - credentialKey: COSE_Key
- Response: `com.android.identity_credential.CertifyAuthKeysProveOwnership`
  - eSessionId: bstr
  - challenge: bstr

- Request:  `com.android.identity_credential.CertifyAuthKeysProveOwnershipResponse`
  - eSessionId: bstr
  - proofOfOwnershipSignature: COSE_Sign1
    - payload in signature set are set to bytes of ProofOfOwnership
    - signature is made with CredentialKey
- Response: `com.android.identity_credential.CertifyAuthKeysReady`
  - eSessionId: bstr

- Request:  `com.android.identity_credential.CertifyAuthKeysSendCerts`
  - eSessionId: bstr
  - authKeyCerts: AuthKeyCerts
    - array of X.509 certificates, one for each auth key
    - Each X.509 certificate is signed by CredentialKey
    - Each X.509 certificate contains an extension at OID 1.3.6.1.4.1.11129.2.1.26
      with ProofOfBinding CBOR
- Response: `com.android.identity_credential.CertifyAuthKeysResponse`
  - eSessionId: bstr
  - staticAuthDatas: StaticAuthDatas
    - array of StaticAuthData, one for each auth key

- Request:  `RequestEndMessage`
  - eSessionId: bstr
- Response: `EndSessionMessage`
  - eSessionId: bstr
  - reason: "Success"

where

    AuthKeyCerts = [ AuthKeyCert ]
    AuthKeyCert = bstr

    StaticAuthDatas = [ StaticAuthData ]

    StaticAuthData = {
        "digestIdMapping": DigestIdMapping,
        "issuerAuth" : IssuerAuth
    }

    DigestIdMapping = {
        NameSpace => [ + IssuerSignedItemWithoutValueBytes ]
    }

    NameSpace = tstr
    DataElementIdentifier = tstr
    IssuerAuth = COSE_Sign1 ; The payload is MobileSecurityObjectBytes

    IssuerSignedItemWithoutValueBytes = #6.24(bstr .cbor IssuerSignedItemWithoutValue)

    IssuerSignedItemWithoutValue = {
      "digestID" : uint,                           ; Digest ID for issuer data auth
      "random" : bstr,                             ; Random value for issuer data auth
      "elementIdentifier" : DataElementIdentifier, ; Data element identifier
      "elementValue" : nil                         ; Data element value is UNSET
    }

Note that IsserSignedItemWithoutValue is similar to IssuerSignedItem
as defined in ISO 18013-5 clause 8.3.2.1.2.2 "Device retrieval mdoc
response" except that the value for the "elementValue" key is set to
nil instead of the actual value. At presentation time the value will
be substituted in after retrieving it via the Identity Credential API.

## Update Check

- Request:  `com.android.identity_credential.UpdateCredential`
  - credentialKey: COSE_Key
- Response: `com.android.identity_credential.UpdateCredentialProveOwnership`
  - eSessionId: bstr
  - challenge: bstr

- Request:  `com.android.identity_credential.UpdateCredentialProveOwnershipResponse`
  - eSessionId: bstr
  - proofOfOwnershipSignature: COSE_Sign1
    - payload in signature set are set to bytes of ProofOfOwnership
    - signature is made with CredentialKey
- Response: `com.android.identity_credential.UpdateCredentialResponse`
  - eSessionId: bstr
  - updateCredentialResult: tstr ("delete"|"no_update"|"update")
    - "delete" the mdl app should start the delete flow
    - "no_update" no further messages need to be send
    - "update" continue update check flow with UpdateCredentialGetDataToUpdate

- Request: `com.android.identity_credential.UpdateCredentialGetDataToUpdate`
  - eSessionId: bstr
- Response: `com.android.identity_credential.UpdateCredentialDataToProvisionMessage`
  - eSessionId: bstr
  - accessControlProfiles: AccessControlProfiles
  - nameSpaces: Namespaces

where

    AccessControlProfiles = [ * AccessControlProfile ]

    AccessControlProfile = {
        "id": uint,
        ? "readerCertificate" : bstr,
        ? (
            "userAuthenticationRequired" : bool,
            "timeoutMillis" : uint,
        )
    }

    Namespaces = {
        * Namespace => [ + Entry ]
    },

    Namespace = tstr

    Entry = {
        "name" : tstr,
        "value" : any,
        "accessControlProfiles" : [ * uint ],
    }

- Request:  `com.android.identity_credential.UpdateCredentialSetProofOfProvisioning`
  - eSessionId: bstr
  - proofOfProvisioningSignature: COSE_Sign1
    - payload in signature set are set to bytes of ProofOfProvisioning
    - signature is made with CredentialKey
- Response: `EndSessionMessage`
  - eSessionId: bstr
  - reason: "Success"
  

## Delete

- Request:  `com.android.identity_credential.DeleteCredential`
  - credentialKey: COSE_Key
- Response: `com.android.identity_credential.DeleteCredentialProveOwnership`
  - eSessionId: bstr
  - challenge: bstr

- Request:  `com.android.identity_credential.DeleteCredentialProveOwnershipResponse`
  - eSessionId: bstr
  - proofOfOwnershipSignature: COSE_Sign1
    - payload in signature set are set to bytes of ProofOfOwnership
    - signature is made with CredentialKey
- Response: `com.android.identity_credential.DeleteCredentialReadyForDeletion`
  - eSessionId: bstr
  - challenge: bstr

- Request:  `com.android.identity_credential.DeleteCredentialDeleted`
  - eSessionId: bstr
  - proofOfDeletionSignature: COSE_Sign1
    - payload in signature set are set to bytes of proofOfDeletion
    - signature is made with CredentialKey
- Response: `EndSessionMessage`
  - eSessionId: bstr
  - reason: "Success"
