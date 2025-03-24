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
import com.android.javacard.mdl.MdlSpecifications;
import com.android.javacard.mdl.SEProvider;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.Util;
import javacard.security.ECPrivateKey;
import javacard.security.KeyBuilder;
import javacard.security.KeyPair;

/**
 * This class represents the presentation package as defined by the Identity Credential design. A
 * presentation package represents ISO 18013-5 compliant document that is stored in Presentation
 * Applet and which is directly accessible by the reader. During provisioning this package created
 * and persisted when Android OS based client requests to swap in the credential. The data is
 * structure as element table which points to various data items stored in persistent memory in
 * PresentationApplet pointed my mHeap member variable.
 */
public class MdocPresentationPkg {

  public static final short MAX_DATA_ITEMS = (byte) 64;
  public static final short ELEM_KEY_ID_OFFSET = 0;
  public static final short ELEM_START_OFFSET = 1;
  public static final short ELEM_LENGTH_OFFSET = 2;
  public static final short ELEM_VALUE_START_OFFSET = 3;
  public static final short ELEM_VALUE_LENGTH_OFFSET = 4;
  public static final byte ELEM_TABLE_ROW_SIZE = 5;
  public static final short ELEM_TABLE_SIZE = (short) (MAX_DATA_ITEMS * ELEM_TABLE_ROW_SIZE);
  public static final byte NS_KEY_ID_OFFSET = 0;
  public static final byte NS_START_OFFSET = 1;
  public static final byte NS_END_OFFSET = 2;
  public static final byte MAX_NS_COUNT = 8;
  public static final byte NS_TABLE_ROW_SIZE = 3;
  public static final byte NS_TABLE_SIZE = (byte) (MAX_NS_COUNT * NS_TABLE_ROW_SIZE);
  public static final byte MAX_DOC_TYPE_SIZE = 65;

  private static short[] mTemp;
  private final KeyPair mAuthKey;
  private final short[] mNsTable;
  private final short[] mElementTable;
  private final CBORDecoder mDecoder;
  private final SEProvider mSEProvider;
  private final MdlSpecifications mMdlSpecifications;
  private short mUsageCount;
  private boolean mPreAllocatedMem;
  // offsets and lengths of the data items in heap.
  private short mIssuerAuthStart;
  private short mIssuerAuthLength;
  private short mDigestMappingLength;
  private short mDigestMappingStart;
  private short mReaderAccessKeysStart;
  private short mReaderAccessKeysLen;
  private byte[] mHeap;
  private byte[] mDocType;
  private short mHeapIndex;
  private short mDataEnd;
  private byte mNsTableSize;
  private short mElementTableSize;

  /**
   * This class represents the presentation package which is the master document that gets
   * provisioned by the provisioning applet and presented by the presentation applet. This document
   * is persistently stored by provisioning applet and based on the usage counter it gets swapped in
   * and out. When reader queries about the data elements of the presentation package then
   * presentation applet generates a view of this document, which gets presented to the reader. In
   * order to quickly create the presentation view, this class maintains several indexes such as
   * namespace table, elements table, etc.
   */
  public MdocPresentationPkg(SEProvider se, MdlSpecifications mdlSpecs) {
    mSEProvider = se;
    mMdlSpecifications = mdlSpecs;
    mDecoder = new CBORDecoder();
    mNsTable = new short[(short) (NS_TABLE_SIZE + 1)];
    mElementTable = new short[(short) (ELEM_TABLE_SIZE + 1)];
    mAuthKey = new KeyPair(KeyPair.ALG_EC_FP, KeyBuilder.LENGTH_EC_FP_256);
    mSEProvider.initECKey(mAuthKey);
    mAuthKey.genKeyPair();
    mTemp = new short[32];
  }

  public short getDataStart() {
    return 0;
  }

  public short getDataLength() {
    return mDataEnd;
  }

  public byte[] getBuffer() {
    return mHeap;
  }

  public void allocMem(short size) {
    if (mHeap == null) {
      mHeap = new byte[size];
    }
    if (mDocType == null) {
      mDocType = new byte[MAX_DOC_TYPE_SIZE];
    }
    mHeapIndex = 0;
  }

