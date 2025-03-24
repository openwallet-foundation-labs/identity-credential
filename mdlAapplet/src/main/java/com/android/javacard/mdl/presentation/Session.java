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
import com.android.javacard.mdl.MdlSpecifications;
import com.android.javacard.mdl.SEProvider;
import javacard.framework.APDU;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.Util;
import javacard.security.AESKey;
import javacard.security.CryptoException;
import javacard.security.ECPrivateKey;
import javacard.security.ECPublicKey;
import javacard.security.HMACKey;
import javacard.security.KeyAgreement;
import javacard.security.KeyBuilder;
import javacard.security.KeyPair;
import javacard.security.MessageDigest;
import javacard.security.Signature;

/**
 * This class stores session transcript and other session specific information to comply with secure
 * session requirements of ISO 18013-5 specifications.
 */
public class Session {

  static final byte HANDOVER_MSG_START = 0;
  static final byte HANDOVER_MSG_LEN = 2;
  static final byte DEVICE_ENGAGEMENT_START = 4;
  static final byte DEVICE_ENGAGEMENT_LEN = 6;
  static final byte BUF_LENGTH_OFFSET = 16;
  static final byte SALT_LEN = 32;
  // Reader Encoded Bytes - COSE key size + 4 bytes for semantic tag and binary str tags + 2
  // bytes of len field
  static final short READER_ENC_KEY_BYTES_BUF_SIZE = 81;
  static final short MAX_HANDOVER_MSG_SIZE = 256;

  byte[] mBuffer;
  // Holds the length of the input data buffered by the AES-GCM crypto algorithm.
  byte[] mAesGcmInternalBufferLen;
  KeyPair mECp256DeviceKeys;
  AESKey mDeviceKey;
  AESKey mReaderKey;
  HMACKey mHmacKey;
  Signature mHmacSigner;
  KeyAgreement mKeyAgreement;
  ECPublicKey mEReaderKeyPub;
  byte[] mReaderEncodedKeyBytes;
  byte[] mSessionTranscriptBytes;
  // Stores the device engagement information i.e. handover select message. The first 8 bytes are:
  // Start and length of handover select msg, Start and length of device engagement followed by
  // handover select msg that includes device engagement structure.
  byte[] mHandover;
  ECPrivateKey mDevicePrivateKey;

  CBORDecoder mDecoder;
  CBOREncoder mEncoder;
  short[] mStructure;
  byte[] mSalt;
  SEProvider mSEProvider;
  MdlSpecifications mMdlSpecifications;

  public Session(SEProvider se, MdlSpecifications mdlSpecs) {
    mAesGcmInternalBufferLen = JCSystem.makeTransientByteArray((short) 1, JCSystem.CLEAR_ON_DESELECT);
    mBuffer =
        JCSystem.makeTransientByteArray(
            (short) (BUF_LENGTH_OFFSET + 1), JCSystem.CLEAR_ON_DESELECT);
    mSEProvider = se;
    mMdlSpecifications = mdlSpecs;
    mDecoder = new CBORDecoder();
    mEncoder = new CBOREncoder();
    mStructure = JCSystem.makeTransientShortArray((short) 32, JCSystem.CLEAR_ON_DESELECT);
    mECp256DeviceKeys = new KeyPair(KeyPair.ALG_EC_FP, KeyBuilder.LENGTH_EC_FP_256);
    mSEProvider.initECKey(mECp256DeviceKeys);
    mHandover = JCSystem.makeTransientByteArray(MAX_HANDOVER_MSG_SIZE, JCSystem.CLEAR_ON_RESET);
    mReaderEncodedKeyBytes =
        JCSystem.makeTransientByteArray(READER_ENC_KEY_BYTES_BUF_SIZE, JCSystem.CLEAR_ON_DESELECT);
    mDevicePrivateKey =
        (ECPrivateKey)
            KeyBuilder.buildKey(KeyBuilder.TYPE_EC_FP_PRIVATE, KeyBuilder.LENGTH_EC_FP_256, false);

    // TODO for JCOP we can replace the following with transient memory using alternative buildKey
    // method.
    mDeviceKey =
        (AESKey)
            KeyBuilder.buildKey(
                KeyBuilder.TYPE_AES_TRANSIENT_DESELECT, KeyBuilder.LENGTH_AES_256, false);
    mReaderKey =
        (AESKey)
            KeyBuilder.buildKey(
                KeyBuilder.TYPE_AES_TRANSIENT_DESELECT, KeyBuilder.LENGTH_AES_256, false);
    mHmacKey =
        (HMACKey) KeyBuilder.buildKey(KeyBuilder.TYPE_HMAC_TRANSIENT_RESET, (short) 512, false);
    mEReaderKeyPub =
        (ECPublicKey)
            KeyBuilder.buildKey(KeyBuilder.TYPE_EC_FP_PUBLIC, KeyBuilder.LENGTH_EC_FP_256, false);
    mSEProvider.initEcPublicKey(mEReaderKeyPub);
    mSessionTranscriptBytes =
        JCSystem.makeTransientByteArray(
            MdlSpecifications.MAX_SESSION_TRANSCRIPT_SIZE, JCSystem.CLEAR_ON_DESELECT);
    mHmacSigner = Signature.getInstance(Signature.ALG_HMAC_SHA_256, false);
    mKeyAgreement = KeyAgreement.getInstance(KeyAgreement.ALG_EC_SVDP_DH_PLAIN, false);
    mSalt = JCSystem.makeTransientByteArray(SALT_LEN, JCSystem.CLEAR_ON_DESELECT);
  }

