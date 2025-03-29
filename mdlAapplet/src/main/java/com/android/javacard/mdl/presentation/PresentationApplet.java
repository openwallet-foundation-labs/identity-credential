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
package com.android.javacard.mdl.presentation;

import com.android.javacard.mdl.CBORBase;
import com.android.javacard.mdl.CBORDecoder;
import com.android.javacard.mdl.CBOREncoder;
import com.android.javacard.mdl.CBOREncoderCalc;
import com.android.javacard.mdl.MdlPresentationPkgStore;
import com.android.javacard.mdl.MdlService;
import com.android.javacard.mdl.MdlSpecifications;
import com.android.javacard.mdl.SEProvider;
import com.android.javacard.mdl.X509CertHandler;
import javacard.framework.AID;
import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.Shareable;
import javacard.framework.Util;
import javacardx.apdu.ExtendedLength;

/**
 * This applet is responsible to serve the reader's request for the Mdl Document. This applet
 * executes all the security controls, data processing & validation and NFC protocol specified in
 * ISO 18013-5 specs. It also is the store for the presentation packages provisioned by the
 * provisioning applet.
 */
public class PresentationApplet extends Applet implements ExtendedLength, MdlService {

  public static final byte[] AID_MDL_DIRECT_ACCESS_APPLET = {
    (byte) 0xA0, 0x00, 0x00, 0x02, 0x48, 0x04, 0x00
  };
  public static final byte INS_ENVELOPE = (byte) 0xC3;
  public static final byte INS_GET_RESPONSE = (byte) 0xC0;
  public static final byte[] AID_NDEF_TAG_APPLET = {
    (byte) 0xD2, 0x76, 0x00, 0x00, (byte) 0x85, 0x01, 0x01
  };
  public static final byte[] DIRECT_ACCESS_PROVISIONING_APPLET_ID = {
    (byte) 0xA0, 0x00, 0x00, 0x02, 0x48, 0x00, 0x01, 0x01, 0x01
  };
  // This is the fixed part of Device Management data.
  // Note: For NFC we don't have device retrieval methods given in this structure. It is
  // exchanged in Handover Select Message's alternate carrier records.
  // Refer  generateDeviceEngagement in com.android.identity.PresentationHelper
  public static final byte[] DEVICE_ENGAGEMENT_FIXED = {
    // CBOR Map of two elements
    (byte) (MdlSpecifications.CBOR_MAP | 0x02),
    // 0: text string of 3 chars containing "1.0" version.
    0x00,
    (byte) (MdlSpecifications.CBOR_TEXT_STR | 0x03),
    0x31,
    0x2E,
    0x30,
    // 1: security data item which is an CBOR array of two elements
    0x01,
    (byte) (MdlSpecifications.CBOR_ARRAY | 0x02),
    // First element of the security data item array is cipher suite 1 (CBOR unsigned int)
    // unsigned int of value 1
    0x01,
    // Second element is Semantic tag 6.24 - Note as tag val (unsigned int 24 = 0x18) > 23 the
    // Semantic tag
    // uses the short encoding and not tiny encoding.
    (byte) (byte) (MdlSpecifications.CBOR_SEMANTIC_TAG | MdlSpecifications.CBOR_UINT8_LENGTH),
    MdlSpecifications.CBOR_SEMANTIC_TAG_ENCODED_CBOR,
    // Data following this is generated in getNfcDeviceEngagementData method.
  };
  static final short MAX_RESPONSE_SIZE = 0x7FFF;
  private static final byte INVALID_VALUE = -1;
  static final byte[] HANDOVER_MSG_FIXED_PART = {
    // 1. Handover Select Record - size = 3 + 2 + 17 = 22
    // Refer nfcCalculateHandover in com.android.identity.PresentationHelper
    // NDEF Header
    (byte) 0x91, // MB=1, ME=0, CF=0, SR=1, IL=0, TNF= 1 (Well Known Type)
    // Lengths
    (byte) 2, // length of "Hs" type
    (byte) 17, // Size of "Hs" payload
    0x48,
    0x73, // // Value of UTF-8 encoded "Hs" type,
    // Payload - Handover Select Record Payload
    // Refer nfcCalculateStaticHandoverSelectPayload in com.android.identity.DataTransferNfc
    0x15, // Major and Minor Version of connection handover specs i.e. 1.5
    // Alternative Carrier Record - only one record
    // NDEF Header
    (byte) 0xD1, // MB=1, ME=1, CF=0, SR=1, IL=0, TNF= 1 (Well Known Type)
    // Lengths
    (byte) 2, // length of "ac" type
    (byte) 11, // Size of "ac" payload
    0x61,
    0x63, // Value of UTF-8 encoded "ac" type,
    // Payload - AC record payload
    // Refer to createNdefRecord method in com.android.identity.DataTransferNfc
    // Header
    0x01, // RFU = 0, CPS = 1 (ACTIVE)
    // CDR
    0x03, // Length of "nfc" configuration record reference chars
    0x6E,
    0x66,
    0x63, // Value of UTF-8 encoded "nfc" Id
    // Aux Record
    0x01, // Number of auxiliary record is 1 as only "mdoc" is required
    0x04, // Length of "mdoc" record reference chars
    0x6d,
    0x64,
    0x6f,
    0x63, // Value of UTF-8 encoded "mdoc" id.

    // 2. "nfc" Carrier Configuration Record - size = 4 +17 + 9 + 3 = 33
    // Refer to createNdefRecord method in com.android.identity.DataTransferNfc
    // NDEF Header
    (byte) 0x1C, // MB=0, ME=0, CF=0, SR=1, IL=1, TNF= 4 (External)
    // Lengths
    (byte) 17, // length of "iso.org:18013:nfc" type
    (byte) 9, // Size of payload
    (byte) 3, // size of id i.e. "nfc"
    // Value of UTF-8 encoded "iso.org:18013:nfc" type
    0x69,
    0x73,
    0x6F,
    0x2E,
    0x6F,
    0x72,
    0x67,
    0x3A,
    0x31,
    0x38,
    0x30,
    0x31,
    0x33,
    0x3A,
    0x6E,
    0x66,
    0x63,
    0x6E,
    0x66,
    0x63, // Value of UTF-8 encoded "nfc" Id 21+9
    // Payload Configuration record as defined in ISO18013 - section 8.2.2.2
    0x01, // version
    0x03, // Data length of the max command size
    0x01, // Data type of the max command size
    (byte) ((Context.MAX_CMD_DATA_RSP_DATA >> 8) & 0x00FF),
    (byte) (Context.MAX_CMD_DATA_RSP_DATA & 0x00FF),
    // Max Cmd Size
    0x03, // Data length of the max response size
    0x02, // Data type of the max response size
    (byte) ((Context.MAX_CMD_DATA_RSP_DATA >> 8) & 0x00FF),
    (byte) (Context.MAX_CMD_DATA_RSP_DATA & 0x00FF),
    // Max response Size

    // 3. "mdoc" NDEF Record - start = 33 + 22 + 2= 57
    // Refer nfcCalculateHandover in com.android.identity.PresentationHelper
    // NDEF Header - HEADER OFFSET = PAYLOAD_LEN_OFFSET - 2
    (byte) 0x5C, // MB=0, ME=0, CF=0, SR=1, IL=1, TNF= 4 (External)
    // Lengths
    (byte) 30, // length of "iso.org:18013:deviceengagement" type
    // Size of Payload - PAYLOAD_LEN_OFFSET = PAYLOAD OFFSET - MDOC_ID_LEN - MDOC_TYPE_LEN - 2
    INVALID_VALUE,
    (byte) 4, // size of id i.e. "mdoc"
    // Type Value - UTF-8 encoded  "iso.org:18013:deviceengagement"
    0x69,
    0x73,
    0x6F,
    0x2E,
    0x6F,
    0x72,
    0x67,
    0x3A,
    0x31,
    0x38,
    0x30,
    0x31,
    0x33,
    0x3A,
    0x64,
    0x65,
    0x76,
    0x69,
    0x63,
    0x65,
    0x65,
    0x6E,
    0x67,
    0x61,
    0x67,
    0x65,
    0x6D,
    0x65,
    0x6E,
    0x74,
    // Id Value - UTF-8 encoded "mdoc"
    0x6d,
    0x64,
    0x6f,
    0x63,
    // Payload - PAYLOAD OFFSET = FIXED PART size
  };
  private static final byte MAX_DOCUMENTS_SLOTS = 1;
  private static final byte MDOC_TYPE_LEN = 30;

