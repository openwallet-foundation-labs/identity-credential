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
package com.android.javacard.mdl.provision;

import com.android.javacard.mdl.CBORBase;
import com.android.javacard.mdl.CBORDecoder;
import com.android.javacard.mdl.CBOREncoder;
import com.android.javacard.mdl.MdlSpecifications;
import com.android.javacard.mdl.SEProvider;
import com.android.javacard.mdl.X509CertHandler;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.Util;
import javacard.security.ECPrivateKey;
import javacard.security.ECPublicKey;
import javacard.security.KeyBuilder;
import javacard.security.KeyPair;
import javacardx.apdu.ExtendedLength;

/**
 * ProvisioningApplet provides interface to provision the 18013-5 compliant credential documents
 * into the direct access store. The provisioning client is an application running in Android OS.
 * This application will use Identity Credential subsystem's direct access api to provision the
 * credentials in this applet which running in the SE. There are two steps of this provisioning
 * process: 1. Create the credential store and provision the document: In this step a MDoc instance
 * i.e. slot is reserved, one or more signing keys are generated and for each signing key credential
 * document/data is provisioned which is called a presentation package. The data itself is not
 * stored in MDoc instance but it is simply encrypted (AES GCM encryption) using the MDoc's Storage
 * Key and sent back to the client. No data validation is done. 2. Swap In the presentation package:
 * At some point, application may decide to swap in a presentation package and store it in
 * presentation applet for the direct access by the reader. Data being stored is validated according
 * to schema. At any given time there will be only one copy of presentation package stored in the
 * presentation applet per slot. This storage is done using shareable interface using MDoc.
 * Moreover, this applet also provides custom api to provision attestation key and certificate in
 * factory. This key can be used to attest the credential keys of the MDoc. Note: At some time in
 * future, remote key provisioning specified by KeyMint will be supported to provision these keys.
 * Also, for detailed explanation of credential data, provisioning and usage related functionality,
 * please refer Identity Credential Direct Access design.
 */
public class ProvisioningApplet extends Applet implements ExtendedLength {

  public static final byte[] DIRECT_ACCESS_PROVISIONING_APPLET_ID = {
    (byte) 0xA0, 0x00, 0x00, 0x02, 0x48, 0x00, 0x01, 0x01, 0x01
  };
  public static final short MAX_DOC_TYPE_SIZE = 64;

  // Maximum Size of the document that can be stored per slot is 32K.
  public static final short MAX_MDOC_SIZE = (short) 0x7FFF;
  // APDU instructions
  public static final byte INS_ENVELOPE = (byte) 0xC3;
  public static final byte INS_PROVISION_DATA = (byte) 0x01;
  // Factory provisioning command tags for the attestation keys.
  public static final byte TAG_ATT_PUB_KEY_CERT = 0x01;
  public static final byte TAG_ATT_PRIVATE_KEY = 0x02;
  // Provisioning Commands that corresponds to Direct Access Store's AIDL API.
  public static final byte CMD_MDOC_CREATE = 1;
  public static final byte CMD_MDOC_LOOKUP = 3;
  public static final byte CMD_MDOC_SWAP_IN = 6;
  public static final byte CMD_MDOC_CREATE_PRESENTATION_PKG = 7;
  public static final byte CMD_MDOC_DELETE_CREDENTIAL = 8;
  public static final byte CMD_MDOC_PROVISION_DATA = 9;
  public static final byte CMD_MDOC_SELECT = 10;
  public static final byte CMD_MDOC_GET_INFORMATION = 11;
  public static final byte CMD_MDOC_CLEAR_USAGE_COUNT = 12;
  public static final byte BEGIN = 0;
  public static final byte UPDATE = 1;
  public static final byte FINISH = 2;
  // Maximum number of supported slots. Currently only 1 slot is supported
  private static final byte MAX_DOCUMENTS_SLOTS = 1;
  // Context stores the state information for current incremental operation.
  private static final byte MAX_CONTEXT_SIZE = (short) 16;
  private static final byte CTX_CMD = 0;
  private static final byte CTX_STATUS = 1;
  private static final byte CTX_SELECTED_DOCUMENT = 2;
  private static final byte SELECT_MDOC = 0;