  public void reset() {
    Util.arrayFillNonAtomic(
        mSessionTranscriptBytes,
        (short) 0,
        MdlSpecifications.MAX_SESSION_TRANSCRIPT_SIZE,
        (byte) 0);
    // TODO reset keys
  }

  private void readerKey(byte[] scratchPad, short scratchOffset, short scratchLen) {
    // SEProvider.print(mReaderEncodedKeyBytes, (short) 2, Util.getShort(mReaderEncodedKeyBytes,
    //    (short) 0));
    short offset =
        coseKey(
            mReaderEncodedKeyBytes,
            (short) 2,
            Util.getShort(mReaderEncodedKeyBytes, (short) 0),
            MdlSpecifications.KEY_EREADER_KEY,
            mStructure);
    short xCordOffset = mStructure[4];
    short xCordLen = mStructure[5];
    short yCordOffset = mStructure[6];
    short yCordLen = mStructure[7];

    if ((short) (xCordLen + yCordLen + 1) > scratchLen) {
      ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
    }
    offset = scratchOffset;
    scratchPad[offset++] = (byte) 0x04;
    offset += copyByteString(mReaderEncodedKeyBytes, xCordOffset, xCordLen, scratchPad, offset);
    offset += copyByteString(mReaderEncodedKeyBytes, yCordOffset, yCordLen, scratchPad, offset);

    // SEProvider.print(scratchPad, scratchOffset, (short) (offset - scratchOffset));
    mEReaderKeyPub.clearKey();
    mEReaderKeyPub.setW(scratchPad, scratchOffset, (short) (offset - scratchOffset));
  }

  Signature computeSessionDataAndSecrets() {
    // Scratch pad is required, and it should be at least 256 bytes.
    // 64 bytes for secret 1 and 2, 32 bytes for secret 3,
    // 32 bytes for salt.
    // 96 bytes are extra.
    byte[] scratchPad = getScratchPad(mStructure);
    short scratchOff = mStructure[0];
    short scratchLen = mStructure[1];

    if (scratchPad.length < 256) {
      ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
    }
    // Then generate session transcript and salt which is used by all further derivations.
    encodeSessionTranscript(mSessionTranscriptBytes, mHandover, mReaderEncodedKeyBytes);
    try {
      // Create reader ephemeral key used for ECDH shared secret generation
      readerKey(scratchPad, scratchOff, scratchLen);
      // Now, generate device and reader symmetric cipher keys.
      // Prepare scratch pad.
      short saltLen = 0;
      short secretStart = scratchOff;
      short secretLen = 0;
      short outStart = (short) (secretStart + 32);
      short pubStart = (short) (outStart + 32);
      short pubLen = 0;
      Util.arrayFillNonAtomic(scratchPad, scratchOff, (short) 256, (byte) 0);
      // Generate salt
      saltLen =
          messageDigestSha256(
              mSessionTranscriptBytes,
              (short) 2,
              Util.getShort(mSessionTranscriptBytes, (short) 0),
              mSalt,
              (short) 0);
      if (saltLen != SALT_LEN) {
        ISOException.throwIt(ISO7816.SW_UNKNOWN);
      }

      // Derive shared secret, that will be used for both SKReader and SKDevice derivations
      mKeyAgreement.init(mECp256DeviceKeys.getPrivate());
      pubLen = mEReaderKeyPub.getW(scratchPad, pubStart);
      secretLen =
          mKeyAgreement.generateSecret(scratchPad, pubStart, pubLen, scratchPad, secretStart);
      // Derive SKDevice and SKReader using hkdf.
      hkdf(
          scratchPad,
          secretStart,
          secretLen,
          mMdlSpecifications.deviceSecretInfo,
          scratchPad,
          outStart);
      mDeviceKey.setKey(scratchPad, outStart);
      hkdf(
          scratchPad,
          secretStart,
          secretLen,
          mMdlSpecifications.readerSecretInfo,
          scratchPad,
          outStart);
      mReaderKey.setKey(scratchPad, outStart);
    } catch (Exception exp) {
      ISOException.throwIt(ISO7816.SW_UNKNOWN);
    }
    return mHmacSigner;
  }