  // The NDEF Data File is Handover Select Message, which consists of 3 records as follows:
  // 1. Handover Select Record, that consists of Alternative Carrier Record consisting of
  // first a CDR pointing to the "nfc "Carrier Configuration NDEF Record and secondly an Auxiliary
  // Record "mdoc" NDEF record.
  //
  // 2. The "nfc" Carrier Configuration NDEF Record provides three fields record of nfc type, max
  // command size and max response size. This record is pointed by CDR.
  // All the above messages are NDEF encoded and hardcoded, except the part of "mdoc" NDEF
  // record that contains Device engagement data, which is referenced by the fixed offset.
  //
  // 3. The "mdoc" NDEF Record, which contains the CBOR encoded Device Engagement data which is
  // generated by MDL Applet. This data is accessed by this applet using MDL Applet's Shareable
  // interface.
  private static final byte MDOC_ID_LEN = 4;
  // counter values
  private static final byte READER_MSG_COUNTER = 0;
  private static final byte DEVICE_MSG_COUNTER = 1;

  private static final short INITIAL_DOC_INDEX = (short) 0xFFFF;
  static CBOREncoderCalc mCalculator;
  // Count of messages received from reader in an active session. This is used to decrypt and
  // encrypt the request and response to and from the reader.
  private static short[] mMsgCounter;
  // Following is used to return multiple values
  private static short[] mRetVal;
  // Following is used to decode the request
  private static short[] mStructure;
  // Cbor Decoder
  private static CBORDecoder mDecoder;
  // Cbor Encoder
  private static CBOREncoder mEncoder;
  // Store of presentation package. Currently, stores only one package.
  private static PresentationPkgStore mPresentationPkgStore;
  // Scratch pad of 512 bytes. Used to store intermediate data.
  private static byte[] mScratchPad;
  private static Context mContext;
  private final MdlSpecifications mMdlSpecifications;
  private final SEProvider mSEProvider;
  private final Session mSession;
  private final X509CertHandler mX509CertHandler;

  private PresentationApplet(SEProvider se) {
    mDecoder = new CBORDecoder();
    mEncoder = new CBOREncoder();
    mCalculator = new CBOREncoderCalc();
    mScratchPad = JCSystem.makeTransientByteArray((short) 512, JCSystem.CLEAR_ON_DESELECT);
    mMdlSpecifications = new MdlSpecifications();
    mSEProvider = se;
    mSession = new Session(mSEProvider, mMdlSpecifications);
    mX509CertHandler = new X509CertHandler();
    mContext =
        new Context(
            JCSystem.makeTransientByteArray(Context.MAX_BUF_SIZE, JCSystem.CLEAR_ON_DESELECT),
            mMdlSpecifications);
    mStructure = JCSystem.makeTransientShortArray((short) 128, JCSystem.CLEAR_ON_DESELECT);
    mRetVal = JCSystem.makeTransientShortArray((short) 10, JCSystem.CLEAR_ON_DESELECT);
    mStructure = JCSystem.makeTransientShortArray((short) 10, JCSystem.CLEAR_ON_DESELECT);
    mMsgCounter = JCSystem.makeTransientShortArray((short) 2, JCSystem.CLEAR_ON_RESET);
    mMsgCounter[READER_MSG_COUNTER] = 1;
    mMsgCounter[DEVICE_MSG_COUNTER] = 1;
    mPresentationPkgStore = PresentationPkgStore.instance();
    mPresentationPkgStore.configure(
        mSEProvider, mMdlSpecifications, MAX_DOCUMENTS_SLOTS, (byte) 1, (short) 0x7FFF);
    reset();
  }

  // Install
  public static void install(byte[] bArray, short bOffset, byte bLength) {
    // check incoming parameter data
    byte iLen = bArray[bOffset]; // aid length
    bOffset = (short) (bOffset + iLen + 1);
    byte cLen = bArray[bOffset]; // control info length
    bOffset = (short) (bOffset + cLen + 1);
    byte aLen = bArray[bOffset]; // applet data length
    // make offset point to the applet data.
    bOffset = (short) (bOffset + 1);
    SEProvider se = new SEProvider(bArray, bOffset, aLen);
    new PresentationApplet(se).register();
  }

  boolean processReaderAuth(
      DocumentRequest req,
      MdocPresentationPkg doc,
      short[] tmpArray,
      byte[] buf,
      short itemsBytesStart,
      short itemsBytesLen,
      short readerAuth,
      short readerAuthLen,
      byte[] scratch,
      short scratchStart,
      short scratchLen) {
    // First check whether document requires reader authentication
    if (!doc.isReaderAuthRequired()) {
      // If no reader auth is required then just return true.
      return true;
    }
    // Decode the reader Auth CoseSign1
    short signLen =
        req.decodeCoseSign1(buf, readerAuth, readerAuthLen, tmpArray, scratch, scratchStart);
    if (signLen < 0) {
      return false;
    }
    // Extract the algorithm
    short alg = tmpArray[0];
    if (alg != SEProvider.ES256 && alg != SEProvider.ES384 && alg != SEProvider.ES512) {
      return false;
    }

    // Perform X509 chain validation - this will return the public keys contained in the
    // certificate chain in the tmpArray.
    if (!validateChain(doc, buf, tmpArray, scratch, signLen, (short) (scratchLen - signLen))) {
      return false;
    }

    short sessionTransLength = Util.getShort(mSession.mSessionTranscriptBytes, (short) 0);
    // The session transcript in Session is tagged byte string so extract Session
    // Transcript array from it. This returns the start of this array.
    short sessionTransStart =
        req.getSessionTranscriptStart(
            mSession.mSessionTranscriptBytes, (short) 2, sessionTransLength);
    // Adjust the transcript length - note 2 bytes at the beginning of the mSessionTranscript
    // stores length of the SessionTranscriptBytes.
    sessionTransLength -= (short) (sessionTransStart - 2);

    // Now perform the reader authentication
    return req.performReaderAuth(
        mSEProvider,
        buf,
        itemsBytesStart,
        itemsBytesLen, // item bytes
        mSession.mSessionTranscriptBytes,
        sessionTransStart,
        sessionTransLength,
        alg, // algorithm to use
        buf,
        tmpArray[1],
        tmpArray[0], // public key
        scratch,
        scratchStart,
        signLen, // signature to validate
        scratch,
        signLen,
        (short) (scratchLen - signLen) // scratch pad
        );
  }

  void reset() {
    mContext.reset();
    mSession.reset();
    short i = (byte) mStructure.length;
    while (i > 0) {
      mStructure[--i] = INVALID_VALUE;
    }
    i = (byte) mRetVal.length;
    while (i > 0) {
      mRetVal[--i] = INVALID_VALUE;
    }
    mMsgCounter[(short) 0] = mMsgCounter[(short) 1] = 1;
  }

  private void handleSessionEst(
      APDU apdu,
      byte[] buf,
      short readerKeyStart,
      short readeKeyLen,
      short devReqStart,
      short devReqLen) {

    // then both the reader's key and encrypted device request must be present
    if (readerKeyStart == 0 || devReqStart == 0) {
      ISOException.throwIt(ISO7816.SW_WRONG_DATA);
    }
    // Copy the encoded reader key into class variable.
    Util.arrayCopyNonAtomic(
        buf, readerKeyStart, mSession.mReaderEncodedKeyBytes, (short) 2, readeKeyLen);
    Util.setShort(mSession.mReaderEncodedKeyBytes, (short) 0, readeKeyLen);
    // Extract the enc data offsets and length
    mDecoder.init(buf, devReqStart, devReqLen);
    short encDataLen = mDecoder.readMajorType(CBORBase.TYPE_BYTE_STRING);
    short encDataStart = mDecoder.getCurrentOffset();
    // compute session transcript and mCryptoKey
    mSession.computeSessionDataAndSecrets();

    // Now reset the device and read msg counter which may be zero.
    mMsgCounter[DEVICE_MSG_COUNTER] = mMsgCounter[READER_MSG_COUNTER] = 1;
    encDataLen = decryptSessionData(buf, encDataStart, encDataLen);
    handleDeviceRequestAndSendResponse(apdu, buf, encDataStart, encDataLen);
  }