  /**
   * CBOR Map = { 0:CBOR uint version, - MS byte major and LS byte minor versions respectively.
   * 1:CBOR uint maxSlots, 2:CBOR uint maxDocSize, 3:CBOR uint minExtendedApduBufSize, 4: CBOR uint
   * number of pre-allocated slots, - each slot equal to maxDocSize. }
   */
  private static final byte[] INFORMATION = {
    (byte) ((byte) (CBORBase.TYPE_MAP << 5) | (byte) 5),
    0,
    CBORBase.ENCODED_TWO_BYTES,
    0x10,
    0x00,
    1,
    MAX_DOCUMENTS_SLOTS,
    2,
    CBORBase.ENCODED_TWO_BYTES,
    0x7F,
    (byte) 0xFF,
    3,
    CBORBase.ENCODED_TWO_BYTES,
    0x10,
    (byte) 0x00,
    4,
    MAX_DOCUMENTS_SLOTS,
  };

  // Array of slots
  private static MDoc[] mDocuments;
  // Scratch pad
  private static byte[] mScratch;
  // Persistent memory where entire apdu buffer will be copied
  private static byte[] heap;
  private final CBORDecoder mDecoder;
  private final CBOREncoder mEncoder;
  // Internal Context to handle incremental operation
  private final short[] mContext;
  private static short[] mRetVal;
  // There can be more then one slots/documents that can be provisioned - this points to selected
  // document/slot currently being processed.
  private final Object[] mSelectedDocument;
  // Holds the signing key - note we neither generate more then one signing key nor do we store
  // them in the applet. At a time only one signing key is generated, attested and returned back.
  // Client sends multiple requests to generate multiple signing Keys.
  KeyPair mSigningKey;
  SEProvider mSEProvider;
  // Attestation Key variables
  short mStorageIndex;
  short mPublicKeyCertStart;
  short mPublicKeyCertLength;
  byte[] mDataStorage;
  KeyPair mAttestKey;
  X509CertHandler mX509CertHandler;