  public short hkdf(
      byte[] secret, short secretStart, short secretLen, byte[] info, byte[] out, short outStart) {
    return hkdf(
        mSalt,
        (short) 0,
        (short) mSalt.length,
        secret,
        secretStart,
        secretLen,
        info,
        out,
        outStart);
  }

  public short hkdf(
      byte[] salt,
      short saltStart,
      short saltLen,
      byte[] secret,
      short secretStart,
      short secretLen,
      byte[] info,
      byte[] out,
      short outStart) {
    mHmacKey.setKey(salt, saltStart, saltLen);
    mHmacSigner.init(mHmacKey, Signature.MODE_SIGN);
    short outLen = mHmacSigner.sign(secret, secretStart, secretLen, out, outStart);
    // Now expand the prk. In out case N = L/DigestLEn = 32/32 = 1. Thus, we have only one
    // iteration i.e. T[1] = T[0] || info || counter, where T[0] is zero length.
    mHmacKey.setKey(out, outStart, outLen);
    mHmacSigner.init(mHmacKey, Signature.MODE_SIGN);
    // derive device key
    mHmacSigner.update(info, (short) 0, (short) info.length);
    out[outStart] = (byte) 1;
    outLen = mHmacSigner.sign(out, outStart, (byte) 1, out, outStart);
    return outLen;
  }

  public short messageDigestSha256(
      byte[] input, short inputStart, short inputLen, byte[] out, short outStart) {
    MessageDigest mDigest = null;
    short len = 0;
    try {
      mDigest = MessageDigest.getInitializedMessageDigestInstance(MessageDigest.ALG_SHA_256, false);
      len = mDigest.doFinal(input, inputStart, inputLen, out, outStart);
    } catch (Exception e) {
      ISOException.throwIt(ISO7816.SW_UNKNOWN);
    }
    return len;
  }

  byte[] getScratchPad(short[] retVal) {
    byte[] buffer = APDU.getCurrentAPDUBuffer();
    retVal[0] = (short) 0;
    retVal[1] = (short) 512;
    return buffer;
  }