  private void handleSessionData(
      APDU apdu, byte[] buf, short statusStart, short statusLen, short dataStart, short dataLen) {
    // either encrypted data or status must be present
    // - but both cannot be absent or present at the same time.
    if (((statusStart == 0) == (dataStart == 0)) || ((statusStart != 0) && (statusLen != 1))) {
      ISOException.throwIt(ISO7816.SW_WRONG_DATA);
    }
    // Is the status present
    if (statusStart != 0) { // If the status is sent then there should
      // Check the status
      short status = buf[statusStart];
      if (status == (short) 20 || status == (short) 10 || status == (short) 11) {
        // terminate the session,.
        reset();
        sendSuccessResponseForSessionTermination(apdu);
      } else { // invalid data is present.
        ISOException.throwIt(ISO7816.SW_DATA_INVALID);
      }
    } else {
      // Session is established, so decrypt and process the data. Then send the response
      // Extract the enc data offsets and length
      mDecoder.init(buf, dataStart, dataLen);
      short encDataLen = mDecoder.readMajorType(CBORBase.TYPE_BYTE_STRING);
      short encDataStart = mDecoder.getCurrentOffset();
      encDataLen = decryptSessionData(buf, encDataStart, encDataLen);
      handleDeviceRequestAndSendResponse(apdu, buf, encDataStart, encDataLen);
    }
  }

  private short decryptSessionData(byte[] buf, short encDataStart, short encDataLen) {
    // Decrypt the data
    encDataLen =
        mSession.encryptDecryptData(
            buf,
            encDataStart,
            encDataLen,
            mSession.mReaderKey,
            mMsgCounter[READER_MSG_COUNTER],
            false,
            mRetVal,
            false);
    // Successfully decrypted and hence increment the reader message counter
    mMsgCounter[READER_MSG_COUNTER]++;
    return encDataLen;
  }

  private short getNextDocIndex(short currentIndex) {
    for (++currentIndex; currentIndex < mContext.MAX_DOC_REQUESTS; ++currentIndex) {
      DocumentRequest docReq = (DocumentRequest) mContext.mDocumentRequests[currentIndex];
      if ((docReq.getDocument() != null) && !docReq.isError()) {
        return currentIndex;
      }
    }
    return Context.MAX_DOC_REQUESTS;
  }

  private void handleDeviceRequestAndSendResponse(
      APDU apdu, byte[] buf, short dataStart, short dataLen) {
    // Process the device request.
    mContext.mDocumentsCount[0] =
        (byte) processDeviceRequest(buf, dataStart, dataLen, mContext.mDocumentRequests);
    // If there are no requests.
    if (mContext.mDocumentsCount[0] <= 0) {
      sendSuccessResponseWithoutDocs(apdu);
    } else {
      // Increment the usage count of the requested document
      short numDocRequests = (short) mContext.mDocumentRequests.length;
      for (short i = 0; i < numDocRequests; i++) {
        DocumentRequest docReq = (DocumentRequest) mContext.mDocumentRequests[i];
        if (docReq.getDocument() != null && !docReq.isError()) {
          docReq.getDocument().incrementUsageCount();
        }
      }
      // Send the response.
      processGetResponse(apdu, true);
    }
  }

  // Process the envelope
  // 1. Receive all the data. This can consist on multiple APDUs. Internal buffer must be big
  // enough.
  // 2. Create Session and establish the session specific crypto material. Alternatively, use
  // the existing session if available.
  // 3. Decrypt the enveloped data.
  // 4. process the device request.
  private void processEnvelope(APDU apdu) {
    // receive all the bytes into internal mBuffer
    if (receiveBytes(apdu)) {
      return;
    }
    // extract the request from 0x53 tag
    short bufIndex = extractRequest(mContext.mBuffer, (short) mContext.mBuffer.length, mRetVal);
    short bufLen = mRetVal[0];
    short requestType =
        mSession.isSessionInitialized()
            ? MdlSpecifications.IND_SESSION_DATA
            : MdlSpecifications.IND_SESSION_ESTABLISHMENT;
    short offset =
        mMdlSpecifications.decodeStructure(
            mMdlSpecifications.getStructure(requestType),
            mStructure,
            mContext.mBuffer,
            bufIndex,
            bufLen);
    if (offset < 0 || offset != (short) (bufIndex + bufLen)) {
      ISOException.throwIt(ISO7816.SW_DATA_INVALID);
    }
    // If this is the session establishment
    if (requestType == MdlSpecifications.IND_SESSION_ESTABLISHMENT) {
      handleSessionEst(
          apdu, mContext.mBuffer, mStructure[0], mStructure[1], mStructure[2], mStructure[3]);
    } else { // this is session data
      handleSessionData(
          apdu, mContext.mBuffer, mStructure[0], mStructure[1], mStructure[2], mStructure[3]);
    }
  }

  /**
   * This method creates Device response with only version and status elements. Status will be
   * success and documents array will be empty.
   */
  private void sendSuccessResponseWithoutDocs(APDU apdu) {
    if (mContext.mChunkSize[0] != 0) {
      ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
    }
    byte[] buf = apdu.getBuffer();
    short le = apdu.setOutgoing();
    // encode the response
    mEncoder.init(mScratchPad, (short) 0, (short) mScratchPad.length);
    short len = encodeDeviceResponseStartSuccess(mEncoder, (byte) 0);
    // Backup the device response from mScratchPad to buf
    Util.arrayCopyNonAtomic(mScratchPad, (short) 0, buf, (short) 0, len);

    // create the headers in apdu buffer. Note: the response length needs to include GCM Auth tag
    short index =
        addEnvAndSessionHeaders(buf, len, le, (short) (len + SEProvider.AES_GCM_TAG_LENGTH));

    // Copy back the device response at the end of the buf.
    Util.arrayCopyNonAtomic(buf, (short) 0, buf, index, len);

    // Now encrypt the device response using SKDeviceSecret and DeviceCounter
    short encLen =
        mSession.encryptDecryptData(
            buf,
            index,
            len,
            mSession.mDeviceKey,
            mMsgCounter[DEVICE_MSG_COUNTER],
            true,
            mRetVal,
            false);
    if (encLen != (short) (len + SEProvider.AES_GCM_TAG_LENGTH)) {
      ISOException.throwIt(ISO7816.SW_UNKNOWN);
    }
    short outLen = (short) (index + encLen - len);
    Util.arrayCopyNonAtomic(buf, len, buf, (short) 0, outLen);
    mMsgCounter[DEVICE_MSG_COUNTER]++;
    sendBytes(apdu, (short) 0, outLen, ISO7816.SW_NO_ERROR);
  }

  /** SessionData = { "status" : uint ; Status code } */
  private void sendSuccessResponseForSessionTermination(APDU apdu) {
    if (mContext.mChunkSize[0] != 0) {
      ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
    }
    byte[] buf = apdu.getBuffer();
    short index = 0;
    short le = apdu.setOutgoing();
    // encode the response
    mEncoder.init(mScratchPad, (short) 0, (short) mScratchPad.length);
    mEncoder.startMap((short) 1);
    // "status" : uint
    mEncoder.encodeRawData(
        mMdlSpecifications.status, (short) 0, (short) mMdlSpecifications.status.length);
    mEncoder.encodeUInt8((byte) 20);

    // add envelope header
    buf[index++] = (byte) 0x53;
    buf[index++] = (byte) (2 + mMdlSpecifications.status.length);
    // Add status
    mEncoder.init(buf, index, le);
    // Map of one element
    mEncoder.startMap((short) 1);
    // "status" : uint
    mEncoder.encodeRawData(
        mMdlSpecifications.status, (short) 0, (short) mMdlSpecifications.status.length);
    // status of 20 i.e. session termination
    mEncoder.encodeUInt8((byte) 20);
    index = mEncoder.getCurrentOffset();
    sendBytes(apdu, (short) 0, index, ISO7816.SW_NO_ERROR);
  }

  // TODO use mDeviceRequest to generate documentError in mBuffer. Send that first followed by
  // document
  // private void addDocumentErrorsToResponse() {
  // }

