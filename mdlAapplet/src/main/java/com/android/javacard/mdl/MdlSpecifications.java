/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.javacard.mdl;

import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Util;
import javacard.security.KeyBuilder;

/**
 * This class implements constants related to ISO 18013-5 specifications and related provisioning
 * specifications. It also implements the decoding and validation logic related to various requests.
 */
public class MdlSpecifications {

  public static final short CBOR_MAC_TAG_SIZE = 32;

  // CBOR constants - TODO remove the following and use CBORBase instead
  public static final short CBOR_UINT8_LENGTH = 0x18;
  public static final short CBOR_UINT16_LENGTH = 0x19;
  public static final short CBOR_ANY = 0x0100;
  public static final short CBOR_BOOL = 0xF0;
  public static final short CBOR_NIL = 0xF6;
  public static final short CBOR_TRUE = 0xF5;
  public static final short CBOR_FALSE = 0xF4;
  public static final short CBOR_MAP = 0xA0;
  public static final short CBOR_ARRAY = 0x80;
  public static final short CBOR_TEXT_STR = 0x60;
  public static final short CBOR_NEG_INT = 0x20;
  public static final short CBOR_UINT = 0;
  public static final short CBOR_SEMANTIC_TAG = 0xC0;
  public static final short CBOR_SEMANTIC_TAG_ENCODED_CBOR = 24;
  public static final short CBOR_MAX_256_BYTES = 0x18; // 24
  public static final short CBOR_MAX_64K_BYTES = 0x19; // 25
  public static final byte ES384 = 34;
  public static final byte ES256 = 6;
  public static final byte ES512 = 35;

  // HAL Commands related keys CBOR Uint < 23
  public static final byte HAL_KEY_OFFSET = 0;
  public static final byte KEY_ENC_DATA = HAL_KEY_OFFSET;
  public static final byte KEY_CERT = (byte) (HAL_KEY_OFFSET + 1);
  public static final short CBOR_MAJOR_TYPE_MASK = 0xE0;
  public static final short CBOR_BINARY_STR = 0x40;
  public static final byte KEY_HAL_CMD_DOC_SLOT = (byte) (HAL_KEY_OFFSET + 2);
  public static final byte KEY_TEST_CREDENTIAL = (byte) (HAL_KEY_OFFSET + 3);
  public static final byte KEY_CRED_CERT_CHALLENGE = (byte) (HAL_KEY_OFFSET + 4);
  public static final byte KEY_HAL_CMD_DOC_TYPE = (byte) (HAL_KEY_OFFSET + 5);
  public static final byte KEY_HAL_CMD_MDOC_NUM_SIGN_KEYS = (byte) (HAL_KEY_OFFSET + 6);
  public static final byte KEY_HAL_CMD_MDOC_USERS_PER_SIGN_KEY = (byte) (HAL_KEY_OFFSET + 7);
  public static final byte KEY_HAL_CMD_MDOC_VALID_TIME = (byte) (HAL_KEY_OFFSET + 8);
  public static final byte KEY_OPERATION = (byte) (HAL_KEY_OFFSET + 9);
  public static final byte KEY_CRED_DATA = (byte) (HAL_KEY_OFFSET + 10);
  public static final byte KEY_READER_ACCESS = (byte) (HAL_KEY_OFFSET + 11);
  public static final byte KEY_KEY_PARAMS = (byte) (HAL_KEY_OFFSET + 12);
  public static final byte KEY_ATTEST_KEY_PARAMS = (byte) (HAL_KEY_OFFSET + 13);
  public static final byte KEY_ATTEST_KEY_BLOB = (byte) (HAL_KEY_OFFSET + 14);

