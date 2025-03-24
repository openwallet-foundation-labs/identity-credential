/*
 * Copyright 2012 Licel LLC.
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
package com.licel.jcardsim.smartcardio;

import com.licel.jcardsim.base.CardManager;
import com.licel.jcardsim.io.JavaCardInterface;
import javax.smartcardio.*;

/**
 * Card implementation class.
 *
 * @author LICEL LLC
 */
public class JCSCard extends Card {
    // default protocol

    // ATR
    private ATR atr;
    // Simulator
    private JavaCardInterface cardInterface;
    //
    private JCSCardChannel basicChannel;

    public JCSCard(JavaCardInterface cardInterface) {
        this.cardInterface = cardInterface;
        atr = new ATR(cardInterface.getATR());
        basicChannel = new JCSCardChannel(this, 0);
    }

    /**
     *
     */
    public ATR getATR() {
        return atr;
    }

    /**
     * Always returns T=0.
     */
    public String getProtocol() {
        return cardInterface.getProtocol();
    }

    public CardChannel getBasicChannel() {
        return basicChannel;
    }

    /**
     * Always returns basic channel with id = 0
     */
    public CardChannel openLogicalChannel() throws CardException {
        return basicChannel;
    }

    /**
     * Do nothing.
     */
    public void beginExclusive() throws CardException {
    }

    /**
     * Do nothing.
     */
    public void endExclusive() throws CardException {
    }

    public byte[] transmitControlCommand(int i, byte[] bytes) throws CardException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * Disconnect form the card
     * @param reset true if the card should be reset
     * @see Card#disconnect(boolean)
     */
    public void disconnect(boolean reset) throws CardException {
        if (reset) {
            cardInterface.reset();
        }
    }

    ResponseAPDU transmitCommand(CommandAPDU capdu) {
        return new ResponseAPDU(CardManager.dispatchApdu(cardInterface, capdu.getBytes()));
    }
}