  // Process Device Request
  //
  // 1. create the data structure for DocumentRequest data structure for the doc requests and Items
  // Requests. Device request consists of three layer of structures:
  // Device request-> docRequest -> ItemsRequest
  // And Items Request consists of Doc Type, etc. This method just creates an index of all the
  // namespaces and corresponding data elements requested.
  // 2. Perform reader auth for each document request.
  // 3. Assemble response. Add Issuer signed, issuer auth device signed and device auth
  // TODO Currently this method does not process intentToRetain flag and just ignores it.
  private short processDeviceRequest(
      byte[] buf, short deviceRequestStart, short deviceRequestLen, Object[] documentRequests) {

    // First decode the top level
    short[] str = mMdlSpecifications.getStructure(MdlSpecifications.KEY_DEVICE_REQUEST);
    mMdlSpecifications.decodeStructure(str, mStructure, buf, deviceRequestStart, deviceRequestLen);

    // Validate device request version.
    // TODO verify whether device request version is valid or not.
    // it should be "1.0" and less then the version of device engagement.
    // Currently we only check for "1.0".
    if (Util.arrayCompare(
            buf, mStructure[0], mMdlSpecifications.DEVICE_REQ_VERSION, (short) 0, mStructure[1])
        != 0) {
      ISOException.throwIt(ISO7816.SW_DATA_INVALID);
    }

    // Now decode the array of doc requests
    short docReqStart = mStructure[2];
    short docReqLen =  mStructure[3];
    mDecoder.init(buf, docReqStart, docReqLen);
    short docReqSize = mDecoder.readMajorType(CBORBase.TYPE_ARRAY);
    short docReqIndex = 0;
    short current = 0;
    short next = 0;

    // Then read doc requests one by one to create Document.
    for (short i = 0; i < docReqSize; i++) {
      if (docReqIndex >= documentRequests.length) {
        ISOException.throwIt(ISO7816.SW_DATA_INVALID);
      }
      current = mDecoder.getCurrentOffset();
      next = mDecoder.skipEntry();
      DocumentRequest docRequest = (DocumentRequest) documentRequests[docReqIndex];
      docRequest.reset();

      // Decode a doc request
      str = mMdlSpecifications.getStructure(MdlSpecifications.KEY_DOC_REQUESTS);
      current =
          mMdlSpecifications.decodeStructure(
              str, mStructure, buf, current, (short) (next - current));
      if (current != next) {
        ISOException.throwIt(ISO7816.SW_DATA_INVALID);
      }
      short itemsStart = mStructure[0];
      short itemsLen = mStructure[1];
      short readerAuthStart = mStructure[2];
      short readerAuthLen = mStructure[3];
      if (itemsStart == INVALID_VALUE || readerAuthStart == INVALID_VALUE) {
        ISOException.throwIt(ISO7816.SW_DATA_INVALID);
      }
      // Now process Items Request
      if (!processItemsRequest(
          buf, itemsStart, itemsLen, readerAuthStart, readerAuthLen, docRequest)) {
        docRequest.setError();
        docRequest.setErrorCode(MdlSpecifications.MDL_ERR_NOT_FOUND);
      } else {
        docReqIndex++;
      }
      mDecoder.init(buf, docReqStart, docReqLen);
      mDecoder.getCurrentOffsetAndIncrease((short) (next - docReqStart));
    }
    return docReqIndex;
  }

  private boolean receiveBytes(APDU apdu) {
    byte[] buf = apdu.getBuffer();
    boolean moreChunksToCome = false;
    if (buf[ISO7816.CLA_ISO7816] == 0x10) {
      moreChunksToCome = true;
    } else if (buf[ISO7816.CLA_ISO7816] != 0x00) {
      ISOException.throwIt(ISO7816.SW_RECORD_NOT_FOUND);
    }
    apdu.setIncomingAndReceive();
    short len = apdu.getIncomingLength();
    if (len == 0) {
      ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
    }

    if ((short) (len + mContext.mBufWriteIndex[0]) > mContext.mBuffer.length) {
      ISOException.throwIt(ISO7816.SW_RECORD_NOT_FOUND);
    }
    Util.arrayCopyNonAtomic(
        buf,
        buf[ISO7816.OFFSET_LC] != 0 ? ISO7816.OFFSET_CDATA : ISO7816.OFFSET_EXT_CDATA,
        mContext.mBuffer,
        mContext.mBufWriteIndex[0],
        len);
    mContext.mBufWriteIndex[0] += len;
    return moreChunksToCome;
  }

  private short extractRequest(byte[] buffer, short bufLen, short[] ret) {
    // extract the tag 0x53
    short tag = (short) (buffer[0] & 0xFF);
    short tagLen = (short) (buffer[1] & 0xff);
    short offset = 2;
    if (tag != (byte) 0x53) {
      ISOException.throwIt(ISO7816.SW_RECORD_NOT_FOUND);
    }
    if (tagLen == (short) 0x0081) {
      tagLen = (short) (buffer[2] & 0xff);
      offset = 3;
    } else if (tagLen == (short) 0x0082) {
      tagLen = Util.getShort(buffer, (short) 2);
      offset = 4;
    } else if (tagLen == (short) 0x0080 || tagLen > 0x0082) {
      ISOException.throwIt(ISO7816.SW_RECORD_NOT_FOUND);
    }
    if ((short) (tagLen + offset) > bufLen) {
      ISOException.throwIt(ISO7816.SW_RECORD_NOT_FOUND);
    }
    ret[0] = tagLen;
    return offset;
  }

  /**
   * DeviceResponse = { "version" : tstr, ; Version of the DeviceResponse structure ? "documents" :
   * [+Document], ; Returned documents ? "documentErrors": [+DocumentError]; For unreturned
   * documents, optional error codes "status" : uint ; Status code } Document = { "docType" :
   * DocType, ; Document type returned "issuerSigned" : IssuerSigned, ; Returned data elements
   * signed by the issuer "deviceSigned" : DeviceSigned, ; Returned data elements signed by the mdoc
   * ? "errors" : Errors } DocumentError = { DocType => ErrorCode ; Error codes for unreturned
   * documents } IssuerSigned = { ? "nameSpaces" : IssuerNameSpaces, ; Returned data elements
   * "issuerAuth" : IssuerAuth ; Contains the mobile security object (MSO) ; for issuer data
   * authentication } IssuerNameSpaces = { ; Returned data elements for each namespace + NameSpace
   * => [ + IssuerSignedItemBytes ] } IssuerSignedItemBytes = #6.24(bstr .cbor IssuerSignedItem)
   * IssuerSignedItem = { "digestID" : uint, ; Digest ID for issuer data authentication "random" :
   * bstr, ; Random value for issuer data authentication "elementIdentifier" :
   * DataElementIdentifier, ; Data element identifier "elementValue" : DataElementValue ; Data
   * element value } DeviceSigned = { "nameSpaces" : DeviceNameSpacesBytes, ; Returned data elements
   * "deviceAuth" : DeviceAuth ; Contains the device authentication ; for mdoc authentication }
   * DeviceNameSpacesBytes = #6.24(bstr .cbor DeviceNameSpaces) DeviceNameSpaces = { * NameSpace =>
   * DeviceSignedItems ; Returned data elements for each namespace } DeviceSignedItems = { +
   * DataElementIdentifier => DataElementValue ; Returned data element identifier and value }
   * DeviceAuth = { ; Either signature or MAC for mdoc authentication "deviceSignature" :
   * DeviceSignature // ; "//" means or "deviceMac" : DeviceMac } Where DeviceMac is COSEMac0 -
   * Array of 4 with protected content {alg=5}, no un protected content and payload followed by mac
   * tag. [ << {1: 5} >>, {}, null, h'
   * E99521A85AD7891B806A07F8B5388A332D92C189A7BF293EE1F543405AE6824D' ]
   *
   * <p>Errors = { + NameSpace => ErrorItems ; Error codes for each namespace } ErrorItems = { +
   * DataElementIdentifier => ErrorCode ; Error code per data element } ErrorCode = int ; Error code
   * TODO this method only returns one document currently. In future this needs to expanded to
   * return multiple documents, Also this method currently does not send any errors.
   */
  private short calculateResponseLength() {
    mCalculator.initialize((short) 0, MAX_RESPONSE_SIZE);
    mContext.mIncrementalResponseState[Context.CURRENT_STATE] = Context.RESP_START;
    mContext.mIncrementalResponseState[Context.CURRENT_STATE] = Context.RESP_START;
    mContext.mIncrementalResponseState[Context.CURRENT_NAMESPACE] = 0;
    mContext.mIncrementalResponseState[Context.CURRENT_ELEMENT] = 0;
    mContext.mIncrementalResponseState[Context.CURRENT_DATA_PTR_START] = 0;
    mContext.mIncrementalResponseState[Context.CURRENT_DATA_PTR_END] = 0;
    while (mContext.mIncrementalResponseState[Context.CURRENT_STATE] != Context.RESP_IDLE) {
      encodeResponse(mCalculator);
    }
    return mCalculator.getCurrentOffset();
  }