  // main name space elements
  // Standard CBOR type indexes
  public static final short IND_KEY_OFFSET = 16;
  public static final short IND_UINT_TYPE = IND_KEY_OFFSET;
  public static final short IND_TXT_STR_TYPE = (short) (IND_KEY_OFFSET + 1);
  public static final short IND_BINARY_STR_TYPE = (short) (IND_KEY_OFFSET + 2);
  public static final short IND_ARRAY_TYPE = (short) (IND_KEY_OFFSET + 3);
  public static final short IND_SESSION_ESTABLISHMENT = (short) (IND_KEY_OFFSET + 4);
  public static final short IND_SESSION_DATA = (short) (IND_KEY_OFFSET + 5);
  public static final short IND_MDOC = (short) (IND_KEY_OFFSET + 6);
  public static final short IND_STATIC_AUTH_DATA = (short) (IND_KEY_OFFSET + 7);
  public static final short IND_ISSUER_SIGNED_ITEM = (short) (IND_KEY_OFFSET + 8);
  public static final short IND_CRED_DATA_DIGEST_MAPPING = (short) (IND_KEY_OFFSET + 9);
  public static final short IND_MDOC_HAL_CMD = (short) (IND_KEY_OFFSET + 10);

  // key strings and uint indexes
  public static final short MDL_KEY_OFFSET = 32;
  public static final short KEY_EREADER_KEY = MDL_KEY_OFFSET;
  public static final short KEY_DEVICE_REQUEST = (short) (MDL_KEY_OFFSET + 1);
  public static final short KEY_STATUS = (short) (MDL_KEY_OFFSET + 2);
  public static final short KEY_VERSION = (short) (MDL_KEY_OFFSET + 3);
  public static final short KEY_DOC_REQUESTS = (short) (MDL_KEY_OFFSET + 4);
  public static final short KEY_NAME_SPACES = (short) (MDL_KEY_OFFSET + 13);
  public static final short KEY_DEVICE_AUTH = (short) (MDL_KEY_OFFSET + 14);
  public static final short KEY_COSE_SIGN_ALG = (short) (MDL_KEY_OFFSET + 15);
  public static final short KEY_ITEMS_REQUEST = (short) (MDL_KEY_OFFSET + 16);
  public static final short KEY_READER_AUTH = (short) (MDL_KEY_OFFSET + 17);
  public static final short KEY_DEVICE_SIGNATURE = (short) (MDL_KEY_OFFSET + 18);
  public static final short KEY_DEVICE_MAC = (short) (MDL_KEY_OFFSET + 19);
  public static final short KEY_REQUEST_INFO = (short) (MDL_KEY_OFFSET + 20);
  public static final short COSE_KEY_KTY_VAL_EC2 = 2;
  public static final short COSE_KEY_CRV_VAL_EC2_P256 = 1;
  public static final short MAX_COSE_KEY_SIZE = 128;
  public static final short MAX_SESSION_DATA_SIZE = 256;
  public static final short COSE_LABEL_ALG = 1;
  public static final short COSE_LABEL_X5CHAIN = 33;

  // From RFC 8152: Table 5: ECDSA Algorithm Values
  public static final short COSE_ALG_ECDSA_256 = -7;
  public static final short COSE_ALG_ECDSA_384 = -35;
  public static final short COSE_ALG_ECDSA_512 = -36;
  public static final short COSE_ALG_HMAC_256_256 = 5;
  // Structures and indexes used for the decoding
  public static final byte STRUCT_ROW_SIZE = 2;
  public static final byte STRUCT_KEY_OFFSET = 0;
  public static final byte STRUCT_VAL_OFFSET = 1;
  // Other constants
  public static final short MAX_SESSION_TRANSCRIPT_SIZE = 512;
  public static final byte EC_P256_COSE_KEY_SIZE = (2 * (KeyBuilder.LENGTH_EC_FP_256 / 8) + 1) + 16;
  public static final short MDL_ERR_NOT_FOUND = 10;