  public void freeMem() {
    if (mPreAllocatedMem) {
      return;
    }
    mHeap = null;
    JCSystem.requestObjectDeletion();
  }

  public void resetUsageCount() {
    mUsageCount = 0;
  }

  public short getUsageCount() {
    return mUsageCount;
  }

  public void incrementUsageCount() {
    mUsageCount++;
  }

  public KeyPair getAuthKeyPair() {
    return mAuthKey;
  }

  public short findNsEntry(short id) {
    for (byte i = 0; i < mNsTableSize; i += NS_TABLE_ROW_SIZE) {
      if (mNsTable[i + NS_KEY_ID_OFFSET] == id) {
        return i;
      }
    }
    return -1;
  }

  public short findNsEntry(byte[] buf, short start, short len) {
    for (byte i = 0; i < mNsTableSize; i += NS_TABLE_ROW_SIZE) {
      if (Util.arrayCompare(mHeap, mNsTable[i + NS_KEY_ID_OFFSET], buf, start, len) == 0) {
        return i;
      }
    }
    return -1;
  }

  public short findElementEntry(short nsIndex, byte[] buf, short start, short len) {
    short elemStart = mNsTable[(short) (nsIndex + NS_START_OFFSET)];
    short elemEnd = (short) (elemStart + mNsTable[(short) (nsIndex + NS_END_OFFSET)]);
    for (short i = elemStart; i < elemEnd; i += ELEM_TABLE_ROW_SIZE) {
      if (Util.arrayCompare(mHeap, mElementTable[(short) (i + ELEM_KEY_ID_OFFSET)], buf, start, len)
          == 0) {
        return i;
      }
    }
    return -1;
  }

  public short findElementEntry(short nsIndex, short elemId) {
    short elemStart = mNsTable[(short) (nsIndex + NS_START_OFFSET)];
    short elemEnd = (short) (elemStart + mNsTable[(short) (nsIndex + NS_END_OFFSET)]);
    for (short i = elemStart; i < elemEnd; i += ELEM_TABLE_ROW_SIZE) {
      if (mElementTable[(short) (i + ELEM_KEY_ID_OFFSET)] == elemId) {
        return i;
      }
    }
    return -1;
  }

  public short readElementRecord(short[] retArr, short start, short elemIndex) {
    retArr[(short) (start + ELEM_KEY_ID_OFFSET)] =
        mElementTable[(short) (elemIndex + ELEM_KEY_ID_OFFSET)];
    retArr[(short) (start + ELEM_START_OFFSET)] =
        mElementTable[(short) (elemIndex + ELEM_START_OFFSET)];
    retArr[(short) (start + ELEM_LENGTH_OFFSET)] =
        mElementTable[(short) (elemIndex + ELEM_LENGTH_OFFSET)];
    retArr[(short) (start + ELEM_VALUE_START_OFFSET)] =
        mElementTable[(short) (elemIndex + ELEM_VALUE_START_OFFSET)];
    retArr[(short) (start + ELEM_VALUE_LENGTH_OFFSET)] =
        mElementTable[(short) (elemIndex + ELEM_VALUE_LENGTH_OFFSET)];
    return ELEM_TABLE_ROW_SIZE;
  }

  public void write(byte[] buf, short start, short len) {
    if ((short) (mHeapIndex + len) > (short) (mHeap.length)) {
      ISOException.throwIt(ISO7816.SW_DATA_INVALID);
    }
    Util.arrayCopyNonAtomic(buf, start, mHeap, mHeapIndex, len);
    mHeapIndex += len;
  }

  public short read(byte[] buf, short start, short len, short ns, short elem, short elemDataPtr) {
    ns = (short) (ns * NS_TABLE_ROW_SIZE);
    if (ns >= mNsTableSize) {
      return 0;
    }
    elem = (short) (elem * ELEM_TABLE_ROW_SIZE);
    if (elem >= mElementTableSize) {
      return 0;
    }
    short remainingBytes =
        (short)
            (mElementTable[(short) (elem + ELEM_START_OFFSET)]
                + mElementTable[(short) (elem + ELEM_LENGTH_OFFSET)]
                - elemDataPtr);
    if (remainingBytes > len) {
      remainingBytes = len;
    }
    Util.arrayCopyNonAtomic(mHeap, elemDataPtr, buf, start, remainingBytes);
    return remainingBytes;
  }