  private ProvisioningApplet(SEProvider se) {
    mPublicKeyCertLength = mPublicKeyCertStart = mStorageIndex = 0;
    if (se.SIGNING_OPTION == SEProvider.SIGNING_OPTION_FK) {
      mDataStorage = new byte[4096];
      mAttestKey = new KeyPair(KeyPair.ALG_EC_FP, KeyBuilder.LENGTH_EC_FP_256);
      se.initECKey(mAttestKey);
      mRetVal = JCSystem.makeTransientShortArray((short) 10, JCSystem.CLEAR_ON_DESELECT);
      mX509CertHandler = new X509CertHandler();
    }
    mSEProvider = se;

    mScratch =
        JCSystem.makeTransientByteArray(mSEProvider.MAX_SCRATCH_SIZE, JCSystem.CLEAR_ON_DESELECT);
    heap = JCSystem.makeTransientByteArray(mSEProvider.MAX_BUFFER_SIZE, JCSystem.CLEAR_ON_DESELECT);
    mDecoder = new CBORDecoder();
    mEncoder = new CBOREncoder();
    mContext = JCSystem.makeTransientShortArray(MAX_CONTEXT_SIZE, JCSystem.CLEAR_ON_DESELECT);
    mSelectedDocument = JCSystem.makeTransientObjectArray((short) 1, JCSystem.CLEAR_ON_DESELECT);
    mSigningKey = new KeyPair(KeyPair.ALG_EC_FP, KeyBuilder.LENGTH_EC_FP_256);
    // This is necessary as JCOP/SE may require ECKey parameters to be set in the SEProvider.
    mSEProvider.initECKey(mSigningKey);
    mDocuments = new MDoc[MAX_DOCUMENTS_SLOTS];
    for (byte i = 0; i < MAX_DOCUMENTS_SLOTS; i++) {
      mDocuments[i] = new MDoc(i, mSEProvider);
    }
  }

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
    new ProvisioningApplet(se).register();
  }

  // Create the credential document
  private static void createDocument(
      MDoc doc, boolean testCred, byte[] scratch, short start, short len, byte[] docType,
      short docTypeStart, short docTypeLen) {
    // Reserve the available slot
    doc.reserve();
    // Create the document.
    doc.create(MAX_MDOC_SIZE, scratch, start, len, docType, docTypeStart, docTypeLen);
    if (testCred) {
      doc.enableTestCred(scratch, start, len);
    }
  }

  // Delete the credential document
  private static void destroyDocument(MDoc doc) {
    // Release the slot
    doc.release();
    // Delete the internal state
    doc.delete(mScratch, (short) 0, (short) mScratch.length);
  }

  private static MDoc findDocument(byte slot) {
    return mDocuments[slot];
  }

  // Send response apdu
  private static void sendApdu(APDU apdu, short start, short len) {
    apdu.setOutgoing();
    apdu.setOutgoingLength(len);
    apdu.sendBytesLong(heap, start, len);
  }

  @Override
  public void deselect() {
    reset();
  }

  private void reset() {
    mSelectedDocument[0] = null;
    clearShortArray(mContext);
    Util.arrayFillNonAtomic(mScratch, (short) 0, (short) mScratch.length, (byte) 0);
    Util.arrayFillNonAtomic(heap, (short) 0, (short) heap.length, (byte) 0);
  }

  private void clearShortArray(short[] arr) {
    for (short i = 0; i < (short) arr.length; i++) {
      arr[i] = 0;
    }
  }

  @Override
  public boolean select() {
    return true;
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
        case INS_PROVISION_DATA:
          processProvisionData(apdu);
          break;
        default:
          ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
      }
    } else {
      ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
    }
  }

  private void processProvisionData(APDU apdu) {
    if (mSEProvider.SIGNING_OPTION != SEProvider.SIGNING_OPTION_FK) {
      ISOException.throwIt(ISO7816.SW_COMMAND_NOT_ALLOWED);
    }
    byte[] buf = apdu.getBuffer();
    if (buf[ISO7816.OFFSET_P1] != 0
        || buf[ISO7816.OFFSET_P2] != 0) {
      ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
    }
    short recvLen = apdu.setIncomingAndReceive();
    short dataOffset = apdu.getOffsetCdata();
    if (dataOffset != ISO7816.OFFSET_EXT_CDATA) {
      ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
    }
    short index = 0;
    short dataLen = apdu.getIncomingLength();
    while (recvLen > 0 && (index < dataLen)) {
      Util.arrayCopyNonAtomic(buf, dataOffset, mScratch, index, recvLen);
      index += recvLen;
      recvLen = apdu.receiveBytes(dataOffset);
    }
    index = 0;
    // Store attestation
    clearAttestationKey();
    while (index < dataLen) {
      short tag = Util.getShort(mScratch, index);
      index += 2;
      short len = Util.getShort(mScratch, index);
      index += 2;
      switch (tag) {
        case TAG_ATT_PUB_KEY_CERT:
          storeAttestationPublicKeyCert(mScratch, index, len);
          break;
        case TAG_ATT_PRIVATE_KEY:
          storeAttestationPrivateKey(mScratch, index, len);
          break;
        default:
          ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
          break;
      }
      index += len;
    }
  }

  public void clearAttestationKey() {
    if (mDataStorage == null) {
      return;
    }
    Util.arrayFillNonAtomic(mDataStorage, (short) 0, (short) mDataStorage.length, (byte) 0);
    mStorageIndex = 0;
  }

  public void storeAttestationPublicKeyCert(byte[] buf, short start, short len) {
    if (mDataStorage == null) {
      return;
    }
    JCSystem.beginTransaction();
    mPublicKeyCertStart = mStorageIndex;
    mPublicKeyCertLength = len;
    Util.arrayCopyNonAtomic(buf, start, mDataStorage, mStorageIndex, len);
    mStorageIndex = (short) (mStorageIndex + len);
    JCSystem.commitTransaction();
  }

  public void storeAttestationPrivateKey(byte[] buf, short start, short len) {
    if (mDataStorage == null || mAttestKey == null) {
      return;
    }
    JCSystem.beginTransaction();
    ECPrivateKey key = (ECPrivateKey) mAttestKey.getPrivate();
    key.setS(buf, start, len);
    JCSystem.commitTransaction();
  }

  public void receiveIncoming(APDU apdu) {
    byte[] srcBuffer = apdu.getBuffer();
    short recvLen = apdu.setIncomingAndReceive();
    short srcOffset = apdu.getOffsetCdata();
    // TODO add logic to handle the extended length buffer. In this case the memory can be reused
    //  from extended buffer.
    short bufferLength = apdu.getIncomingLength();
    if (bufferLength == 0) {
      ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
    }
    short index = 0;
    while (recvLen > 0 && (index < bufferLength)) {
      Util.arrayCopyNonAtomic(srcBuffer, srcOffset, heap, index, recvLen);
      index += recvLen;
      recvLen = apdu.receiveBytes(srcOffset);
    }
  }

  private void processEnvelope(APDU apdu) {
    // receive bytes into mBuffer
    byte[] buf = apdu.getBuffer();
    if (buf[ISO7816.CLA_ISO7816] != 0x10 && buf[ISO7816.CLA_ISO7816] != 0x00) {
      ISOException.throwIt(ISO7816.SW_RECORD_NOT_FOUND);
    }
    receiveIncoming(apdu);
    handleCommand(apdu);
    if ((short) (mContext[CTX_STATUS] & (short) 0xFF00) == ISO7816.SW_BYTES_REMAINING_00) {
      ISOException.throwIt(mContext[CTX_STATUS]);
    } else {
      reset();
    }
  }

  private void handleCommand(APDU apdu) {
    byte[] buf = apdu.getBuffer();
    short len = apdu.getIncomingLength();
    short start = apdu.getOffsetCdata();
    if (buf[ISO7816.OFFSET_P1] != 0 || buf[ISO7816.OFFSET_P2] != 0) {
      ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
    }
    if (len < 2) {
      ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
    }

    // Applet handles one command at a time. CTX gets cleared when a command completes.
    // handle if it is an mdoc credential command.
    if (!handleMdocCommands(Util.getShort(buf, start), apdu, heap, (short) 2, len)) {
      ISOException.throwIt(ISO7816.SW_DATA_INVALID);
    }
    // else if (other credential commands in future)
  }

  private boolean handleMdocCommands(short cmd, APDU apdu, byte[] buf, short start, short len) {
    switch (cmd) {
      case CMD_MDOC_CREATE: {
        short docStrLen = Util.getShort(buf, start);
        if (docStrLen > MAX_DOC_TYPE_SIZE) {
          ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }
        start += 2;
        handleCreateMdoc(apdu, buf, start, docStrLen);
        break;
      }
      case CMD_MDOC_SELECT:
        // [byte slot]
        handleSelectMdoc(buf[start]); // slot
        break;
      case CMD_MDOC_CREATE_PRESENTATION_PKG:
        {
          // [
          //   byte slot,
          //   short notBeforeLen, byte[] notBefore,
          //   short notAfterLen, byte[] notAfter
          // ]
          if (buf.length < mSEProvider.MAX_BUFFER_SIZE) { // The buffer must be extended length.
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
          }
          byte slot = buf[start++];
          MDoc doc = findDocument(slot);
          if (doc == null) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
          }
          short notBeforeLen = Util.getShort(buf, start);
          start += 2;
          short notBeforeStart = start;
          start += notBeforeLen;
          short notAfterLen = Util.getShort(buf, start);
          start += 2;
          short notAfterStart = start;
          start += notAfterLen;
          short testDevAuthPrivateKeyLen = 0;
          short testDevAuthPrivateKeyStart = 0;
          short testDevAuthPublicKeyStart = 0;
          short testDevAuthPublicKeyLen = 0;
          if (doc.isTestCredential()) {
            testDevAuthPrivateKeyLen = Util.getShort(buf, start);
            start += 2;
            testDevAuthPrivateKeyStart = start;
            start += testDevAuthPrivateKeyLen;
            testDevAuthPublicKeyLen = Util.getShort(buf, start);
            start += 2;
            testDevAuthPublicKeyStart = start;
            start += testDevAuthPublicKeyLen;
          }
          short payLoadLength = start;
          handleCreateAuthKeys(
              apdu,
              slot,
              notBeforeStart,
              notBeforeLen,
              notAfterStart,
              notAfterLen,
              testDevAuthPublicKeyStart,
              testDevAuthPublicKeyLen,
              testDevAuthPrivateKeyStart,
              testDevAuthPrivateKeyLen,
              payLoadLength);
          break;
        }
      case CMD_MDOC_LOOKUP:
        // [byte slot]
        handleLookUpCredential(buf[start]); // slot
        break;
      case CMD_MDOC_DELETE_CREDENTIAL:
        // [byte slot]
        handleDeleteCredential(buf[start]); // slot
        break;
      case CMD_MDOC_GET_INFORMATION:
        {
          // [byte slot] or nothing
          byte slot = (len > start) ? buf[start] : (byte) -1;
          handleGetInformation(apdu, slot);
          break;
        }
      case CMD_MDOC_CLEAR_USAGE_COUNT:
      {
        MDoc doc = findDocument(buf[start]);
        if (doc == null) {
          ISOException.throwIt(ISO7816.SW_RECORD_NOT_FOUND);
        }
        doc.clearUsageCount();
        break;
      }
      case CMD_MDOC_PROVISION_DATA:
        {
          // [byte slot, byte op, short provDataLen, byte[] provDataLen]
          byte slot = buf[start++];
          byte op = buf[start++];
          mDecoder.init(buf, start, len);
          len = mDecoder.readMajorType(CBORBase.TYPE_BYTE_STRING);
          handleProvisionData(apdu, slot, op, mDecoder.getCurrentOffset(), len); // Encrypted data.
        }
        break;

      case CMD_MDOC_SWAP_IN:
        {
          // [byte slot, byte op,short enc_data_len, byte[] encData]
          byte slot = buf[start++];
          byte op = buf[start++];
          mDecoder.init(buf, start, len);
          len = mDecoder.readMajorType(CBORBase.TYPE_BYTE_STRING);
          handleSwapInMdoc(apdu, slot, op, mDecoder.getCurrentOffset(), len); // Encrypted data.
        }
        break;
      default:
        return false;
    }
    return true;
  }

  /**
   * This command can be used for starting, updating and finishing provision. This is indicated by
   * the op parameter. In this operation the input data is in clear text, and it is returned
   * encrypted. It is not stored into the memory. The input data is validated i.e. valid cbor and
   * mdoc values.
   */
  private void handleProvisionData(APDU apdu, byte slot, byte op, short start, short len) {
    MDoc doc = findDocument(slot);
    if (doc == null) {
      ISOException.throwIt(ISO7816.SW_RECORD_NOT_FOUND);
    }
    byte[] buf = heap; // apdu.getBuffer();
    if (op == BEGIN) {
      // Begin uses the data it decrypts it and then re encrypts it such that more data can be
      // encrypted and added using update operation incrementally.
      // Extract the nonce and then decrypt the data - then re-encrypt it.
      // First 12 bytes will be nonce
      short encDataStart = (short) (start + SEProvider.AES_GCM_NONCE_LENGTH);
      short encDataLen = (short) (len - SEProvider.AES_GCM_NONCE_LENGTH);
      // Credential public key is the auth data - copy to scratch
      short credKeyLen = aesGcmAad(slot, mScratch, (short) 0);
      mSEProvider.beginAesGcmOperation(
          doc.getStorageKey(),
          false,
          buf,
          start,
          SEProvider.AES_GCM_NONCE_LENGTH,
          mScratch,
          (short) 0,
          credKeyLen);

      // decrypt data - the decrypted data will be in scratch buffer. Scratch will be sufficiently
      // large to hold slightly greater than 64 bytes of the data.
      short end =
          mSEProvider.encryptDecryptInPlace(
              buf, encDataStart, encDataLen, mScratch, (short) 0, (short) mScratch.length);

      short finalLen =
          mSEProvider.doAesGcmOperation(buf, end, (short) 0, mScratch, (short) 0, false);
      Util.arrayCopyNonAtomic(mScratch, (short) 0, buf, end, finalLen);
      end += finalLen;
      short size = (short) (end - encDataStart);
      Util.arrayCopyNonAtomic(buf, encDataStart, mScratch, (short) 0, size);

      // Validate the decrypted data - array of 2 elements, where first element is byte string
      // and second must be CBOR encoded null - i.e. empty presentation package
      mDecoder.init(mScratch, (short) 0, size);
      if (mDecoder.readMajorType(CBORBase.TYPE_ARRAY) != 2) {
        ISOException.throwIt(ISO7816.SW_DATA_INVALID);
      }
      if (mDecoder.getMajorType() != CBORBase.TYPE_BYTE_STRING) {
        ISOException.throwIt(ISO7816.SW_DATA_INVALID);
      }
      mDecoder.skipEntry();
      if (mDecoder.getRawByte() != CBORBase.ENCODED_NULL) {
        ISOException.throwIt(ISO7816.SW_DATA_INVALID);
      }
      // Now, current offset in decoded stream is pointing to credential data start point, so don't
      // skip one byte. i.e. don't perform mDecoder.increaseOffset((short) 1);
      short decryptedDataLen = mDecoder.getCurrentOffset();

      // Copy the decrypted data back to buf. So now 'encDataStart' will point to
      // array of two elements and 'start' will point to start of new nonce.
      // We do not include encoded null value i.e. one less than decrypted data length.
      Util.arrayCopyNonAtomic(mScratch, (short) 0, buf, encDataStart, decryptedDataLen);

      // Now start new encryption - regenerate the nonce.
      mSEProvider.generateRandomData(buf, start, SEProvider.AES_GCM_NONCE_LENGTH);
      credKeyLen = aesGcmAad(slot, mScratch, (short) 0);
      mSEProvider.beginAesGcmOperation(
          doc.getStorageKey(),
          true,
          buf,
          start,
          SEProvider.AES_GCM_NONCE_LENGTH,
          mScratch,
          (short) 0,
          credKeyLen);

      // We are now ready to add credential data and encrypt it which will come in update and
      // finish calls.
      // Encrypt the current data. The encrypted data can be less then the input data if it is
      // not block aligned.
      end =
          mSEProvider.encryptDecryptInPlace(
              buf, encDataStart, decryptedDataLen, mScratch, (short) 0, (short) mScratch.length);
      // The response data starts at start with nonce.
      len = (short) (end - start);
      // now package the response as byte string - note this will add the header and decrement
      // the start pointer.
      start = addByteStringHeader(buf, start, len);
      len = (short) (end - start);
    } else if (op == FINISH) {
      // Encrypts the remaining data, and it will also have auth tag appended at the end.
      // Encrypt
      short end =
          mSEProvider.encryptDecryptInPlace(
              buf, start, len, mScratch, (short) 0, (short) mScratch.length);
      short finalLen =
          mSEProvider.doAesGcmOperation(buf, end, (short) 0, mScratch, (short) 0, false);
      Util.arrayCopyNonAtomic(mScratch, (short) 0, buf, end, finalLen);
      end += finalLen;
      len = (short) (end - start);
      start = addByteStringHeader(buf, start, len);
      len = (short) (end - start);
    } else if (op == UPDATE) {
      // Update operation encrypts the data and sends it back to client.
      // Encrypt
      short end =
          mSEProvider.encryptDecryptInPlace(
              buf, start, len, mScratch, (short) 0, (short) mScratch.length);
      len = (short) (end - start);
      start = addByteStringHeader(buf, start, len);
      len = (short) (end - start);
    } else {
      ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
    }
    // now package the response as byte string - note this will add the header and decrement
    // the start pointer.
    sendApdu(apdu, start, len);
  }

  private short addByteStringHeader(byte[] buf, short start, short len) {
    mEncoder.init(mScratch, (short) 0, (short) 4);
    short offset = mEncoder.startByteString(len);
    start -= offset;
    Util.arrayCopyNonAtomic(mScratch, (short) 0, buf, start, offset);
    return start;
  }

  /**
   * This command can be used for starting, updating and finishing swap in. This is indicated by the
   * op parameter.
   */
  private void handleSwapInMdoc(APDU apdu, byte slot, byte op, short start, short len) {
    MDoc doc = findDocument(slot);
    if (doc == null) {
      ISOException.throwIt(ISO7816.SW_RECORD_NOT_FOUND);
    }
    byte[] buf = heap; // apdu.getBuffer();
    if (op == BEGIN) {
      // Extract the nonce and then decrypt the data - then start storing it.
      // First 12 bytes will be nonce
      short encDataStart = (short) (start + SEProvider.AES_GCM_NONCE_LENGTH);
      short encDataLen = (short) (len - SEProvider.AES_GCM_NONCE_LENGTH);

      // Credential public key is the auth data - copy to scratch
      short credKeyLen = aesGcmAad(slot, mScratch, (short) 0);
      mSEProvider.beginAesGcmOperation(
          doc.getStorageKey(),
          false,
          buf,
          start,
          SEProvider.AES_GCM_NONCE_LENGTH,
          mScratch,
          (short) 0,
          credKeyLen);
      doc.startProvisioning();

      // decrypt data - the decrypted data will be directly stored in the doc specific flash memory.
      short end =
          mSEProvider.encryptDecryptInPlace(
              buf, encDataStart, encDataLen, mScratch, (short) 0, (short) mScratch.length);
      short size = (short) (end - encDataStart);
      byte[] apduBuf = apdu.getBuffer();
      Util.arrayCopyNonAtomic(buf, encDataStart, apduBuf, (short) 0, size);
      doc.store(apduBuf, (short) 0, size);
      // B
    } else if (op == FINISH) {
      // Decrypts the remaining data, and it will store and then enumerate the data.
      short end =
          mSEProvider.encryptDecryptInPlace(
              buf, start, len, mScratch, (short) 0, (short) mScratch.length);
      short finalLen =
          mSEProvider.doAesGcmOperation(buf, end, (short) 0, mScratch, (short) 0, false);
      Util.arrayCopyNonAtomic(mScratch, (short) 0, buf, end, finalLen);
      end += finalLen;
      short size = (short) (end - start);
      byte[] apduBuf = apdu.getBuffer();
      Util.arrayCopyNonAtomic(buf, start, apduBuf, (short) 0, size);
      doc.store(apduBuf, (short) 0, size);
      // doc.store(buf, start, size);
      doc.commitProvisioning();
    } else if (op == UPDATE) {
      // Update operation decrypts the data and stores it.
      short end =
          mSEProvider.encryptDecryptInPlace(
              buf, start, len, mScratch, (short) 0, (short) mScratch.length);
      short size = (short) (end - start);
      byte[] apduBuf = apdu.getBuffer();
      Util.arrayCopyNonAtomic(buf, start, apduBuf, (short) 0, size);
      doc.store(apduBuf, (short) 0, size);
    } else {
      ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
    }
  }

  /**
   * This command can be used for getting the information about the slot. If no slot is specified
   * then it returns hardware information. Else it returns usage information
   */
  private void handleGetInformation(APDU apdu, byte slot) {
    byte[] buf = heap;
    if (slot < 0) {
      Util.arrayCopyNonAtomic(
          INFORMATION, (short) 0, buf, (short) 0, (short) INFORMATION.length);
      sendApdu(apdu, (short) 0, (short) INFORMATION.length);
    } else {
      MDoc doc = findDocument(slot);
      if (doc == null) {
        ISOException.throwIt(ISO7816.SW_RECORD_NOT_FOUND);
      }
      Util.setShort(buf, (short) 0, doc.getUsageCount());
      sendApdu(apdu, (short) 0, (short) 2);
    }
  }

  /** This command may not be required. */
  private void handleSelectMdoc(byte slot) {
    MDoc doc = findDocument(slot);
    if (doc == null) {
      ISOException.throwIt(ISO7816.SW_RECORD_NOT_FOUND);
    }
    if (mSelectedDocument[SELECT_MDOC] != null) {
      reset();
    }
    mSelectedDocument[SELECT_MDOC] = doc;
    mContext[CTX_SELECTED_DOCUMENT] = SELECT_MDOC;
  }

  private void handleLookUpCredential(byte slot) {
    MDoc doc = findDocument(slot);
    if (doc == null || !doc.isReserved()) {
      ISOException.throwIt(ISO7816.SW_RECORD_NOT_FOUND);
    }
  }

  private void handleDeleteCredential(byte slot) {
    MDoc doc = findDocument(slot);
    if (doc == null) {
      ISOException.throwIt(ISO7816.SW_RECORD_NOT_FOUND);
    }
    destroyDocument(doc);
  }

  private void handleCreateMdoc(APDU apdu, byte[] docType, short docTypeStart, short docTypeLen) {
    // Find next available slot
    byte slot = 0;
    MDoc doc = null;
    for (; slot < MAX_DOCUMENTS_SLOTS; ++slot) {
      doc = mDocuments[slot];
      if (!doc.isReserved()) {
        break;
      }
    }
    if (slot == MAX_DOCUMENTS_SLOTS) {
      // no slots available
      ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
    }
    byte[] apduBuf = apdu.getBuffer();
    Util.arrayCopyNonAtomic(docType, docTypeStart, apduBuf, (short) 0, docTypeLen);
    createDocument(doc, false, mScratch, (short) 0, (short) mScratch.length, apduBuf,
            (short) 0, docTypeLen);
    mContext[CTX_STATUS] = 0;
    apduBuf[0] = slot;
    sendApdu(apdu, (short) 0, (short) 1);
  }

  private short aesGcmAad(byte slot, byte[] buf, short start) {
    switch (mSEProvider.SIGNING_OPTION) {
      case SEProvider.SIGNING_OPTION_FK:
      {
        mX509CertHandler.decodeCert(mDataStorage, mPublicKeyCertStart, mPublicKeyCertLength,
                mRetVal, buf, start, (short) (buf.length - start));
        short offset = start;
        buf[offset++] = slot;
        // retVal[5] = publicKeyStart; retVal[6] = publicKeyLen;
        Util.arrayCopyNonAtomic(mDataStorage, mRetVal[5], buf, offset, mRetVal[6]);
        offset += mRetVal[6];
        return (short) (offset - start);
      }
      case SEProvider.SIGNING_OPTION_CASD:
        // todo
        break;
      default:
        ISOException.throwIt(ISO7816.SW_DATA_INVALID);
    }
    return (short) 0;
  }

  private void handleCreateAuthKeys(
      APDU apdu,
      byte slot,
      short notBeforeStart,
      short notBeforeLen,
      short notAfterStart,
      short notAfterLen,
      short testDevAuthPublicKeyStart,
      short testDevAuthPublicKeyLen,
      short testDevAuthPrivateKeyStart,
      short testDevAuthPrivateKeyLen,
      short payloadLen) {
    MDoc doc = findDocument(slot);
    if (doc == null) {
      ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
    }

    byte[] buf = heap;
    // Generate the key pair
    mSigningKey.genKeyPair();
    // if the doc is test credential then client can provide test auth keys
    if (doc.isTestCredential()) {
      if (testDevAuthPrivateKeyLen > 0) {
        ((ECPrivateKey) mSigningKey.getPrivate())
            .setS(buf, testDevAuthPrivateKeyStart, testDevAuthPrivateKeyLen);
      }
      if (testDevAuthPublicKeyLen > 0) {
        ((ECPublicKey) mSigningKey.getPublic())
            .setW(buf, testDevAuthPublicKeyStart, testDevAuthPublicKeyLen);
      }
    }
    // Skip the input payload
    short start = payloadLen;
    mEncoder.init(buf, start, (short) (mSEProvider.MAX_BUFFER_SIZE - payloadLen));
    mEncoder.startMap((short) 2);

    // Add the Certificate
    mEncoder.encodeUInt8(MdlSpecifications.KEY_CERT);
    // Generate and add cert - cert should not exceed 512 bytes.
    short certStart = (short) (mSEProvider.MAX_BUFFER_SIZE - SEProvider.SIGNING_CERT_MAX_SIZE);
    short certLen = 0;
    if (mSEProvider.SIGNING_OPTION == SEProvider.SIGNING_OPTION_FK) {
      certLen =
              mSEProvider.generateSigningKeyCert(
                      (ECPublicKey) (mSigningKey.getPublic()),
                      (ECPrivateKey) (mAttestKey.getPrivate()),
                      buf,
                      notBeforeStart,
                      notBeforeLen,
                      buf,
                      notAfterStart,
                      notAfterLen,
                      buf,
                      certStart,
                      SEProvider.SIGNING_CERT_MAX_SIZE,
                      mScratch,
                      (short) 0,
                      (short) (mScratch.length));
    } else {
      // todo do signing with CASD
      ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
    }
    mEncoder.startByteString(certLen);
    Util.arrayCopyNonAtomic(buf, certStart, buf, mEncoder.getCurrentOffset(), certLen);
    mEncoder.increaseOffset(certLen);

    // Encrypt and add data.
    // Start the encrypt operation
    mSEProvider.generateRandomData(mScratch, (short) 0, SEProvider.AES_GCM_NONCE_LENGTH);
    short size = aesGcmAad(slot, mScratch, SEProvider.AES_GCM_NONCE_LENGTH);
    mSEProvider.beginAesGcmOperation(
        doc.getStorageKey(),
        true,
        mScratch,
        (short) 0,
        SEProvider.AES_GCM_NONCE_LENGTH,
        mScratch,
        SEProvider.AES_GCM_NONCE_LENGTH,
        size);

    // Encode the input data which is an array of 2 elements. We also add nonce in the front before
    // the array.
    mEncoder.encodeUInt8(MdlSpecifications.KEY_ENC_DATA);
    // len will be always 64 = 12 (nonce) + 32 (private key) + 16 (tag) + 1 (Array header) +
    // 2 (priv key bstr header) + 1 (encoded null)
    mEncoder.startByteString((short) 64);
    Util.arrayCopyNonAtomic(
        mScratch, (short) 0, buf, mEncoder.getCurrentOffset(), SEProvider.AES_GCM_NONCE_LENGTH);
    mEncoder.increaseOffset(SEProvider.AES_GCM_NONCE_LENGTH);
    short dataStart = mEncoder.getCurrentOffset();
    mEncoder.startArray((short) 2);
    // first element is the private key as byte string
    size = ((ECPrivateKey) mSigningKey.getPrivate()).getS(mScratch, (short) 0);
    mEncoder.encodeByteString(mScratch, (short) 0, size);
    // second element is the credential data which is null at this stage so add cbor null type/value
    buf[mEncoder.getCurrentOffset()] = CBORBase.ENCODED_NULL;
    mEncoder.increaseOffset((short) 1);
    short dataLen = (short) (mEncoder.getCurrentOffset() - dataStart);
    // encrypt the data in place.
    short dataEnd =
        mSEProvider.encryptDecryptInPlace(
            buf, dataStart, dataLen, mScratch, (short) 0, (short) mScratch.length);
    // Finish and add the auth tag
    short finalLen =
        mSEProvider.doAesGcmOperation(buf, dataEnd, (short) 0, mScratch, (short) 0, false);
    Util.arrayCopyNonAtomic(mScratch, (short) 0, buf, dataEnd, finalLen);
    dataEnd += finalLen;
    short len = (short) (dataEnd - dataStart + SEProvider.AES_GCM_NONCE_LENGTH);
    if (len != 64) { // this should never happen
      ISOException.throwIt(ISO7816.SW_UNKNOWN);
    }
    sendApdu(apdu, start, (short) (dataEnd - start));
  }
}