  // Mdl Name space
  // "org.iso.18013.5.1" - nameSpaces
  public final byte[] mdlNameSpace = {
    0x71, 0x6F, 0x72, 0x67, 0x2E, 0x69, 0x73, 0x6F, 0x2E, 0x31, 0x38, 0x30, 0x31, 0x33, 0x2E, 0x35,
    0x2E, 0x31
  };
  // structures associated to main namespace elements
  public final short[] STRUCT_SESSION_EST = {
    KEY_EREADER_KEY, CBOR_SEMANTIC_TAG,
    KEY_DEVICE_REQUEST, CBOR_BINARY_STR,
  };
  public final short[] STRUCT_SESSION_DATA = {
    KEY_STATUS, CBOR_UINT,
    KEY_DEVICE_REQUEST, CBOR_BINARY_STR,
  };
  public final short[] STRUCT_DEVICE_REQ = {
    KEY_VERSION, CBOR_TEXT_STR,
    KEY_DOC_REQUESTS, CBOR_ARRAY,
  };
  public final short KEY_COSE_KTY = (short) (MDL_KEY_OFFSET + 5);
  public final short KEY_COSE_CRV = (short) (MDL_KEY_OFFSET + 6);
  public final short KEY_COSE_X_COORD = (short) (MDL_KEY_OFFSET + 7);
  public final short KEY_COSE_Y_COORD = (short) (MDL_KEY_OFFSET + 8);
  public final short[] STRUCT_COSE_KEY = {
    KEY_COSE_KTY, CBOR_UINT, // TODO key can also be text string
    KEY_COSE_CRV, CBOR_UINT, // TODO key can also be text string
    KEY_COSE_X_COORD, CBOR_BINARY_STR,
    KEY_COSE_Y_COORD, CBOR_BINARY_STR, // TODO key can also be boolean
  };
  public final short KEY_DOC_TYPE = (short) (MDL_KEY_OFFSET + 9);
  public final short KEY_ISSUER_SIGNED = (short) (MDL_KEY_OFFSET + 10);
  public final short KEY_DEVICE_SIGNED = (short) (MDL_KEY_OFFSET + 11);
  public final short KEY_ERRORS = (short) (MDL_KEY_OFFSET + 12);
  public final short[] STRUCT_MDOC = {
    KEY_DOC_TYPE, CBOR_TEXT_STR,
    KEY_ISSUER_SIGNED, CBOR_MAP, // TODO in future we may want to decode this
    KEY_DEVICE_SIGNED, CBOR_MAP, // TODO in future we may want to decode this recursively. For
    //  this we can use the key id to getStructure and recurse.
    KEY_ERRORS, CBOR_MAP,
  };
  public final short[] STRUCT_DEVICE_SIGNED = {
    KEY_NAME_SPACES, CBOR_SEMANTIC_TAG,
    KEY_DEVICE_AUTH, CBOR_NIL, // This is actually a map but for document being provisioned this
    // will always be nil
  };
  public final short[] STRUCT_MDOC_REQUEST = {
    KEY_ITEMS_REQUEST, CBOR_SEMANTIC_TAG,
    KEY_READER_AUTH, CBOR_ARRAY,
  };
  public final short[] STRUCT_DEVICE_AUTH = {
    KEY_DEVICE_SIGNATURE, CBOR_ARRAY,
    KEY_DEVICE_MAC, CBOR_ARRAY,
  };
  public final short[] STRUCT_ITEMS_REQUEST = {
    KEY_DOC_TYPE, CBOR_TEXT_STR,
    KEY_NAME_SPACES, CBOR_MAP,
    KEY_REQUEST_INFO, CBOR_MAP,
  };
  public final short KEY_DIGEST_MAPPING = (short) (MDL_KEY_OFFSET + 21);
  public final short KEY_DIGEST_ID = (short) (MDL_KEY_OFFSET + 22);
  public final short KEY_ELEM_ID = (short) (MDL_KEY_OFFSET + 23);
  public final short KEY_ELEM_VAL = (short) (MDL_KEY_OFFSET + 24);
  public final short KEY_ISSUER_AUTH = (short) (MDL_KEY_OFFSET + 25);

