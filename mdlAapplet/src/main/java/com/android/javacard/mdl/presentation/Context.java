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

import com.android.javacard.mdl.MdlSpecifications;
import javacard.framework.JCSystem;
import javacard.framework.Util;

/**
 * This class stores the information in current context of the presentation applet. This information
 * is related to the device request and incremental response processing. Presentation Applet can get
 * sequence of document requests, which needs to be responded. Now, depending on document size and
 * io buffer size (i.e. chunk size), each request has to be processed and incrementally responded.
 * This is done in Document Request and PresentationApplet classes. The state of the request and
 * response processing is maintained in this Context. The data path of the response is that first
 * the data is read from the persistent PresentationPackage then encoded, encrypted and stored in
 * Context's buffer. This is then transferred to apdu buffer based on chunk size. The Content's
 * buffer size will always be greater then apdu's buffer size. Larger the size of buffer, better
 * will be the throughput of this data path.
 */
public class Context {

  // Incremental Response states
  public static final byte RESP_IDLE = 0;
  public static final byte RESP_START = 1;
  public static final byte RESP_DOCUMENT = 2;
  public static final byte RESP_DEVICE_SIGNED = 3; // We do not divide this in individual parts.
  public static final byte RESP_ISSUER_SIGNED = 4;
  public static final byte RESP_ISSUER_AUTH = 5;
  public static final byte RESP_ISSUER_NS = 6;
  public static final byte RESP_NS_ELEMENTS = 7;
  public static final byte RESP_NS_ELEMENT = 8;
  public static final byte CURRENT_DOC = 0;
  public static final byte CURRENT_STATE = 1;
  public static final byte CURRENT_NAMESPACE = 2;
  public static final byte CURRENT_ELEMENT = 3;
  public static final byte CURRENT_DATA_PTR_START = 4;
  public static final byte CURRENT_DATA_PTR_END = 5;

  // Buffer related metadata
  public static final short MAX_BUF_SIZE = 5096;
  public static final short MAX_CMD_DATA_RSP_DATA = 261;
  static final byte MAX_DOC_REQUESTS = 2;

  // Current response state
  public short[] mIncrementalResponseState;
  // List of document requests to be processed
  Object[] mDocumentRequests;
  // Document Request which is currently being incrementally processed.
  Object[] mDocuments;

  // Stores the request message from the reader and response message to reader. The size is equal
  // to MAX_BUFFER_SIZE.
  byte[] mBuffer;
  // start of the data to be read.
  short[] mBufReadIndex;
  // cursor for the data to be written.
  short[] mBufWriteIndex;
  // Maximum size of the data expected by the reader. This has to be less than MAX_BUFFER_SIZE by
  // at least 256 bytes, in order to allow us to handle non aligned key value pairs and also auth
  // tag.
  short[] mChunkSize;
  // Total remaining encoded data from the package. When device request is received the
  // presentation applet calculates this value based on available data in the presentation
  // packaged provisioned earlier. This value is decremented everytime some data from presentation
  // package is encoded and encrypted and copied in to context buffer.
  short[] mRemainingBytes;
  // Total number of documents that has to be sent in the response to the reader.
  byte[] mDocumentsCount;

  public Context(byte[] buffer, MdlSpecifications mdlSpecs) {
    mBuffer = buffer;
    mBufReadIndex = JCSystem.makeTransientShortArray((short) 1, JCSystem.CLEAR_ON_DESELECT);
    mBufWriteIndex = JCSystem.makeTransientShortArray((short) 1, JCSystem.CLEAR_ON_DESELECT);
    mRemainingBytes = JCSystem.makeTransientShortArray((short) 1, JCSystem.CLEAR_ON_DESELECT);
    mDocumentsCount = JCSystem.makeTransientByteArray((short) 1, JCSystem.CLEAR_ON_DESELECT);
    mChunkSize = JCSystem.makeTransientShortArray((short) 1, JCSystem.CLEAR_ON_DESELECT);
    mIncrementalResponseState =
        JCSystem.makeTransientShortArray((short) 7, JCSystem.CLEAR_ON_DESELECT);

    // Array of enumerated requests
    mDocumentRequests = new DocumentRequest[MAX_DOC_REQUESTS];
    for (byte i = 0; i < MAX_DOC_REQUESTS; i++) {
      // Each Device request will have pointer to reader auth, DocType i.e. mdl or another doc
      // type and itemsRequests bytes used for reader auth. The pointer will have two fields i.e.
      // length and start in the mBuffer Note we are currently only supported doc type processing
      // and reader auth processing. In the future we may support the individual element processing.
      mDocumentRequests[i] = new DocumentRequest(mdlSpecs);
      // Each device response corresponds to associated requested doc type. It will have
      // two pointers - one pointer is index in doc table and another pointer is the index in MSO
      // table.
    }
    // Request under progress i.e. incrementally responded.
    mDocuments = JCSystem.makeTransientObjectArray((short) 1, JCSystem.CLEAR_ON_RESET);
  }

  // Reset the context.
  void reset() {
    clearBuffer();
    mChunkSize[0] = 0;
    mRemainingBytes[0] = 0;
    mBufWriteIndex[0] = 0;
    mIncrementalResponseState[0] = RESP_IDLE;
    mIncrementalResponseState[1] = 0;
    clearDocumentRequests();
  }

  void clearBuffer() {
    Util.arrayFillNonAtomic(mBuffer, (short) 0, (short) mBuffer.length, (byte) 0);
  }

  void clearDocumentRequests() {
    for (byte i = 0; i < MAX_DOC_REQUESTS; i++) {
      ((DocumentRequest) mDocumentRequests[i]).reset();
    }
    mDocumentsCount[0] = 0;
  }
  
  void setChunkSize(short chunkSize) {
    mChunkSize[0] = chunkSize;
  }

  // Context is set to outgoing i.e. response processing is started.
  void setOutGoing(short responseSize, short currentDoc) {
    mRemainingBytes[0] = responseSize;
    // Actual chunk size is always two bytes less because the two bytes are always consumed by
    // status word.
    mBufReadIndex[0] = 0;
    mBufWriteIndex[0] = 0;
    mIncrementalResponseState[Context.CURRENT_STATE] = RESP_START;
    mIncrementalResponseState[Context.CURRENT_NAMESPACE] = 0;
    mIncrementalResponseState[Context.CURRENT_ELEMENT] = 0;
    mIncrementalResponseState[Context.CURRENT_DATA_PTR_START] = 0;
    mIncrementalResponseState[Context.CURRENT_DATA_PTR_END] = 0;
    // Select the first document and then send that.
    mIncrementalResponseState[Context.CURRENT_DOC] = currentDoc;
  }
}
