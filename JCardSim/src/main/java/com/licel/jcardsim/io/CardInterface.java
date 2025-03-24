/*
 * Copyright 2013 Licel LLC.
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
package com.licel.jcardsim.io;

/**
 * Basic SmartCard Interface.
 *
 * @author LICEL LLC
 */
public interface CardInterface {

    /**
     * Powerdown/Powerup
     */
    public void reset();

    /**
     * Returns ATR
     * @return ATR bytes
     */
    public byte[] getATR();

    /**
     * Transmit APDU to previous selected applet
     *
     * If no applet was selected returns <code>byte[2]</code>
     * with status word 0x6986 (Command not allowed (no current EF))
     *
     * @param commandAPDU command apdu
     * @return response APDU
     * @see javax.smartcardio.CommandAPDU
     * @see javax.smartcardio.ResponseAPDU
     */
    public byte[] transmitCommand(byte[] commandAPDU);
}