  /**
   * DeviceResponse = { "version" : tstr, ; Version of the DeviceResponse structure ? "documents" :
   * [+Document], ; Returned documents ? "documentErrors": [+DocumentError]; For unreturned
   * documents, optional error codes "status" : uint ; Status code } This method encodes version and
   * status which is always success.
   */
  private short encodeDeviceResponseStartSuccess(CBOREncoder encoder, byte reqCount) {
    // Map of 3 because we do not return error. Out of three elements two are encoded here and the
    // third i.e. array of documents is encoded in separate method which will be called from
    // encodeResponse method.
    short start = encoder.getCurrentOffset();
    encoder.startMap((short) 3);

    // version" : "1.0"
    encoder.encodeRawData(
        mMdlSpecifications.version, (short) 0, (short) mMdlSpecifications.version.length);
    encoder.encodeRawData(
        mMdlSpecifications.DEVICE_REQ_VERSION,
        (short) 0,
        (short) mMdlSpecifications.DEVICE_REQ_VERSION.length);
    // "status" : 0 - always ok
    encoder.encodeRawData(
        mMdlSpecifications.status, (short) 0, (short) mMdlSpecifications.status.length);
    // 0 - always ok
    encoder.encodeUInt8((byte) 0);
    // "documents" : [+Document]
    encoder.encodeRawData(
        mMdlSpecifications.documents, (short) 0, (short) mMdlSpecifications.documents.length);
    encoder.startArray(reqCount);
    return (short) (encoder.getCurrentOffset() - start);
  }

  /**
   * SessionData = { ? "data" : bstr ; Encrypted mdoc response or mdoc request ? "status" : uint ;
   * Status code } We only add start of "data" in this method.
   */

  /**
   * Document = { "docType" : DocType, ; Document type returned "issuerSigned" : IssuerSigned, ;
   * Returned data elements signed by the issuer "deviceSigned" : DeviceSigned, ; Returned data
   * elements signed by the mdoc ? "errors" : Errors } This encodes start of map and docType.
   */
  private short encodeDocumentStart(CBOREncoder encoder, DocumentRequest request) {
    short start = encoder.getCurrentOffset();
    // Document
    encoder.startMap((short) 3);
    // "docType" : DocType
    encoder.encodeRawData(
        mMdlSpecifications.docType, (short) 0, (short) mMdlSpecifications.docType.length);
    short len = request.getDocType(mScratchPad, (short) 0);
    encoder.encodeTextString(mScratchPad, (short) 0, len);
    return (short) (encoder.getCurrentOffset() - start);
  }

  /**
   * Document = { ... "deviceSigned" : DeviceSigned, ; Returned data elements signed by the mdoc ...
   * } "deviceSigned" : DeviceSigned
   *
   * <p>DeviceSigned = { "nameSpaces" : DeviceNameSpacesBytes, ; Returned data elements "deviceAuth"
   * : DeviceAuth ; Contains the device authentication ; for mdoc authentication }
   * DeviceNameSpacesBytes = this is empty i.e. 0 bytes DeviceAuth = { "deviceMac" : DeviceMac }
   * DeviceMac is COSE_Mac0 structure (section 9.1.3.5), where payload is null, ‘external_aad’ is
   * byte string of 0 bytes and alg element in protected header will be “HMAC 256/256” i.e. 1. The
   * mac tag will be calculated in request.
   *
   * <p>This method encodes device signed key value pair. Note we don't divide this because we
   * calculate the entire during querying stage.
   */
  private short encodeDeviceSigned(CBOREncoder encoder, DocumentRequest request) {
    short start = encoder.getCurrentOffset();
    // "deviceSigned" : DeviceSigned
    encoder.encodeRawData(
        mMdlSpecifications.deviceSigned, (short) 0, (short) mMdlSpecifications.deviceSigned.length);
    // DeviceSigned
    encoder.startMap((short) 2);
    // nameSpaces" : DeviceNameSpacesBytes - empty structure
    encoder.encodeRawData(
        mMdlSpecifications.nameSpaces, (short) 0, (short) mMdlSpecifications.nameSpaces.length);
    encoder.encodeTag((byte) MdlSpecifications.CBOR_SEMANTIC_TAG_ENCODED_CBOR);
    encoder.startByteString((short) 1); // empty
    encoder.startMap((short) 0);
    // "deviceAuth" : DeviceAuth
    encoder.encodeRawData(
        mMdlSpecifications.deviceAuth, (short) 0, (short) mMdlSpecifications.deviceAuth.length);
    // DeviceAuth
    encoder.startMap((byte) 1);
    // "deviceMac" : DeviceMac - DeviceMac is Cose structure
    encoder.encodeRawData(
        mMdlSpecifications.deviceMac, (short) 0, (short) mMdlSpecifications.deviceMac.length);
    // DeviceMac - array of 4.
    encoder.startArray((short) 4);
    // 1. Protected content which is map with one key value pair
    // Protected
    encoder.startByteString((short) 3);
    encoder.startMap((short) 1);
    encoder.encodeUInt8((byte) MdlSpecifications.COSE_LABEL_ALG);
    encoder.encodeUInt8((byte) MdlSpecifications.COSE_ALG_HMAC_256_256);
    // 2. Unprotected is empty
    encoder.startMap((short) 0);
    // 3. payload is null
    encoder.writeRawByte((byte) MdlSpecifications.CBOR_NIL);
    // 4. Mac tag - always 32 bytes long
    byte[] macTag = request.getDeviceAuthMacTag();
    encoder.encodeRawData(macTag, (short) 0, (short) macTag.length);
    return (short) (encoder.getCurrentOffset() - start);
  }

  short addEnvAndSessionHeaders(byte[] buf, short index, short len, short respLength) {
    // add session data header
    // Map of one element
    mEncoder.init(mScratchPad, (short) 0, (short) mScratchPad.length);
    mEncoder.startMap((short) 1);
    // "data" : bstr
    mEncoder.encodeRawData(
        mMdlSpecifications.session_data, (short) 0, (short) mMdlSpecifications.session_data.length);
    mEncoder.startByteString(respLength);
    respLength += mEncoder.getCurrentOffset();

    // add envelope header
    buf[index++] = (byte) 0x53;
    if (respLength < 128) {
      buf[index++] = (byte) respLength;
    } else if (respLength < 256) {
      buf[index++] = (byte) 0x81;
      buf[index++] = (byte) respLength;
    } else {
      buf[index++] = (byte) 0x82;
      Util.setShort(buf, index, respLength);
      index += 2;
    }
    // Copy the session data header.
    return Util.arrayCopyNonAtomic(mScratchPad, (short) 0, buf, index, mEncoder.getCurrentOffset());
  }

  private short transferData(byte[] buf, short index, short bufLen) {
    // amount of data to be copied from the context buffer
    short len = (short) (mContext.mBufWriteIndex[0] - mContext.mBufReadIndex[0]);

    // Index points to next empty position in the apdu buffer - starting from zero.
    // So, available space in apdu buffer is (bufLen - index). Now, the buf length will be
    // equal to chunk size as that is the max we can send.
    short availBuf = (short) (bufLen - index);

    // If input data length is more than available apdu buffer space then only send the data
    // length equal to available data length and subtract that from remaining bytes.
    if (len > availBuf) {
      len = availBuf;
    }

    // Copy the data from context buffer to apdu buffer.
    Util.arrayCopyNonAtomic(mContext.mBuffer, mContext.mBufReadIndex[0], buf, index, len);
    mContext.mBufReadIndex[0] += len;
    return len;
  }

  private short encodeData(CBOREncoder encoder) {
    short start = encoder.getCurrentOffset();
    // For every doc request. Each document is a root node of the tree.
    while (mContext.mIncrementalResponseState[Context.CURRENT_STATE] != Context.RESP_IDLE) {
      try {
        encodeResponse(mEncoder);
      } catch (ISOException exp) {
        // This exception is thrown by encoder when it cannot encode anymore data as it has run
        // out of space.
        if (exp.getReason() == ISO7816.SW_UNKNOWN) {
          break;
        } else {
          // Some error happened
          ISOException.throwIt(exp.getReason());
        }
      }
    }
    return (short) (encoder.getCurrentOffset() - start);
  }