  /**
   * StaticAuthData = { "digestIdMapping": DigestIdMapping, "issuerAuth" : IssuerAuth }
   * DigestIdMapping = { NameSpace =&gt; [ + IssuerSignedItemBytes ] } ; Defined in ISO 18013-5 ;
   * NameSpace = String DataElementIdentifier = String DigestID = uint IssuerAuth = COSE_Sign1 ; The
   * payload is MobileSecurityObjectBytes IssuerSignedItemBytes = #6.24(bstr .cbor IssuerSignedItem)
   * IssuerSignedItem = { "digestID" : uint, ; Digest ID for issuer data auth "random" : bstr, ;
   * Random value for issuer data auth "elementIdentifier" : DataElementIdentifier, ; Data element
   * identifier "elementValue" : NULL ; Placeholder for Data element value }
   */
  public final short[] STRUCT_STATIC_AUTH_DATA = {
    KEY_DIGEST_MAPPING, CBOR_MAP,
    KEY_ISSUER_AUTH, CBOR_MAP, // Check whether this is an array
  };

  public final short KEY_RANDOM = (short) (MDL_KEY_OFFSET + 26);
  public final short[] STRUCT_ISSUER_SIGNED_ITEM = {
    KEY_DIGEST_ID, CBOR_UINT,
    KEY_RANDOM, CBOR_BINARY_STR,
    KEY_ELEM_ID, CBOR_TEXT_STR,
    KEY_ELEM_VAL, CBOR_ANY,
  };
  public final short KEY_ISSUER_NS = (short) (MDL_KEY_OFFSET + 27);

  /**
   * CredentialData = { "digestIdMapping": DigestIdMapping, "issuerAuth" :
   * IssuerAuth, "readerAccess" : ReaderAccess } DigestIdMapping = { NameSpace => [ +
   * IssuerSignedItemBytes ] } ReaderAccess = [ * COSE_Key ] DigestIdMapping = { NameSpace =&gt; [ +
   * IssuerSignedItemBytes ] } ; Defined in ISO 18013-5 ; NameSpace = String DataElementIdentifier =
   * String DigestID = uint IssuerAuth = COSE_Sign1 ; The payload is MobileSecurityObjectBytes
   * IssuerSignedItemBytes = #6.24(bstr .cbor IssuerSignedItem) IssuerSignedItem = { "digestID" :
   * uint, ; Digest ID for issuer data auth "random" : bstr, ; Random value for issuer data auth
   * "elementIdentifier" : DataElementIdentifier, ; Data element identifier "elementValue" : NULL ;
   * Placeholder for Data element value }
   */
  public final short[] STRUCT_CRED_DATA = {
    KEY_ISSUER_NS, CBOR_MAP,
    KEY_ISSUER_AUTH, CBOR_ARRAY,
    KEY_READER_ACCESS, CBOR_ARRAY,
  };

