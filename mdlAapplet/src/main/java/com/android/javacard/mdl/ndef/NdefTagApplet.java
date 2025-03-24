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
package com.android.javacard.mdl.ndef;

import com.android.javacard.mdl.MdlService;
import javacard.framework.AID;
import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.Util;
import javacardx.apdu.ExtendedLength;

/**
 * This class implements the NDefTagApplet which is required to exchange Mdl related capability
 * container, ndef files, and handover select message with an NFC Type 4 Tag as specified by ISO
 * 18013-5 specifications. This applet is just a placeholder because an actual NFC enabled SE may
 * already have a similar applet. The key design requirement is that NDEF Applet needs to get
 * dynamically generated Ephemeral Key from Presentation Applet which is used for session
 * establishment by MDL Reader. Thus, an NDEF Applet must use or expose shareable interface to
 * enable this dynamic data sharing. In this current placeholder implementation, NDEFApplet uses
 * Presentation Applet's shareable interface to get NDEF File content. Note: This applet
 * implementation is very similar to that provided in Android Identity Credential implementation
 * which supports Host Card Emulation mode. The only addition in this code is that of Presentation
 * Applet's shareable interface usage.
 */
public class NdefTagApplet extends Applet implements ExtendedLength {

  public static final byte[] AID_NDEF_TAG_APPLET = {
    (byte) 0xD2, 0x76, 0x00, 0x00, (byte) 0x85, 0x01, 0x01
  };

  public static final byte[] AID_MDL_DIRECT_ACCESS_APPLET = {
    (byte) 0xA0, 0x00, 0x00, 0x02, 0x48, 0x04, 0x00
  };

  static final short MAX_NDEF_DATA_FILE_SIZE = 1024;
  static final short STATUS_WORD_END_OF_FILE_REACHED = 0x6282;
  static final byte INS_SELECT = ISO7816.INS_SELECT;
  static final byte INS_READ_BINARY = (byte) 0xB0;
  static final short FILE_ID_CAPS_CONTAINER = (short) 0xE103;
  static final short FILE_ID_NDEF_FILE = (short) 0xE104;

  // Hardcoded Capability Container files that points to read only NDEF Data File.
  static final byte[] CAPS_CONTAINER = {
    (byte) 0x00,
    (byte) 0x0F, // size of capability container '00 0F' = 15 bytes
    (byte) 0x20, // mapping version v2.0
    (byte) 0x7F,
    (byte) 0xFF, // maximum response data length '7F FF'
    (byte) 0x7F,
    (byte) 0xFF, // maximum command data length '7F FF'
    (byte) 0x04,
    (byte) 0x06, // NDEF File Control TLV
    (byte) 0xE1,
    (byte) 0x04, // NDEF file identifier 'E1 04'
    (byte) 0x7F,
    (byte) 0xFF, // maximum NDEF file size '7F FF'
    (byte) 0x00, // file read access condition (allow read)
    (byte) 0xFF // file write access condition (do not write)
  };
  private final short[] mSelectedFile;
  private final byte[] mNdefDataFile;
  private final AID mAid;

  public NdefTagApplet() {
    mAid =
        new AID(
            AID_MDL_DIRECT_ACCESS_APPLET, (short) 0, (byte) AID_MDL_DIRECT_ACCESS_APPLET.length);

    mNdefDataFile =
        JCSystem.makeTransientByteArray(MAX_NDEF_DATA_FILE_SIZE, JCSystem.CLEAR_ON_DESELECT);
    mSelectedFile = JCSystem.makeTransientShortArray((short) (1), JCSystem.CLEAR_ON_DESELECT);
  }

  public static void install(byte[] buf, short off, byte len) {
    NdefTagApplet applet = new NdefTagApplet();
    applet.register();
  }

  @Override
  public void process(APDU apdu) throws ISOException {
    byte[] buffer = apdu.getBuffer();
    byte ins = buffer[ISO7816.OFFSET_INS];
    if (selectingApplet()) {
      return;
    }
    if (apdu.isSecureMessagingCLA()) {
      ISOException.throwIt(ISO7816.SW_SECURE_MESSAGING_NOT_SUPPORTED);
    }
    // process commands to the applet
    if (apdu.isISOInterindustryCLA()) {
      switch (ins) {
        case INS_SELECT:
          processSelect(apdu);
          break;
        case INS_READ_BINARY:
          processReadBinary(apdu);
          break;
        default:
          ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
      }
    } else {
      ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
    }
  }

  private void processSelect(APDU apdu) {
    apdu.setIncomingAndReceive();
    byte[] buf = apdu.getBuffer();
    if (buf[ISO7816.OFFSET_P1] != (byte) 0x00 && buf[ISO7816.OFFSET_P2] != (byte) 0x0C) {
      ISOException.throwIt(ISO7816.SW_FUNC_NOT_SUPPORTED);
    }
    // File ID will always be two bytes
    if (buf[ISO7816.OFFSET_LC] != 2) {
      ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
    }
    // Only support two file ids
    switch (Util.getShort(buf, ISO7816.OFFSET_CDATA)) {
      case FILE_ID_CAPS_CONTAINER:
        mSelectedFile[0] = FILE_ID_CAPS_CONTAINER;
        break;
      case FILE_ID_NDEF_FILE:
        // Current design enforces new session specific NDEF Data everytime NDEF Tag Applet is
        // selected. Everytime Ndef File is selected new device Engagement data must be generated
        // and hence new Ndef data file needs to be generated.
        MdlService mdl =
            (MdlService) JCSystem.getAppletShareableInterfaceObject(mAid, MdlService.SERVICE_ID);
        short payloadLength = mdl.getHandoverSelectMessage(buf, (short) 0);
        Util.arrayCopyNonAtomic(buf, (short) 0, mNdefDataFile, (short) 2, payloadLength);
        Util.setShort(mNdefDataFile, (short) 0, payloadLength);
        mSelectedFile[0] = FILE_ID_NDEF_FILE;
        break;
      default:
        ISOException.throwIt(ISO7816.SW_RECORD_NOT_FOUND);
        break;
    }
  }

  private void processReadBinary(APDU apdu) {
    byte[] buf = apdu.getBuffer();

    if (buf[ISO7816.OFFSET_LC] == 0) {
      if ((short) buf.length < 7) {
        ISOException.throwIt(ISO7816.SW_RECORD_NOT_FOUND);
      }
    }
    short offset = Util.getShort(buf, ISO7816.OFFSET_P1);
    byte[] file = mSelectedFile[0] == FILE_ID_NDEF_FILE ? mNdefDataFile : CAPS_CONTAINER;

    // Total length of the NDEF File includes payload and preceding two bytes for the file length.
    short contentLen = (short) (2 + Util.getShort(file, (short) 0));
    if (offset < 0 || offset >= contentLen) {
      ISOException.throwIt(ISO7816.SW_WRONG_P1P2);
    }
    short size = apdu.setOutgoing();

    // (short)(offset + size) can become negative if it is > 0x7FFF
    short targetOffset = (short) (offset + size);
    if (targetOffset < 0) {
      ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
    }
    if (targetOffset > contentLen) {
      ISOException.throwIt(STATUS_WORD_END_OF_FILE_REACHED);
    }
    apdu.setOutgoingLength(size);
    apdu.sendBytesLong(file, offset, size);
  }
}