  /**
   * This method returns the response to the reader. It checks whether this is the first message in
   * a chain of responses and if so, it handles that differently by calculating entire message
   * length and appending envelope and session headers. Rest of the messages sent incrementally. It
   * sends message les then or equal to chunk size. However, it processes message data greater the
   * chunk size. This is done so that we can take care of element boundaries.
   *
   * <p>For the first message: SessionData = { ? "data" : bstr ; Encrypted mdoc response or mdoc
   * request (includes AES GCM auth tag) ? "status" : uint ; Status code }
   */
  private void processGetResponse(APDU apdu, boolean firstMessage) {
    // Start responding and return the requested document.
    // received before we respond, it is also the chunk size.
    byte[] buf = apdu.getBuffer();
    short le = apdu.setOutgoing();
    short index = 0;
    mContext.setChunkSize(le);

    // This is the first message in the chain of responses.
    if (firstMessage) {
      // calculate the device response length - this is required because envelope header and
      // session header requires total device response length. This only returns size of data to
      // be encoded. It does not include the Auth tag which will be added to the encrypted
      // device response data.
      mContext.setOutGoing(calculateResponseLength(), getNextDocIndex(INITIAL_DOC_INDEX));

      // Add the envelope and session data headers and resp length includes the auth tag length
      // which will be concatenated with encrypted response data.
      index =
          addEnvAndSessionHeaders(
              buf,
              (short) 0,
              mContext.mChunkSize[0],
              (short) (mContext.mRemainingBytes[0] + SEProvider.AES_GCM_TAG_LENGTH));
      // This is the first response message hence initialize for the session encryption.
      mSession.beginIncrementalEncryption(
          mMsgCounter[DEVICE_MSG_COUNTER], mScratchPad, (short) 0, (short) mScratchPad.length);
    }
    // Drive the response state machine, which sends the response to individual document requests
    // received in device request one by one - incrementally i.e. in chunks of size = le.
    short len = 0;
    short status = ISO7816.SW_NO_ERROR;
    // First check whether there is any space remaining in the apdu buffer.
    while (index < mContext.mChunkSize[0]) {
      // if there is any data remaining in context buffer and if yes then copy that to apdu buffer.
      if (mContext.mBufReadIndex[0] < mContext.mBufWriteIndex[0]) {
        len = transferData(buf, index, mContext.mChunkSize[0]);
        // Remaining bytes to be sent is equal to
        // remaining bytes to be encoded from presentation package +
        // remaining encrypted data in context buffer +
        // remaining un encrypted data in Session's encryption buffer +
        // 16 bytes of auth tag.
        short remBytes =
            (short)
                (mContext.mRemainingBytes[0]
                    + mContext.mBufWriteIndex[0]
                    - mContext.mBufReadIndex[0]
                    + mSession.mBuffer[Session.BUF_LENGTH_OFFSET]
                    + SEProvider.AES_GCM_TAG_LENGTH
                    + mSession.mAesGcmInternalBufferLen[0]);
        // Calculate the status.
        if (remBytes > 255) {
          status = ISO7816.SW_BYTES_REMAINING_00;
        } else if (remBytes > 0) {
          status = (short) (ISO7816.SW_BYTES_REMAINING_00 | (short) (remBytes & 0x00ff));
        } else if (remBytes < 0) {
          ISOException.throwIt(ISO7816.SW_UNKNOWN);
        } else {
          status = ISO7816.SW_NO_ERROR;
        }
        // advance the current pointer in the apdu buffer.
        index += len;
      } else if (mContext.mRemainingBytes[0] > 0) { // There is data remaining to be encoded.
        // Context buffer is empty,
        // So encode new data by reading the package. This will increment write pointer.
        // Then encrypt the data which may be upto 16 bytes less if the data is not block aligned.
        mContext.mBufWriteIndex[0] = mContext.mBufReadIndex[0] = 0;
        // If there is any buffered data in encryption session then copy that.
        mContext.mBufWriteIndex[0] +=
            mSession.readAndClearBufferedData(mContext.mBuffer, mContext.mBufWriteIndex[0]);
        mEncoder.init(
            mContext.mBuffer,
            mContext.mBufWriteIndex[0],
            (short) (mContext.mBuffer.length - mContext.mBufWriteIndex[0]));
        // Encode data from presentation package and copy that in context buffer.
        short encodedLen = encodeData(mEncoder);
        mContext.mRemainingBytes[0] -= encodedLen;
        mContext.mBufWriteIndex[0] += encodedLen;

        // Encrypt all the data from 0 to write index.
        // Encrypt in place. The returned data can be less than the input data if the data is not
        // blocked aligned.
        mContext.mBufWriteIndex[0] =
            mSession.encryptDataIncrementally(
                mContext.mBuffer,
                (short) 0,
                mContext.mBufWriteIndex[0],
                mScratchPad,
                (short) 0,
                (short) mScratchPad.length);
      } else if (mContext.mRemainingBytes[0] == 0) { // Nothing to encode
        // If there is any buffered data in encryption session then copy that.
        mContext.mBufReadIndex[0] = 0;
        mContext.mBufWriteIndex[0] = mSession.readAndClearBufferedData(mContext.mBuffer, (short) 0);
        mContext.mBufWriteIndex[0] =
            mSession.finishIncrementalEncryption(
                mContext.mBuffer, (short) 0, mContext.mBufWriteIndex[0],
                mScratchPad,
                (short) 0,
                (short) mScratchPad.length);

        mSession.mBuffer[Session.BUF_LENGTH_OFFSET] = 0;
        mContext.mRemainingBytes[0] =
            (short) (mContext.mRemainingBytes[0] - SEProvider.AES_GCM_TAG_LENGTH);
        // Encryption is successful
        mMsgCounter[DEVICE_MSG_COUNTER]++;
      } else {
        // no more data to process
        mContext.mRemainingBytes[0] = 0;
        break;
      }
    }
    sendBytes(apdu, (short) 0, index, status);
  }

  /**
   * IssuerSigned = { ? "nameSpaces" : IssuerNameSpaces, ; Returned data elements "issuerAuth" :
   * IssuerAuth ; Contains the mobile security object (MSO) ; for issuer data authentication } This
   * method only encodes start of map and issuerAuth key.
   */
  short encodeIssuerSigned(CBOREncoder encoder, DocumentRequest request) {
    short start = encoder.getCurrentOffset();
    encoder.encodeRawData(
        mMdlSpecifications.issuerSigned, (short) 0, (short) mMdlSpecifications.issuerSigned.length);
    encoder.startMap((short) 2);
    encoder.encodeRawData(
        mMdlSpecifications.issuerAuth, (short) 0, (short) mMdlSpecifications.issuerAuth.length);
    return (short) (encoder.getCurrentOffset() - start);
  }

  short encodeNs(CBOREncoder encoder, DocumentRequest request, short nsIndex) {
    short start = encoder.getCurrentOffset();
    short length = request.getNsId(nsIndex, mScratchPad, (short) 0);
    encoder.encodeRawData(mScratchPad, (short) 0, (short) length);
    encoder.startArray(request.getElementCountForNs(nsIndex));
    return (short) (encoder.getCurrentOffset() - start);
  }

  // Note: end = start + length
  short encodeBytes(CBOREncoder encoder, byte[] buf, short start, short end) {
    short len = (short) (end - start);
    short bufLen = (short) (encoder.getBufferLength() - encoder.getCurrentOffset());
    // if the buffer is full and more data requires to be encoded then that means that encoding
    // has to be done in next getResponse cycle so throw an exception which will
    // be handled in encodeData
    if (bufLen == 0 && len > 0) {
      ISOException.throwIt(ISO7816.SW_UNKNOWN);
    }
    if (len > bufLen) {
      len = bufLen;
    }
    encoder.encodeRawData(buf, start, len);
    return len;
  }

