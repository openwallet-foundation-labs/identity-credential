/*
 * Copyright 2015 Robert Bachmann
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
import com.licel.jcardsim.base.SimulatorRuntime;
import com.licel.jcardsim.io.JavaxSmartCardInterface;

import javax.smartcardio.*;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Simulates a JavaCard.
 *
 * @see com.licel.jcardsim.smartcardio.CardTerminalSimulator
 */
public class CardSimulator extends JavaxSmartCardInterface {
    private final CardImpl card = new CardImpl();
    private final AtomicReference<CardTerminal> owningCardTerminalReference
            = new AtomicReference<CardTerminal>();
    private final AtomicReference<Thread> threadReference = new AtomicReference<Thread>();

    /**
     * Create a Simulator object using a new SimulatorRuntime.
     * <ul>
     * <li>SimulatorRuntime#resetRuntime is called</li>
     * </ul>
     */
    public CardSimulator() {
        this(new SimulatorRuntime());
    }

    /**
     * Create a Simulator object using a provided Runtime.
     * <ul>
     * <li>SimulatorRuntime#resetRuntime is called</li>
     * </ul>
     *
     * @param runtime SimulatorRuntime instance to use
     * @throws java.lang.NullPointerException if <code>runtime</code> is null
     */
    public CardSimulator(SimulatorRuntime runtime) {
        super(runtime);
    }

    /**
     * Wrapper for {@link #transmitCommand(byte[])}
     *
     * @param commandApdu CommandAPDU
     * @return ResponseAPDU
     */
    @Override
    public ResponseAPDU transmitCommand(CommandAPDU commandApdu) {
        return new ResponseAPDU(transmitCommand(commandApdu.getBytes()));
    }

    /**
     * <p>Assigns this simulated card to a CardTerminal.</p>
     * <p>If the card is already assigned to another CardTerminal, it will be ejected
     * and inserted into the CardTerminal <code>terminal</code>.</p>
     *
     * @param terminal card terminal or <code>null</code>
     */
    public synchronized void assignToTerminal(CardTerminal terminal) {
        final CardTerminal oldCardTerminal = owningCardTerminalReference.getAndSet(terminal);

        if (terminal == oldCardTerminal) {
            return;
        }

        if (oldCardTerminal != null) {
            // eject card from old Terminal
            ((CardTerminalSimulator.CardTerminalImpl) oldCardTerminal).assignSimulator(null);
        }

        if (terminal != null) {
            // reset card
            card.disconnect();
            // assign to new terminal
            ((CardTerminalSimulator.CardTerminalImpl) terminal).assignSimulator(this);
        }
    }

    /**
     * @return the assigned CardTerminal or null if none is assigned
     */
    public CardTerminal getAssignedCardTerminal() {
        return owningCardTerminalReference.get();
    }

    final Card internalConnect(String protocol) {
        card.connect(protocol);
        return card;
    }

    final void internalEject(CardTerminal oldTerminal) {
        if (owningCardTerminalReference.compareAndSet(oldTerminal, null)) {
            card.eject();
        }
    }

    private enum CardState {
        Connected, Disconnected, Ejected
    }

    private static final class CardChannelImpl extends CardChannel {
        private final CardImpl card;
        private final int channelNr;

        public CardChannelImpl(CardImpl card, int channelNr) {
            this.card = card;
            this.channelNr = channelNr;
        }

        @Override
        public Card getCard() {
            return card;
        }

        @Override
        public int getChannelNumber() {
            card.ensureConnected();
            return channelNr;
        }

        @Override
        public ResponseAPDU transmit(CommandAPDU commandAPDU) throws CardException {
            return new ResponseAPDU(card.transmitCommand(commandAPDU.getBytes()));
        }

        @Override
        public int transmit(ByteBuffer byteBuffer, ByteBuffer byteBuffer2) throws CardException {
            byte[] result = card.transmitCommand(new CommandAPDU(byteBuffer).getBytes());
            byteBuffer2.put(result);
            return result.length;
        }

        @Override
        public void close() throws CardException {
            throw new CardException("Can not close basic channel");
        }
    }

    private final class CardImpl extends Card {
        private final CardChannel basicChannel;
        private volatile String protocol = "T=0";
        private volatile byte protocolByte = 0;
        private volatile CardState state = CardState.Connected;

        CardImpl() {
            this.basicChannel = new CardChannelImpl(this, 0);
        }

        void ensureConnected() {
            CardState cardState = state;
            if (cardState == CardState.Disconnected) {
                throw new IllegalStateException("Card was disconnected");
            } else if (cardState == CardState.Ejected) {
                throw new IllegalStateException("Card was removed");
            }
        }

        @Override
        public ATR getATR() {
            return new ATR(CardSimulator.this.getATR());
        }

        @Override
        public String getProtocol() {
            return protocol;
        }

        @Override
        public CardChannel getBasicChannel() {
            return basicChannel;
        }

        @Override
        public CardChannel openLogicalChannel() throws CardException {
            throw new CardException("Logical channel not supported");
        }

        @Override
        public void beginExclusive() throws CardException {
            synchronized (runtime) {
                if (!threadReference.compareAndSet(null, Thread.currentThread())) {
                    throw new CardException("Card is held exclusively by Thread " + threadReference.get());
                }
            }
        }

        @Override
        public void endExclusive() throws CardException {
            synchronized (runtime) {
                if (!threadReference.compareAndSet(Thread.currentThread(), null)) {
                    throw new CardException("Card is held exclusively by Thread " + threadReference.get());
                }
            }
        }

        @Override
        public byte[] transmitControlCommand(int i, byte[] bytes) throws CardException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void disconnect(boolean reset) throws CardException {
            synchronized (runtime) {
                if (reset) {
                    CardSimulator.this.reset();
                }
                state = CardState.Disconnected;
            }
        }

        void connect(String protocol) {
            synchronized (runtime) {
                this.protocolByte = CardSimulator.this.getProtocolByte(protocol);
                this.protocol = protocol;
                this.state = CardState.Connected;
            }
        }

        void eject() {
            synchronized (runtime) {
                CardSimulator.this.reset();
                state = CardState.Ejected;
            }
        }

        void disconnect() {
            synchronized (runtime) {
                CardSimulator.this.reset();
                state = CardState.Disconnected;
            }
        }

        byte[] transmitCommand(byte[] capdu) throws CardException {
            synchronized (runtime) {
                ensureConnected();
                Thread thread = threadReference.get();
                if (thread != null && thread != Thread.currentThread()) {
                    throw new CardException("Card is held exclusively by Thread " + thread.getName());
                }

                byte currentProtocol = getProtocolByte(CardSimulator.this.getProtocol());
                try {
                    runtime.changeProtocol(protocolByte);
                    return CardManager.dispatchApdu(CardSimulator.this, capdu);
                } finally {
                    runtime.changeProtocol(currentProtocol);
                }
            }
        }
    }
}
