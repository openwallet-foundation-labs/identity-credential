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

import com.licel.jcardsim.base.Simulator;

import java.util.Properties;

/**
 * Card Acceptance Device (CAD).
 *
 * @author LICEL LLC
 */
public class CAD {

    public final static byte INTERNAL = 0;
    public final static byte RMI = 1;
    public final static byte JAVAX_SMARTCARDIO = 2;
    byte interfaceType;
    CardInterface cardInterface;

    public CAD(Properties params) {
        byte type = Byte.parseByte(params.getProperty("com.licel.jcardsim.terminal.type", Byte.toString(INTERNAL)));
        switch (type) {
            case INTERNAL:
                cardInterface = new Simulator();
                break;
            case RMI:
 /*               try {
                    String host = params.getProperty("com.licel.jcardsim.terminal.host");
                    int port = Integer.parseInt(params.getProperty("com.licel.jcardsim.terminal.port"));
                    cardInterface = new JavaCardRemoteClient(host, port);
                } catch (Exception e) {
                    throw new RuntimeException("RMI CardInterface init error", e);
                }
*/
                throw new IllegalArgumentException("Unknown CAD type: " + type);

            case JAVAX_SMARTCARDIO:
                cardInterface = new JavaxSmartCardInterface();
                break;
            default:
                throw new IllegalArgumentException("Unknown CAD type: " + type);
        }
        this.interfaceType = type;

    }

    public CardInterface getCardInterface() {
        return cardInterface;
    }
}