  /**
   * This is the response specific state machine. Note: This is only valid for Device Response
   * schema specified in Mdl specifications. Every switch case is one state and they do two things:
   * 1) action: Does the action required for the current state - mainly encoding the keys of this
   * node and sometime child nodes. 2) change to next state: Sets transition to next state. The next
   * state can be either root node or child node value.
   */
  private short encodeResponse(CBOREncoder encoder) {
    short len = 0;
    switch (mContext.mIncrementalResponseState[Context.CURRENT_STATE]) {
      case Context.RESP_IDLE:
        return (short) 0;
      case Context.RESP_START:
        // Encodes common device response.
        len = encodeDeviceResponseStartSuccess(encoder, mContext.mDocumentsCount[0]);
        if (mContext.mDocumentsCount[0] > 0) {
          mContext.mIncrementalResponseState[Context.CURRENT_DOC] = getNextDocIndex(INITIAL_DOC_INDEX);
          mContext.mIncrementalResponseState[Context.CURRENT_ELEMENT] = 0;
          mContext.mIncrementalResponseState[Context.CURRENT_NAMESPACE] = 0;
          mContext.mIncrementalResponseState[Context.CURRENT_DATA_PTR_END] = 0;
          mContext.mIncrementalResponseState[Context.CURRENT_DATA_PTR_START] = 0;
          mContext.mIncrementalResponseState[Context.CURRENT_STATE] = Context.RESP_DOCUMENT;
        }
        break;
      case Context.RESP_DOCUMENT:
        {
          short docIndex = mContext.mIncrementalResponseState[Context.CURRENT_DOC];
          if (docIndex < Context.MAX_DOC_REQUESTS) {
            DocumentRequest request = (DocumentRequest) mContext.mDocumentRequests[docIndex];
            if (request == null) {
              ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
            }
            // The package is the main tree from where we want to source the data.
            MdocPresentationPkg pkg = request.getDocument();
            if (pkg == null) {
              ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
            }
            mContext.mDocuments[0] = request;
            len = encodeDocumentStart(encoder, request);
            mContext.mIncrementalResponseState[Context.CURRENT_STATE] = Context.RESP_DEVICE_SIGNED;
          } else {
            mContext.mIncrementalResponseState[Context.CURRENT_DOC] = 0;
            mContext.mDocuments[0] = null;
            mContext.mIncrementalResponseState[Context.CURRENT_STATE] = Context.RESP_IDLE;
          }
        }
        break;
      case Context.RESP_DEVICE_SIGNED:
        {
          len = encodeDeviceSigned(encoder, (DocumentRequest) mContext.mDocuments[0]);
          mContext.mIncrementalResponseState[Context.CURRENT_STATE] = Context.RESP_ISSUER_SIGNED;
        }
        break;
      case Context.RESP_ISSUER_SIGNED:
        {
          DocumentRequest request = (DocumentRequest) mContext.mDocuments[0];
          len = encodeIssuerSigned(encoder, request);
          short authStart = request.getDocument().getIssuerAuthStart();
          short authEnd = (short) (authStart + request.getDocument().getIssuerAuthLength());
          mContext.mIncrementalResponseState[Context.CURRENT_DATA_PTR_START] = authStart;
          mContext.mIncrementalResponseState[Context.CURRENT_DATA_PTR_END] = authEnd;
          mContext.mIncrementalResponseState[Context.CURRENT_STATE] = Context.RESP_ISSUER_AUTH;
        }
        break;
      case Context.RESP_ISSUER_AUTH:
        {
          DocumentRequest request = (DocumentRequest) mContext.mDocuments[0];
          len =
              encodeBytes(
                  encoder,
                  request.getDocument().getBuffer(),
                  mContext.mIncrementalResponseState[Context.CURRENT_DATA_PTR_START],
                  mContext.mIncrementalResponseState[Context.CURRENT_DATA_PTR_END]);
          mContext.mIncrementalResponseState[Context.CURRENT_DATA_PTR_START] += len;
          if (mContext.mIncrementalResponseState[Context.CURRENT_DATA_PTR_START]
              == mContext.mIncrementalResponseState[Context.CURRENT_DATA_PTR_END]) {
            mContext.mIncrementalResponseState[Context.CURRENT_DATA_PTR_START] =
                mContext.mIncrementalResponseState[Context.CURRENT_DATA_PTR_END] = 0;
            if (request.getNsCount() > 0) {
              len +=
                  encoder.encodeRawData(
                      mMdlSpecifications.nameSpaces,
                      (short) 0,
                      (short) mMdlSpecifications.nameSpaces.length);
              short offset = encoder.getCurrentOffset();
              len += (short) (encoder.startMap(request.getNsCount()) - offset);
              mContext.mIncrementalResponseState[Context.CURRENT_STATE] = Context.RESP_ISSUER_NS;
            } else {
              mContext.mIncrementalResponseState[Context.CURRENT_DOC] =
                  getNextDocIndex(mContext.mIncrementalResponseState[Context.CURRENT_DOC]);
              mContext.mIncrementalResponseState[Context.CURRENT_STATE] = Context.RESP_DOCUMENT;
            }
          }
        }
        break;
      case Context.RESP_ISSUER_NS:
        {
          DocumentRequest request = (DocumentRequest) mContext.mDocuments[0];
          short nsIndex = mContext.mIncrementalResponseState[Context.CURRENT_NAMESPACE];
          if (nsIndex < request.getNsCount()) {
            mContext.mIncrementalResponseState[Context.CURRENT_ELEMENT] = 0;
            mContext.mIncrementalResponseState[Context.CURRENT_STATE] = Context.RESP_NS_ELEMENTS;
            mContext.mIncrementalResponseState[Context.CURRENT_ELEMENT] = 0;
            len = encodeNs(encoder, request, nsIndex);
          } else {
            mContext.mIncrementalResponseState[Context.CURRENT_ELEMENT] = 0;
            mContext.mIncrementalResponseState[Context.CURRENT_NAMESPACE] = 0;
            mContext.mIncrementalResponseState[Context.CURRENT_DATA_PTR_END] = 0;
            mContext.mIncrementalResponseState[Context.CURRENT_DATA_PTR_START] = 0;
            mContext.mIncrementalResponseState[Context.CURRENT_DOC] =
                getNextDocIndex(mContext.mIncrementalResponseState[Context.CURRENT_DOC]);
            mContext.mIncrementalResponseState[Context.CURRENT_STATE] = Context.RESP_DOCUMENT;
          }
        }
        break;
      case Context.RESP_NS_ELEMENTS:
        {
          DocumentRequest request = (DocumentRequest) mContext.mDocuments[0];
          short nsIndex = mContext.mIncrementalResponseState[Context.CURRENT_NAMESPACE];
          short elemIndex = mContext.mIncrementalResponseState[Context.CURRENT_ELEMENT];
          if (elemIndex < request.getElementCountForNs(nsIndex)) {
            short elemStart = request.getElementStart(nsIndex, elemIndex);
            short elemEnd = (short) (elemStart + request.getElementLen(nsIndex, elemIndex));
            mContext.mIncrementalResponseState[Context.CURRENT_DATA_PTR_START] = elemStart;
            mContext.mIncrementalResponseState[Context.CURRENT_DATA_PTR_END] = elemEnd;
            mContext.mIncrementalResponseState[Context.CURRENT_STATE] = Context.RESP_NS_ELEMENT;
          } else {
            mContext.mIncrementalResponseState[Context.CURRENT_NAMESPACE]++;
            mContext.mIncrementalResponseState[Context.CURRENT_STATE] = Context.RESP_ISSUER_NS;
          }
        }
        break;
      case Context.RESP_NS_ELEMENT:
        {
          DocumentRequest request = (DocumentRequest) mContext.mDocuments[0];
          len =
              encodeBytes(
                  encoder,
                  request.getDocument().getBuffer(),
                  mContext.mIncrementalResponseState[Context.CURRENT_DATA_PTR_START],
                  mContext.mIncrementalResponseState[Context.CURRENT_DATA_PTR_END]);
          mContext.mIncrementalResponseState[Context.CURRENT_DATA_PTR_START] += len;
          if (mContext.mIncrementalResponseState[Context.CURRENT_DATA_PTR_START]
              == mContext.mIncrementalResponseState[Context.CURRENT_DATA_PTR_END]) {
            mContext.mIncrementalResponseState[Context.CURRENT_DATA_PTR_START] =
                mContext.mIncrementalResponseState[Context.CURRENT_DATA_PTR_END] = 0;
            mContext.mIncrementalResponseState[Context.CURRENT_ELEMENT]++;
            mContext.mIncrementalResponseState[Context.CURRENT_STATE] = Context.RESP_NS_ELEMENTS;
          }
        }
        break;
      default:
        ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        break;
    }
    return len;
  }

  private void sendBytes(APDU apdu, short start, short len, short status) {
    apdu.setOutgoingLength(len);
    apdu.sendBytes(start, len);
    if (status != ISO7816.SW_NO_ERROR) {
      ISOException.throwIt(status);
    } else {
      mContext.reset();
    }
  }