  /**
   * KEY_DOC_TYPE, CBOR_TEXT_STR, KEY_DIGEST_MAPPING, CBOR_MAP, KEY_ISSUER_AUTH, CBOR_ARRAY,
   * KEY_READER_ACCESS, CBOR_MAP,
   *
   * <p>First parse the cred data and extract digest mapping. Then parse the digest mapping and
   * extract the name space keys. Then for each namespace parse the array of the items. Finally
   * store each item in the element index table.
   */
  public void enumerate(short[] temp) {
    // Parse the auth keys and cred data
    mDecoder.initialize(mHeap, getDataStart(), getDataLength());
    short size = mDecoder.readMajorType(CBORBase.TYPE_ARRAY);
    if (size != 2) {
      ISOException.throwIt(ISO7816.SW_DATA_INVALID);
    }
    size = mDecoder.readMajorType(CBORBase.TYPE_BYTE_STRING);
    if (size != (short) (KeyBuilder.LENGTH_EC_FP_256 / 8)) {
      ISOException.throwIt(ISO7816.SW_DATA_INVALID);
    }

    ((ECPrivateKey) mAuthKey.getPrivate()).setS(mHeap, mDecoder.getCurrentOffset(), size);
    mDecoder.increaseOffset(size);
    // Now Parse the cred data.
    size = mDecoder.readMajorType(CBORBase.TYPE_BYTE_STRING);
    short[] struct = mMdlSpecifications.getStructure(MdlSpecifications.KEY_CRED_DATA);
    mMdlSpecifications.decodeStructure(struct, temp, mHeap, mDecoder.getCurrentOffset(), size);
    // Doc types should match

    mDigestMappingStart = temp[0];
    mDigestMappingLength = temp[1];
    mIssuerAuthStart = temp[2];
    mIssuerAuthLength = temp[3];
    mReaderAccessKeysStart = temp[4];
    mReaderAccessKeysLen = temp[5];

    // The digest mapping is a map of namespaces - each of which is an array of 4 elements.
    mDecoder.init(mHeap, mDigestMappingStart, mDigestMappingLength);
    short nsCount = mDecoder.readMajorType(CBORBase.TYPE_MAP);
    // For each key-value entry in the map.
    for (byte i = 0; i < nsCount; i++) {
      // record the start of namespace key string
      mNsTable[mNsTableSize++] = mDecoder.getCurrentOffset();
      // skip the key - returns to start of array value
      mNsTable[mNsTableSize++] = mDecoder.skipEntry();
      // skip the array - returns to end of the array
      mNsTable[mNsTableSize++] = mDecoder.skipEntry();
    }

    // Now for every entry in Ns Table enumerate the elements
    struct = mMdlSpecifications.getStructure(MdlSpecifications.IND_ISSUER_SIGNED_ITEM);
    for (byte i = 0; i < mNsTableSize; i += NS_TABLE_ROW_SIZE) {
      short nsStart = mNsTable[(short) (i + 1)];
      short nsLen = (short) (mNsTable[(short) (i + 2)] - nsStart);
      mNsTable[(short) (i + 1)] = mElementTableSize;
      mDecoder.init(mHeap, nsStart, nsLen);
      short items = mDecoder.readMajorType(CBORBase.TYPE_ARRAY);
      while (items > 0) {
        short start = mDecoder.getCurrentOffset(); // semantic tag
        short end = mDecoder.skipEntry();

        mMdlSpecifications.decodeTaggedStructure(struct, temp, mHeap, start, (short) (end - start));
        if (temp[1] == 0 || temp[3] == 0 || temp[5] == 0 || temp[7] == 0) {
          ISOException.throwIt(ISO7816.SW_DATA_INVALID);
        }

        mElementTable[mElementTableSize++] = temp[4];
        mElementTable[mElementTableSize++] = start;
        mElementTable[mElementTableSize++] = (short) (end - start);
        mElementTable[mElementTableSize++] = temp[6];
        mElementTable[mElementTableSize++] = temp[7];
        items--;
      }
      // We store one more then actual end offset because we start from 0'th index.
      mNsTable[(short) (i + 2)] = mElementTableSize;
    }
  }