  /*
   * SessionTranscript = [
   *  DeviceEngagementBytes,
   *  EReaderKeyBytes,
   *  Handover
   *  ]
   *  Handover = QRHandover / NFCHandover / any
   *      QRHandover= nil
   *  NFCHandover = [
   *  bstr of handoverSelectMsg
   *  ]
   *  SessionTranscriptBytes = #6.24(bstr .cbor SessionTranscript)
   *  DeviceEngagementBytes = #6.24(bstr .cbor DeviceEngagement in handoverSelectMsg)
   *  EReaderKeyBytes = encodedReaderKeyBytes
   */
  private void encodeSessionTranscript(
      byte[] sessionTranscript, byte[] handoverSelectMsg, byte[] encodedReaderKeyBytes) {
    short readerKeyBytesLen = Util.getShort(encodedReaderKeyBytes, (short) 0);
    short deviceEngBytesLen = Util.getShort(handoverSelectMsg, DEVICE_ENGAGEMENT_LEN);
    short deviceEngBytesStart = Util.getShort(handoverSelectMsg, DEVICE_ENGAGEMENT_START);
    short handoverSelectMsgLen = Util.getShort(handoverSelectMsg, HANDOVER_MSG_LEN);
    short handoverSelectMsgStart = Util.getShort(handoverSelectMsg, HANDOVER_MSG_START);
    short totalLen = (short) (deviceEngBytesLen + readerKeyBytesLen + handoverSelectMsgLen);

    // add cbor overheads
    totalLen++; // cbor overhead of SessionTranscript which is an array or 3 elements = 1 byte
    totalLen += (short) 4; // cbor overhead of DeviceEngagementBytes - will always be > 23 and < 256
    totalLen += (short) 4; // cbor overhead of Handover Array with bstr and 0xF6 - will
    // always be > 23 and < 256.

    short length = Util.getShort(sessionTranscript, (short) 0);
    if (length != 0 || (short) (totalLen + 3) > (short) sessionTranscript.length) {
      ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
    }

    // Add SessionTranscriptBytes = #6.24(bstr .cbor SessionTranscript)
    short offset = (short) 2;
    sessionTranscript[offset++] =
        (byte) (MdlSpecifications.CBOR_SEMANTIC_TAG | MdlSpecifications.CBOR_UINT8_LENGTH);
    sessionTranscript[offset++] = (byte) (MdlSpecifications.CBOR_SEMANTIC_TAG_ENCODED_CBOR);
    if (totalLen > 255) {
      sessionTranscript[offset++] =
          (byte) (MdlSpecifications.CBOR_BINARY_STR | MdlSpecifications.CBOR_UINT16_LENGTH);
      Util.setShort(sessionTranscript, offset, totalLen);
      offset += 2;
    } else {
      sessionTranscript[offset++] =
          (byte) (MdlSpecifications.CBOR_BINARY_STR | MdlSpecifications.CBOR_UINT8_LENGTH);
      sessionTranscript[offset++] = (byte) totalLen;
    }
    // Add SessionTranscript Array of 3 elements
    sessionTranscript[offset++] = (byte) (MdlSpecifications.CBOR_ARRAY | (byte) 3);

    // First element is DeviceEngagementBytes = #6.24(bstr .cbor DeviceEngagement in
    // handoverSelectMsg)
    sessionTranscript[offset++] =
        (byte) (MdlSpecifications.CBOR_SEMANTIC_TAG | MdlSpecifications.CBOR_UINT8_LENGTH);
    sessionTranscript[offset++] = (byte) (MdlSpecifications.CBOR_SEMANTIC_TAG_ENCODED_CBOR);
    sessionTranscript[offset++] =
        (byte) (MdlSpecifications.CBOR_BINARY_STR | MdlSpecifications.CBOR_UINT8_LENGTH);
    sessionTranscript[offset++] = (byte) deviceEngBytesLen;
    // copy DeviceEngagement from handoverSelectMsg select message
    offset =
        Util.arrayCopyNonAtomic(
            handoverSelectMsg, deviceEngBytesStart, sessionTranscript, offset, deviceEngBytesLen);

    // Second element is EReaderKeyBytes which is same as encodedReaderKeyBytes.
    offset =
        Util.arrayCopyNonAtomic(
            encodedReaderKeyBytes, (short) 2, sessionTranscript, offset, readerKeyBytesLen);

    // Third element is bstr .cbor handoverSelectMsg
    sessionTranscript[offset++] = (byte) (MdlSpecifications.CBOR_ARRAY | (byte) 2);
    sessionTranscript[offset++] =
        (byte) (MdlSpecifications.CBOR_BINARY_STR | MdlSpecifications.CBOR_UINT8_LENGTH);
    sessionTranscript[offset++] = (byte) handoverSelectMsgLen;
    offset =
        Util.arrayCopyNonAtomic(
            handoverSelectMsg,
            handoverSelectMsgStart,
            sessionTranscript,
            offset,
            handoverSelectMsgLen);
    sessionTranscript[offset++] = (byte) 0xF6; // simple value 22.
    // Finally, update the session transcript length.
    Util.setShort(sessionTranscript, (short) 0, (short) (offset - 2));
  }

  boolean isSessionInitialized() {
    return Util.getShort(mSessionTranscriptBytes, (short) 0) > 0;
  }

  short encryptDecryptData(
      byte[] mBuffer,
      short dataStart,
      short dataLen,
      AESKey key,
      short counter,
      boolean encrypt,
      short[] retVal,
      boolean justUpdate) {
    byte[] scratch = getScratchPad(retVal);
    short scratchStart = retVal[0];
    short nonceLen = generateNonce(encrypt, counter, scratch, scratchStart);
    if (encrypt) {
      return mSEProvider.aesGCMEncryptOneShot(
          key,
          mBuffer,
          dataStart,
          dataLen,
          mBuffer,
          dataStart,
          scratch,
          scratchStart,
          nonceLen,
          null,
          (short) 0,
          (short) 0,
          justUpdate);
    } else {
      return mSEProvider.aesGCMDecryptOneShot(
          key,
          mBuffer,
          dataStart,
          dataLen,
          mBuffer,
          dataStart,
          scratch,
          scratchStart,
          nonceLen,
          null,
          (short) 0,
          (short) 0,
          justUpdate);
    }
  }