  // Fixed arrays of strings - along with text string header
  // "eReaderKey"
  public final byte[] session_eReaderKey = {
    0x6A, 0x65, 0x52, 0x65, 0x61, 0x64, 0x65, 0x72, 0x4b, 0x65, 0x79
  };
  // "status"
  public final byte[] status = {0x66, 0x73, 0x74, 0x61, 0x74, 0x75, 0x73};
  // "version"
  public final byte[] version = {0x67, 0x76, 0x65, 0x72, 0x73, 0x69, 0x6f, 0x6e};
  // "data"
  public final byte[] session_data = {0x64, 0x64, 0x61, 0x74, 0x61};
  // "docRequests"
  public final byte[] docRequests = {
    0x6B, 0x64, 0x6f, 0x63, 0x52, 0x65, 0x71, 0x75, 0x65, 0x73, 0x74, 0x73
  };
  // "docType"
  public final byte[] docType = {0x67, 0x64, 0x6f, 0x63, 0x54, 0x79, 0x70, 0x65};
  // "issuerSigned"
  public final byte[] issuerSigned = {
    0x6C, 0x69, 0x73, 0x73, 0x75, 0x65, 0x72, 0x53, 0x69, 0x67, 0x6e, 0x65, 0x64
  };
  // "issuerAuth"
  public final byte[] issuerAuth = {
    0x6A, 0x69, 0x73, 0x73, 0x75, 0x65, 0x72, 0x41, 0x75, 0x74, 0x68
  };
  // "deviceSigned"
  public final byte[] deviceSigned = {
    0x6C, 0x64, 0x65, 0x76, 0x69, 0x63, 0x65, 0x53, 0x69, 0x67, 0x6e, 0x65, 0x64
  };
  // "errors"
  public final byte[] errors = {0x66, 0x65, 0x72, 0x72, 0x6F, 0x72, 0x73};
  // "nameSpaces"
  public final byte[] nameSpaces = {
    0x6A, 0x6e, 0x61, 0x6d, 0x65, 0x53, 0x70, 0x61, 0x63, 0x65, 0x73
  };
  // "deviceAuth"
  public final byte[] deviceAuth = {
    0x6A, 0x64, 0x65, 0x76, 0x69, 0x63, 0x65, 0x41, 0x75, 0x74, 0x68
  };
  // "readerAuth"
  public final byte[] readerAuth = {
    0x6A, 0x72, 0x65, 0x61, 0x64, 0x65, 0x72, 0x41, 0x75, 0x74, 0x68
  };
  // "itemsRequest"
  public final byte[] itemsRequest = {
    0x6C, 0x69, 0x74, 0x65, 0x6d, 0x73, 0x52, 0x65, 0x71, 0x75, 0x65, 0x73, 0x74
  };
  // "requestInfo"
  public final byte[] requestInfo = {
    0x6B, 0x72, 0x65, 0x71, 0x75, 0x65, 0x73, 0x74, 0x49, 0x6e, 0x66, 0x6f
  };
  // "deviceSignature"
  public final byte[] deviceSignature = {
    0x6F, 0x64, 0x65, 0x76, 0x69, 0x63, 0x65, 0x53, 0x69, 0x67, 0x6e, 0x61, 0x74, 0x75, 0x72, 0x65
  };
  // "deviceMac"
  public final byte[] deviceMac = {0x69, 0x64, 0x65, 0x76, 0x69, 0x63, 0x65, 0x4d, 0x61, 0x63};
  // "ES256"
  public final byte[] es256 = {0x65, 0x45, 0x53, 0x32, 0x35, 0x36};
  // "DeviceAuthentication"
  public final byte[] deviceAuthentication = {
    0x74, 0x44, 0x65, 0x76, 0x69, 0x63, 0x65, 0x41, 0x75, 0x74, 0x68, 0x65, 0x6e, 0x74, 0x69, 0x63,
    0x61, 0x74, 0x69, 0x6f, 0x6e
  };
  // "ReaderAuthentication"
  public final byte[] readerAuthentication = {
    0x74, 0x52, 0x65, 0x61, 0x64, 0x65, 0x72, 0x41, 0x75, 0x74, 0x68, 0x65, 0x6E, 0x74, 0x69, 0x63,
    0x61, 0x74, 0x69, 0x6F, 0x6E
  };
  // "digestIdMapping"
  public final byte[] digestIdMapping = {
    0x6F, 0x64, 0x69, 0x67, 0x65, 0x73, 0x74, 0x49, 0x64, 0x4d, 0x61, 0x70, 0x70, 0x69, 0x6e, 0x67
  };
  // "issuerNameSpaces"
  public final byte[] issuerNameSpaces = {
    0x70, 0x69, 0x73, 0x73, 0x75, 0x65, 0x72, 0x4e, 0x61, 0x6d, 0x65, 0x53, 0x70, 0x61, 0x63, 0x65,
    0x73,
  };
  // "digestID"
  public final byte[] digestID = {0x68, 0x64, 0x69, 0x67, 0x65, 0x73, 0x74, 0x49, 0x44};
  // "random"
  public final byte[] random = {0x66, 0x72, 0x61, 0x6E, 0x64, 0x6F, 0x6D};
  // "elementIdentifier"
  public final byte[] elementIdentifier = {
    0x71, 0x65, 0x6C, 0x65, 0x6D, 0x65, 0x6E, 0x74, 0x49, 0x64, 0x65, 0x6E, 0x74, 0x69, 0x66, 0x69,
    0x65, 0x72
  };
  // "elementValue"
  public final byte[] elementValue = {
    0x6C, 0x65, 0x6C, 0x65, 0x6D, 0x65, 0x6E, 0x74, 0x56, 0x61, 0x6C, 0x75, 0x65,
  };
  // Following are used in HKDF key derivation - so they are not text strings
  // "SKDevice"
  public final byte[] deviceSecretInfo = {0x53, 0x4b, 0x44, 0x65, 0x76, 0x69, 0x63, 0x65};
  // "SKReader"
  public final byte[] readerSecretInfo = {0x53, 0x4b, 0x52, 0x65, 0x61, 0x64, 0x65, 0x72};
  // "EMacKey"
  public final byte[] eMacKey = {0x45, 0x4d, 0x61, 0x63, 0x4b, 0x65, 0x79};
  // "readerAccess"
  public final byte[] readerAccess = {
    0x6C, 0x72, 0x65, 0x61, 0x64, 0x65, 0x72, 0x41, 0x63, 0x63, 0x65, 0x73, 0x73
  };
  public final byte KEY_MDL_NAMESPACE = 1;
  public final short[] NAMESPACES = {
    KEY_MDL_NAMESPACE, CBOR_ARRAY,
  };