  public short getIssuerAuthStart() {
    return mIssuerAuthStart;
  }

  public short getIssuerAuthLength() {
    return mIssuerAuthLength;
  }

  public void create(short size, byte[] docStr, short docStrStart, short docStrLen) {
    allocMem(size);
    if (docStrLen > (MAX_DOC_TYPE_SIZE - 1)) {
      ISOException.throwIt(ISO7816.SW_DATA_INVALID);
    }
    JCSystem.beginTransaction();
    mDocType[0] = (byte) (docStrLen & 0xff);
    Util.arrayCopyNonAtomic(docStr, docStrStart, mDocType, (short) 1, mDocType[0]);
    JCSystem.commitTransaction();
  }

  /**
   * Resets the Element table. This function is called during a document deletion
   * and also before enumerating a new credential.
   */
  private void reset() {
    mDigestMappingStart = -1;
    mIssuerAuthStart = -1;
    mReaderAccessKeysStart = -1;
    mReaderAccessKeysLen = -1;
    mDigestMappingLength = -1;
    mIssuerAuthLength = -1;
    mElementTableSize = 0;
    mNsTableSize = 0;
  }

  public void delete() {
    mHeapIndex = 0;
    reset();
    freeMem();
  }

  public void setPreAllocated() {
    mPreAllocatedMem = true;
  }

  public void startProvisioning() {
    mHeapIndex = 0;
    mDataEnd = 0;
  }

  public void commitProvisioning() {
    mDataEnd = mHeapIndex;
    reset();
    enumerate(mTemp);
  }

  public boolean isMatching(byte[] buf, short start, short len) {
    return len == mDocType[0]
        && Util.arrayCompare(buf, start, mDocType, (short) 1, mDocType[0]) == 0;
  }

  public boolean isReaderAuthRequired() {
    mDecoder.init(mHeap, mReaderAccessKeysStart, mReaderAccessKeysLen);
    short count = mDecoder.readMajorType(CBORBase.TYPE_ARRAY);
    return count > 0;
  }

  public short getDocType(byte[] buf, short start) {
    Util.arrayCopyNonAtomic(mDocType, (short) 1, buf, start, mDocType[0]);
    return mDocType[0];
  }

  public boolean isMatchingReaderAuthKey(byte[] buf, short start, short len) {
    try {
      mDecoder.init(mHeap, mReaderAccessKeysStart, mReaderAccessKeysLen);
      short count = mDecoder.readMajorType(CBORBase.TYPE_ARRAY);
      for (short i = 0; i < count; i++) {
        short keyLen = mDecoder.readMajorType(CBORBase.TYPE_BYTE_STRING);
        short keyStart = mDecoder.getCurrentOffset();
        if (len == keyLen && Util.arrayCompare(buf, start, mHeap, keyStart, len) == 0) {
          return true;
        }
        mDecoder.increaseOffset(keyLen);
      }
    } catch (ISOException exp) {
      return false;
    }
    return false;
  }

  public short getElementStart(short elemIndex) {
    return mElementTable[(short) (elemIndex + MdocPresentationPkg.ELEM_START_OFFSET)];
  }

  public short getElementLen(short elemIndex) {
    return mElementTable[(short) (elemIndex + MdocPresentationPkg.ELEM_LENGTH_OFFSET)];
  }

  public short getNsId(short nsIndex, byte[] buf, short start) {
    short offset = mNsTable[(short) (nsIndex + NS_KEY_ID_OFFSET)];
    mDecoder.init(mHeap, offset, (short) (mHeap.length - offset));
    short len = (short) (mDecoder.skipEntry() - offset);
    Util.arrayCopyNonAtomic(mHeap, offset, buf, start, len);
    return len;
  }
}