  void beginIncrementalEncryption(
      short counter, byte[] scratch, short scratchStart, short scratchLen) {
    Util.arrayFillNonAtomic(scratch, scratchStart, SEProvider.AES_GCM_NONCE_LENGTH, (byte) 0);
    scratch[(short) (scratchStart + 7)] = 1;
    Util.setShort(scratch, (short) (scratchStart + 10), counter);
    mBuffer[BUF_LENGTH_OFFSET] = 0;
    mAesGcmInternalBufferLen[0] = 0;
    mSEProvider.beginAesGcmOperation(
        mDeviceKey,
        true,
        scratch,
        scratchStart,
        SEProvider.AES_GCM_NONCE_LENGTH,
        null,
        (short) 0,
        (short) 0);
  }

  /**
   * This method incrementally encrypts the data in place. It expects that incoming buffer has at
   * least 16 bytes (one block) extra at end i.e. len = data length + 16;.
   */
  short encryptDataIncrementally(
      byte[] buf, short start, short len, byte[] scratch, short scratchStart, short scratchLen) {
    // If the data is not block aligned then buffer the non block aligned bytes and make the
    // input block aligned.
    byte rem = (byte) (len % 16);
    short dataEnd = (short) (start + len - rem);
    // Copy the non block aligned data into the buffer.
    Util.arrayCopyNonAtomic(buf, dataEnd, mBuffer, (short) 0, rem);
    mBuffer[BUF_LENGTH_OFFSET] = rem;

    // encrypt in place
    len -= rem;
    scratchLen -= (scratchLen % 16);
    dataEnd = mSEProvider.encryptDecryptInPlace(buf, start, len, scratch, scratchStart, scratchLen);
    if ((short) (start + len) > dataEnd) {
      mAesGcmInternalBufferLen[0] += (byte) ((start + len) - dataEnd);
    } else {
      mAesGcmInternalBufferLen[0] -= (byte) (dataEnd - (start + len));
    }
    return (short) (dataEnd - start);
  }

  short finishIncrementalEncryption(byte[] buf, short start, short len, byte[] scratch,
      short scratchStart, short scratchLen) {
    Util.arrayCopyNonAtomic(buf, start, scratch, scratchStart, len);
    start += mSEProvider.doAesGcmOperation(scratch, scratchStart, len, buf, start, false);
    mAesGcmInternalBufferLen[0] = 0;
    return start;
  }

  private short generateNonce(boolean encrypt, short counter, byte[] scratch, short scratchStart) {
    byte zeros = (byte) (SEProvider.AES_GCM_NONCE_LENGTH - 2);
    Util.arrayFillNonAtomic(scratch, scratchStart, zeros, (byte) 0);
    scratch[(short) (scratchStart + 7)] = (byte) (encrypt ? 1 : 0);
    Util.setShort(scratch, (short) (scratchStart + zeros), counter);
    return SEProvider.AES_GCM_NONCE_LENGTH;
  }

  public short coseKey(byte[] buffer, short start, short len, short keyId, short[] structure) {
    if (buffer[start++] != (byte) 0xD8
        || buffer[start++] != MdlSpecifications.CBOR_SEMANTIC_TAG_ENCODED_CBOR) {
      ISOException.throwIt(ISO7816.SW_DATA_INVALID);
    }
    len -= 2;
    mDecoder.init(buffer, start, len);
    len = mDecoder.readMajorType(CBORBase.TYPE_BYTE_STRING);
    short offset = mDecoder.getCurrentOffset();

    short[] type = mMdlSpecifications.getStructure(keyId);
    offset = mMdlSpecifications.decodeStructure(type, structure, buffer, offset, len);
    // TODO introduce fixed value in structure decoding rule - this eliminate need for the
    // following if else
    if (buffer[structure[0]] != MdlSpecifications.COSE_KEY_KTY_VAL_EC2
        || structure[1] != (byte) 1
        || buffer[structure[2]] != MdlSpecifications.COSE_KEY_CRV_VAL_EC2_P256
        || structure[3] != 1
        || structure[4] == 0
        || structure[5] != 34
        || structure[6] == 0
        || structure[7] != 34) {
      ISOException.throwIt(ISO7816.SW_DATA_INVALID);
    }
    return offset;
  }