  // COSE related constants
  public final byte[] coseKeyKty = {1};
  public final byte[] coseKeyEc2Crv = {0x20};
  public final byte[] coseKeyEc2_X = {0x21};
  public final byte[] coseKeyEc2_Y = {0x22};
  public final byte[] coseSignAlg = {1};
  public final short[] STRUCT_MDL_DOCUMENT_DIGESTS = {
    KEY_MDL_NAMESPACE, CBOR_ARRAY,
  };

  public final byte[] DEVICE_REQ_VERSION = {0x63, 0x31, 0x2e, 0x30};
  final short CBOR_ADDITIONAL_MASK = 0x1F;

  /**
   * Create Mdoc Credentials: Cbor Map { 0 : CBOR Text String name, 1 : CBOR Text String docType, 2
   * : CBOR uint numSigningKeys, 3 : CBOR uint numUsesPerSigningKey, 4 : CBOR uint
   * signingKeyMinValidTimeMillis }
   */
  private final short[] STRUCT_MDOC_HAL_CMD = {
    KEY_ENC_DATA, CBOR_BINARY_STR,
    KEY_CERT, CBOR_BINARY_STR,
    KEY_HAL_CMD_DOC_SLOT, CBOR_UINT,
    KEY_TEST_CREDENTIAL, CBOR_UINT,
    KEY_CRED_CERT_CHALLENGE, CBOR_BINARY_STR,
    KEY_HAL_CMD_DOC_TYPE, CBOR_BINARY_STR,
    KEY_HAL_CMD_MDOC_NUM_SIGN_KEYS, CBOR_UINT,
    KEY_HAL_CMD_MDOC_USERS_PER_SIGN_KEY, CBOR_UINT,
    KEY_HAL_CMD_MDOC_VALID_TIME, CBOR_BINARY_STR,
    KEY_OPERATION, CBOR_UINT,
    KEY_CRED_DATA, CBOR_MAP,
    KEY_READER_ACCESS, CBOR_ARRAY,
    KEY_KEY_PARAMS, CBOR_MAP,
    KEY_ATTEST_KEY_PARAMS, CBOR_MAP,
    KEY_ATTEST_KEY_BLOB, CBOR_BINARY_STR,
  };

  // "documents"
  public final byte[] documents = {
    0x69, 0x64, 0x6f, 0x63, 0x75, 0x6d, 0x65, 0x6e, 0x74, 0x73,
  };
  private CBORDecoder mDecoder;

  public MdlSpecifications() {
    mDecoder = new CBORDecoder();
  }

