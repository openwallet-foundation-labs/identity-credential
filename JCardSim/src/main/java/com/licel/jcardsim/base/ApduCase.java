/*
 * Copyright 2014 Robert Bachmann
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
package com.licel.jcardsim.base;

import javacard.framework.ISO7816;
import javacard.framework.Util;

/**
 * Case of an <code>APDU</code>.
 */
public enum ApduCase {
    /**
     * Case 1 APDU (CLA, INS, P1, P2)
     */
    Case1(false),
    /**
     * Case 2 APDU (CLA, INS, P1, P2, 1 byte Le)
     */
    Case2(false),
    /**
     * Case 2 extended APDU (CLA, INS, P1, P2, 0, 2 byte Le)
     */
    Case2Extended(true),
    /**
     * Case 3 APDU (CLA, INS, P1, P2, 1 byte Lc, Data)
     */
    Case3(false),
    /**
     * Case 3 extended APDU (CLA, INS, P1, P2, 2 byte Lc, Data)
     */
    Case3Extended(true),
    /**
     * Case 4 APDU (CLA, INS, P1, P2, 1 byte Lc, Data, 1 byte Le)
     */
    Case4(false),
    /**
     * Case 4 extended APDU (CLA, INS, P1, P2, 0, 2 byte Lc, Data, 2 byte Le)
     */
    Case4Extended(true);

    private final boolean extended;

    ApduCase(boolean extended) {
        this.extended = extended;
    }

    /**
     * @return <code>true</code> for extended APDU
     */
    public boolean isExtended() {
        return extended;
    }

    /**
     * Determine case of APDU
     * @param command command APDU byte buffer
     * @return Case of <code>command</code>
     * @throws java.lang.IllegalArgumentException if <code>command</code> is malformed
     * @throws java.lang.NullPointerException if <code>command</code> is null
     */
    public static ApduCase getCase(byte[] command) {
        if (command == null) {
            throw new NullPointerException("command");
        }
        if (command.length < 4) {
            throw new IllegalArgumentException("command: malformed APDU, length < 4");
        }
        if (command.length == 4) {
            return Case1;
        }
        if (command.length == 5) {
            return Case2;
        }
        if (command.length == 7 && command[ISO7816.OFFSET_LC] == 0) {
            return Case2Extended;
        }
        if (command[ISO7816.OFFSET_LC] == 0) {
            int lc = Util.getShort(command, (short) (ISO7816.OFFSET_LC + 1));
            int offset = ISO7816.OFFSET_LC + 3;
            if (lc + offset == command.length) {
                return Case3Extended;
            } else if (lc + offset + 2 == command.length) {
                return Case4Extended;
            } else {
                throw new IllegalArgumentException("Invalid extended C-APDU: Lc or Le is invalid");
            }
        } else {
            int lc = (command[ISO7816.OFFSET_LC] & 0xFF);
            int offset = ISO7816.OFFSET_LC + 1;
            if (lc + offset == command.length) {
                return Case3;
            } else if (lc + offset + 1 == command.length) {
                return Case4;
            } else {
                throw new IllegalArgumentException("Invalid C-APDU: Lc or Le is invalid");
            }
        }
    }
}