  public short copyByteString(byte[] buf, short index, short len, byte[] out, short start) {
    mDecoder.init(buf, index, len);
    if (mDecoder.getMajorType() != CBORBase.TYPE_BYTE_STRING) {
      ISOException.throwIt(ISO7816.SW_DATA_INVALID);
    }
    short length = mDecoder.readLength();
    length =
        (short)
            (Util.arrayCopyNonAtomic(
                    mDecoder.getBuffer(), mDecoder.getCurrentOffset(), out, start, length)
                - start);
    mDecoder.increaseOffset(length);
    return length;
  }

  /* The tag payload is a CBOR Byte string containing Cose Key structure of some length which
     will be always > 23 but less than 256 and hence short encoding can be used which will be
     defined by the device management data 0x58, INVALID_VALUE, The Cose Key structure for
     ephemeral session key is a map of 4 elements i.e. 0xA4. The four elements are as follows:
       1. Key Type (1): EC2 (2) - both CBOR unsigned int - 01, 02,
       2. EC2 Curve Type (-1): P_256 (1) - the negative CBOR i.e. major type 2 and -1 is 0x20 -
          20,01,
       3. EC2 Key X co-ord (-2 i.e. 0x21) - CBOR Byte String of X Co-ord
       4. EC2 Key Y co-ord (-3 i.e. 0x22) - CBOR Byte String of Y Co-ord
  */
  short generateAndAddEDeviceKey_p256(byte[] buf, short offset) {
    mECp256DeviceKeys.genKeyPair();
    ECPublicKey pub = (ECPublicKey) mECp256DeviceKeys.getPublic();
    // Write Byte string with 23 < len < 256 i.e. short encoding
    buf[offset++] =
        (byte) (MdlSpecifications.CBOR_BINARY_STR | MdlSpecifications.CBOR_MAX_256_BYTES);
    // length offset - fill this later
    short lenOff = offset;
    offset++;
    // Cbor Map of 4 elements
    buf[offset++] = (byte) 0xA4;
    // Write first key:value pair i.e.  01:02
    buf[offset++] = 0x01;
    buf[offset++] = 0x02;
    // Write second pair
    buf[offset++] = (byte) (MdlSpecifications.CBOR_NEG_INT | (byte) (0));
    buf[offset++] = (byte) MdlSpecifications.COSE_KEY_CRV_VAL_EC2_P256;
    // Third pair i.e. X Cord of the Key = 0x21 i.e. -2
    buf[offset++] = (byte) (MdlSpecifications.CBOR_NEG_INT | (byte) (2 - 1));
    // Byte String which will between 23 and 256 - short encoding
    buf[offset++] =
        (byte) (MdlSpecifications.CBOR_BINARY_STR | MdlSpecifications.CBOR_MAX_256_BYTES);
    // Get X9.62 formatted uncompressed octet string of the point. This is generally
    // 0x04 || {32 bytes of x cord} || {32 bytes of y cord}
    byte len = (byte) pub.getW(buf, offset); // len = 2 * coordLen + 1
    len = (byte) ((len - 1) / 2); // co-ordinate len
    buf[offset++] = len; // this overrides 0x04 i.e. preceding un-compressed format byte.
    offset += len;
    // now offset points to start of Y co-ordinate. We have to insert CBOR elements 0x22, 0x58 &
    // coord len byte = 3 bytes
    // First shift by 3 bytes
    Util.arrayCopyNonAtomic(buf, offset, buf, (short) (offset + 3), len);
    // Y Cord of the Key = 0x22 i.e. -3
    buf[offset++] = (byte) (MdlSpecifications.CBOR_NEG_INT | (byte) (3 - 1));
    // Byte String which will be always be greater than 23 and less than 256 - short encoding
    buf[offset++] =
        (byte) (MdlSpecifications.CBOR_BINARY_STR | MdlSpecifications.CBOR_MAX_256_BYTES);
    // Write len
    buf[offset++] = len;
    offset += len;
    // Now write the length of COSE key data structure - Note: add 4 bytes of first two elements
    // of COSE Key
    buf[lenOff] = (byte) (offset - lenOff - 1);
    return offset;
  }

  short readAndClearBufferedData(byte[] buf, short start) {
    if (mBuffer[BUF_LENGTH_OFFSET] > 0) {
      Util.arrayCopyNonAtomic(mBuffer, (short) 0, buf, start, mBuffer[BUF_LENGTH_OFFSET]);
    }
    return mBuffer[BUF_LENGTH_OFFSET];
  }
}