  // Returns structure for a given key
  public short[] getStructure(short key) {
    switch (key) {
      case IND_SESSION_DATA:
        return STRUCT_SESSION_DATA;
      case IND_SESSION_ESTABLISHMENT:
        return STRUCT_SESSION_EST;
      case IND_STATIC_AUTH_DATA:
        return STRUCT_STATIC_AUTH_DATA;
      case IND_ISSUER_SIGNED_ITEM:
        return STRUCT_ISSUER_SIGNED_ITEM;
      case IND_CRED_DATA_DIGEST_MAPPING:
        return STRUCT_MDL_DOCUMENT_DIGESTS;
      case IND_MDOC_HAL_CMD:
        return STRUCT_MDOC_HAL_CMD;
      case KEY_EREADER_KEY:
        return STRUCT_COSE_KEY;
      case KEY_DEVICE_REQUEST:
        return STRUCT_DEVICE_REQ;
      case KEY_DEVICE_SIGNED:
        return STRUCT_DEVICE_SIGNED;
      case KEY_ITEMS_REQUEST:
        return STRUCT_ITEMS_REQUEST;
      case KEY_DOC_REQUESTS:
        return STRUCT_MDOC_REQUEST;
      case IND_MDOC:
        return STRUCT_MDOC;
      case KEY_DEVICE_AUTH:
        return STRUCT_DEVICE_AUTH;
      case KEY_CRED_DATA:
        return STRUCT_CRED_DATA;
      default:
        ISOException.throwIt(ISO7816.SW_UNKNOWN);
    }
    return null;
  }

  public short decodeStructure(
      short[] reqType, short[] retStructure, byte[] buf, short index, short length) {
    mDecoder.init(buf, index, length);
    clearStructure(retStructure);
    byte[] buffer = mDecoder.getBuffer();
    short numElements = mDecoder.readMajorType(CBORBase.TYPE_MAP);
    if ((short) (numElements * 2) > (short) reqType.length) {
      ISOException.throwIt(ISO7816.SW_DATA_INVALID);
    }
    while (numElements > 0) {
      short rowIndex =
          getKey(
              reqType,
              mDecoder.getBuffer(),
              mDecoder.getCurrentOffset()); // returns matching row in structure
      // All keys are used only once in a request
      if (retStructure[rowIndex] != 0) {
        ISOException.throwIt(ISO7816.SW_DATA_INVALID);
      }
      short valType = reqType[(short) (rowIndex + STRUCT_VAL_OFFSET)];
      short valStart = mDecoder.skipEntry(); // skip the key part
      assertValType(valType, buffer, valStart);
      short valEnd = mDecoder.skipEntry(); // skip the value
      short valLen = (short) (valEnd - valStart);
      retStructure[rowIndex++] = valStart;
      retStructure[rowIndex] = valLen;
      numElements--;
    }
    return mDecoder.getCurrentOffset();
  }

  private void clearStructure(short[] struct) {
    byte len = (byte) struct.length;
    for (byte i = 0; i < len; i++) {
      struct[i] = 0;
    }
  }

  private short getKey(short[] struct, byte[] buf, short keyStart) {
    byte index = 0;
    byte len = (byte) struct.length;
    while (index < len) {
      if (compareMain(struct[index], buf, keyStart)) {
        return index;
      }
      index = (byte) (index + STRUCT_ROW_SIZE + STRUCT_KEY_OFFSET);
    }
    ISOException.throwIt(ISO7816.SW_DATA_INVALID);
    return -1;
  }

  private short assertValType(short type, byte[] buf, short index) {
    switch (type) {
      case CBOR_SEMANTIC_TAG:
        if ((short) (buf[index] & CBOR_MAJOR_TYPE_MASK) != CBOR_SEMANTIC_TAG
            || (short) (buf[index++] & CBOR_ADDITIONAL_MASK) != CBOR_UINT8_LENGTH
            || (short) buf[index++] != CBOR_SEMANTIC_TAG_ENCODED_CBOR) {
          ISOException.throwIt(ISO7816.SW_DATA_INVALID);
        }
        type = CBOR_BINARY_STR;
      case CBOR_BINARY_STR:
      case CBOR_TEXT_STR:
      case CBOR_MAP:
      case CBOR_ARRAY:
        if ((buf[index] & CBOR_MAJOR_TYPE_MASK) != type) {
          ISOException.throwIt(ISO7816.SW_DATA_INVALID);
        }
        break;
      case CBOR_UINT:
      case CBOR_NEG_INT:
        byte t = (byte) (buf[index] & CBOR_MAJOR_TYPE_MASK);
        if (t != CBOR_UINT && t != CBOR_NEG_INT) {
          ISOException.throwIt(ISO7816.SW_DATA_INVALID);
        }
        break;
      case CBOR_NIL:
        if (CBOR_NIL != (short) (buf[index] & 0xFF)) {
          ISOException.throwIt(ISO7816.SW_DATA_INVALID);
        }
        break;
      case CBOR_BOOL:
        if (CBOR_TRUE != (short) (buf[index] & 0xFF) && CBOR_FALSE != (short) (buf[index] & 0xFF)) {
          ISOException.throwIt(ISO7816.SW_DATA_INVALID);
        }
        break;
      case CBOR_ANY:
        break;
      default:
        ISOException.throwIt(ISO7816.SW_DATA_INVALID);
        break;
    }
    return index;
  }

