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

import java.nio.ByteBuffer;
import javax.smartcardio.*;

/**
 * CardChannel implementation class.
 * @author LICEL LLC
 */
public class JCSCardChannel extends CardChannel {
     private JCSCard card;
     private int channel;

    public JCSCardChannel(JCSCard card, int channel) {
        this.card = card;
        this.channel = channel;
    }
     
    public Card getCard() {
        return card;
    }

    public int getChannelNumber() {
        return channel;
    }

    public ResponseAPDU transmit(CommandAPDU capdu) throws CardException {
        return card.transmitCommand(capdu);
    }

    public int transmit(ByteBuffer bb, ByteBuffer bb1) throws CardException {
        ResponseAPDU response = transmit(new CommandAPDU(bb));
        byte[] binaryResponse = response.getBytes();
        bb1.put(binaryResponse);
        return binaryResponse.length;
    }

    /**
     * Do nothing.
     */
    public void close() throws CardException {
    
    }
    
}