  /**
   * The following method does basic chain validation. 1) Extract tbs, keys, alg, signature from the
   * chain. Also, check whether the last cert in the chain is self signed. 2) Check that alg
   * requested in argument is supported by the key in the first certificate. 3) Then do the chain
   * validation if there are more than one certificate
   *
   * <p>TODO currently key usage validation is not done and also the root cert in the chain is not
   * validated as it is not clear whether the root cert will be self signed and if so whether it
   * needs to be validated or not.
   */
  public boolean validateChain(
      MdocPresentationPkg doc,
      byte[] buf,
      short[] tmpArray,
      byte[] scratch,
      short scratchStart,
      short scratchLen) {
    // store the alg
    short alg = tmpArray[0];
    // store the cert count
    short count = tmpArray[1];
    if (count <= 0) {
      return false;
    }
    // point to the end of the chain i.e. root cert
    short i = (short) ((short) (count * 2) + 2);
    short attKeyStart = -1;
    short attKeyLen = -1;
    boolean foundAtLeastOneKey = false;
    while (i > 2) {
      short certStart = tmpArray[--i];
      short certLen = tmpArray[--i];
      short certEnd = (short) (certStart + certLen);
      // Now decode cert - this will return 4 parameters of the cert in mRetVal
      if (!mX509CertHandler.decodeCert(
          buf, certStart, certEnd, mRetVal, scratch, scratchStart, scratchLen))
        return false;

      if (doc.isMatchingReaderAuthKey(buf, mRetVal[5], mRetVal[6])) {
        foundAtLeastOneKey = true;
      }
      if (count > 1
          && attKeyLen > 0
          && !mSEProvider.validateEcDsaSign(
              buf,
              mRetVal[1],
              mRetVal[2], // tbs
              mRetVal[0], // alg
              scratch,
              mRetVal[3],
              mRetVal[4], // sign
              attKeyStart,
              attKeyLen // attestation key
              )) {
        return false;
      }
      attKeyStart = mRetVal[5];
      attKeyLen = mRetVal[6];
    }
    tmpArray[0] = attKeyLen;
    tmpArray[1] = attKeyStart;
    return foundAtLeastOneKey;
  }

  private boolean processItemsRequest(
      byte[] buf,
      short itemsBytesStart,
      short itemsBytesLen,
      short readerAuthStart,
      short readerAuthLen,
      DocumentRequest req) {

    // Now get the doc type out of the items
    mDecoder.init(buf, itemsBytesStart, itemsBytesLen);
    if (mDecoder.readMajorType(CBORBase.TYPE_TAG)
        != MdlSpecifications.CBOR_SEMANTIC_TAG_ENCODED_CBOR) {
      ISOException.throwIt(ISO7816.SW_DATA_INVALID);
    }
    short itemsLen = mDecoder.readMajorType(CBORBase.TYPE_BYTE_STRING);
    short itemsStart = mDecoder.getCurrentOffset();

    // read items request within this doc request to extract the doc type.
    short[] str = mMdlSpecifications.getStructure(MdlSpecifications.KEY_ITEMS_REQUEST);
    mMdlSpecifications.decodeStructure(str, mStructure, mContext.mBuffer, itemsStart, itemsLen);
    short docTypeStart = mStructure[0];
    short docTypeLen = mStructure[1];
    short nameSpacesStart = mStructure[2];
    short nameSpacesLen = mStructure[3];

    // TODO currently just storing the requestInfo and it is not used.
    short requestInfo = mStructure[4];
    short requestInfoLen = mStructure[5];
    // document with the desired doc type must be present and if it requires reader
    // authentication then reader authentication must be successful.
    if (docTypeStart == (short) 0 || nameSpacesStart == (short) 0) {
      return false;
    }
    docTypeStart += 1; // Ignore Tstr Major type
    docTypeLen -= 1;
    // Now get the document.
    MdocPresentationPkg doc =
        PresentationPkgStore.instance().findPackage(buf, docTypeStart, docTypeLen);
    if (doc != null) {
      // Now initialise the document request which will parse the name spaces and create internal
      // mapping to stored data.
      // Perform readAuth
      if (processReaderAuth(
          req,
          doc,
          mStructure,
          buf,
          itemsBytesStart,
          itemsBytesLen,
          readerAuthStart,
          readerAuthLen,
          mScratchPad,
          (short) 0,
          (short) mScratchPad.length)) {
        return req.init(
            doc,
            mSession,
            buf,
            nameSpacesStart,
            nameSpacesLen,
            mScratchPad,
            (short) 0,
            (short) mScratchPad.length);
      }
    }
    return false;
    // TODO match individual items requested by the doc request with that in the document. If
    // not available then error must be returned for those items or the entire doc request.
  }

  @Override
  public void deselect() {
    reset();
  }

  @Override
  public void process(APDU apdu) {
    byte[] buffer = apdu.getBuffer();
    if (selectingApplet()) {
      return;
    }
    if (apdu.isSecureMessagingCLA()) {
      ISOException.throwIt(ISO7816.SW_SECURE_MESSAGING_NOT_SUPPORTED);
    }
    // process commands to the applet
    if (apdu.isISOInterindustryCLA()) {
      switch (buffer[ISO7816.OFFSET_INS]) {
        case INS_ENVELOPE:
          processEnvelope(apdu);
          break;
        case INS_GET_RESPONSE:
          processGetResponse(apdu, false);
          break;
        default:
          ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
      }
    }
  }

  // -------- MdlService Interface implementation
  @Override
  public Shareable getShareableInterfaceObject(AID clientAID, byte parameter) {
    byte[] buf = new byte[16];
    byte len = clientAID.getBytes(buf, (short) 0);
    switch (parameter) {
      case MdlService.SERVICE_ID:
        if (Util.arrayCompare(buf, (short) 0, AID_NDEF_TAG_APPLET, (short) 0, len) != 0) {
          ISOException.throwIt(ISO7816.SW_WRONG_DATA);
        }
        return this;
      case MdlPresentationPkgStore.SERVICE_ID:
        if (Util.arrayCompare(buf, (short) 0, DIRECT_ACCESS_PROVISIONING_APPLET_ID, (short) 0, len)
            != 0) {
          ISOException.throwIt(ISO7816.SW_WRONG_DATA);
        }
        return mPresentationPkgStore;
      default:
        ISOException.throwIt(ISO7816.SW_WRONG_DATA);
    }
    return null;
  }

  @Override
  public short getHandoverSelectMessage(byte[] buf, short start) {
    if (buf == null
        || (short) buf.length
            < (short)
                (start
                    + HANDOVER_MSG_FIXED_PART.length
                    + DEVICE_ENGAGEMENT_FIXED.length
                    + MdlSpecifications.EC_P256_COSE_KEY_SIZE)) {
      ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
    }

    short offset =
        Util.arrayCopyNonAtomic(
            HANDOVER_MSG_FIXED_PART, (short) 0, buf, start, (byte) HANDOVER_MSG_FIXED_PART.length);
    byte payloadLenOffset = (byte) (offset - MDOC_ID_LEN - MDOC_TYPE_LEN - 2);
    offset =
        Util.arrayCopyNonAtomic(
            DEVICE_ENGAGEMENT_FIXED,
            (short) 0,
            buf,
            offset,
            (short) DEVICE_ENGAGEMENT_FIXED.length);

    // Currently, only p256 EC keys are supported.
    offset = mSession.generateAndAddEDeviceKey_p256(buf, offset);
    short len = (short) (offset - start);
    // Payload length will be always less than 256 for p256 keys.
    buf[payloadLenOffset] = (byte) (len - (short) HANDOVER_MSG_FIXED_PART.length);
    if (buf[payloadLenOffset] > 255) {
      ISOException.throwIt(ISO7816.SW_UNKNOWN);
    }
    Util.arrayCopyNonAtomic(buf, start, mSession.mHandover, (short) 8, len);
    Util.setShort(mSession.mHandover, Session.HANDOVER_MSG_START, (short) 8);
    Util.setShort(mSession.mHandover, Session.HANDOVER_MSG_LEN, len);
    Util.setShort(
        mSession.mHandover,
        Session.DEVICE_ENGAGEMENT_START,
        (short) (HANDOVER_MSG_FIXED_PART.length + 8));
    Util.setShort(mSession.mHandover, Session.DEVICE_ENGAGEMENT_LEN, buf[payloadLenOffset]);
    return len;
  }
}