  public byte[] getDocNameSpaceString(short keyId) {
    byte[] str = null;
    if (keyId == KEY_MDL_NAMESPACE) {
      str = mdlNameSpace;
    }
    return str;
  }

  // Main namespace elements mapping table.
  private boolean compareMain(short keyId, byte[] buf, short keyStart) {
    byte[] str = null;
    switch (keyId) {
      case KEY_READER_ACCESS:
        str = readerAccess;
        break;
      case KEY_EREADER_KEY:
        str = session_eReaderKey;
        break;
      case KEY_DEVICE_REQUEST:
        str = session_data;
        break;
      case KEY_STATUS:
        str = status;
        break;
      case KEY_VERSION:
        str = version;
        break;
      case KEY_DOC_REQUESTS:
        str = docRequests;
        break;
      case KEY_COSE_KTY:
        str = coseKeyKty;
        break;
      case KEY_COSE_CRV:
        str = coseKeyEc2Crv;
        break;
      case KEY_COSE_X_COORD:
        str = coseKeyEc2_X;
        break;
      case KEY_COSE_Y_COORD:
        str = coseKeyEc2_Y;
        break;
      case KEY_COSE_SIGN_ALG:
        str = coseSignAlg;
        break;
      case KEY_DOC_TYPE:
        str = docType;
        break;
      case KEY_ISSUER_SIGNED:
        str = issuerSigned;
        break;
      case KEY_DEVICE_SIGNED:
        str = deviceSigned;
        break;
      case KEY_ERRORS:
        str = errors;
        break;
      case KEY_NAME_SPACES:
        str = nameSpaces;
        break;
      case KEY_DEVICE_AUTH:
        str = deviceAuth;
        break;
      case KEY_DEVICE_SIGNATURE:
        str = deviceSignature;
        break;
      case KEY_DEVICE_MAC:
        str = deviceMac;
        break;
      case KEY_READER_AUTH:
        str = readerAuth;
        break;
      case KEY_ITEMS_REQUEST:
        str = itemsRequest;
        break;
      case KEY_REQUEST_INFO:
        str = requestInfo;
        break;
      case KEY_DIGEST_MAPPING:
        str = digestIdMapping;
        break;
      case KEY_ISSUER_NS:
        str = issuerNameSpaces;
        break;
      case KEY_DIGEST_ID:
        str = digestID;
        break;
      case KEY_RANDOM:
        str = random;
        break;
      case KEY_ELEM_ID:
        str = elementIdentifier;
        break;
      case KEY_ELEM_VAL:
        str = elementValue;
        break;
      case KEY_ISSUER_AUTH:
        str = issuerAuth;
        break;
      default:
        break;
    }
    return compare(str, buf, keyStart);
  }

  private boolean compare(byte[] key, byte[] buf, short start) {
    return (key != null)
        && (Util.arrayCompare(buf, start, key, (short) 0, (short) key.length) == 0);
  }

  public short decodeTaggedStructure(
      short[] struct, short[] temp, byte[] buf, short start, short len) {
    if (Util.getShort(buf, start) != (short) 0xD818) {
      return -1;
    }
    start += 2;
    mDecoder.init(buf, start, len);
    len = mDecoder.readMajorType(CBORBase.TYPE_BYTE_STRING);
    return decodeStructure(struct, temp, buf, mDecoder.getCurrentOffset(), len);
  }
}
