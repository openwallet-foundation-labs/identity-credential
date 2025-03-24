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

import com.licel.jcardsim.utils.AutoResetEvent;

import javax.smartcardio.*;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.Provider;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * <p>A simulated {@link javax.smartcardio.TerminalFactory}.</p>
 * <p>Example: Obtaining a Card</p>
 * <pre>
 * // create card simulator
 * CardSimulator cardSimulator = new CardSimulator();
 *
 * // connect to a card
 * CardTerminal terminal =
 *   CardTerminalSimulator.terminal(cardSimulator);
 * Card card = terminal.connect("*");
 * </pre>
 * <p>Example: Inserting/ejecting a Card</p>
 * <pre>
 * // create card simulator
 * CardSimulator cardSimulator = new CardSimulator();
 *
 * // create CardTerminal
 * CardTerminals terminals = CardTerminalSimulator.terminals("my terminal")
 * CardTerminal terminal = terminals.getTerminal("my terminal");
 *
 * // insert Card
 * cardSimulator.assignToTerminal(terminal);
 *
 * // eject Card
 * cardSimulator.assignToTerminal(null);
 * </pre>
 *
 * @see com.licel.jcardsim.smartcardio.CardSimulator
 */
public final class CardTerminalSimulator {
    private CardTerminalSimulator() {
    }

    /**
     * Create a single CardTerminal.
     *
     * @param cardSimulator card to insert
     * @param name          the terminal name
     * @return a new <code>CardTerminal</code> instance
     * @throws java.lang.NullPointerException if name or cardSimulator is null
     */
    public static CardTerminal terminal(CardSimulator cardSimulator, String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        if (cardSimulator == null) {
            throw new NullPointerException("cardSimulator");
        }
        CardTerminal cardTerminal = terminals(name).getTerminal(name);
        cardSimulator.assignToTerminal(cardTerminal);
        return cardTerminal;
    }

    /**
     * Create a CardTerminal with name "jCardSim.Terminal".
     *
     * @param cardSimulator card to insert
     * @return a new <code>CardTerminal</code> instance
     * @throws java.lang.NullPointerException if name or cardSimulator is null
     */
    public static CardTerminal terminal(CardSimulator cardSimulator) {
        return terminal(cardSimulator, "jCardSim.Terminal");
    }

    /**
     * <p>Create CardTerminals.</p>
     * <p>Example:</p>
     * <pre>
     *  CardTerminals terminals = CardTerminalSimulator.terminals("1","2");
     *  CardTerminal terminal = terminals.getTerminal("1");
     *
     *  // assign simulator
     *  CardSimulator cardSimulator = new CardSimulator();
     *  cardSimulator.assignToTerminal(terminal);
     * </pre>
     *
     * @param names the terminal names
     * @return a new <code>CardTerminals</code> instance
     * @throws java.lang.NullPointerException     if names is null
     * @throws java.lang.IllegalArgumentException if any name is null or duplicated
     * @see javax.smartcardio.CardTerminals
     */
    public static CardTerminals terminals(String... names) {
        if (names == null) {
            throw new NullPointerException("names");
        }
        Set<String> set = new HashSet<String>(names.length);
        for (String name : names) {
            if (set.contains(name)) {
                throw new IllegalArgumentException("Duplicate name '" + name + "'");
            }
            set.add(name);
        }
        return new CardTerminalsImpl(names);
    }

    /**
     * <p>Security provider.</p>
     * <p>Register the SecurityProvider with:</p>
     * <pre>
     * if (Security.getProvider("CardTerminalSimulator") == null) {
     *     Security.addProvider(new CardTerminalSimulator.SecurityProvider());
     * }
     * </pre>
     */
    public static final class SecurityProvider extends Provider {
        public SecurityProvider() {
            super("CardTerminalSimulator", 1.0d, "jCardSim Virtual Terminal Provider");
            AccessController.doPrivileged(new PrivilegedAction() {
                public Object run() {
                    put("TerminalFactory." + "CardTerminalSimulator", Factory.class.getCanonicalName()
                            .replace(".Factory", "$Factory"));
                    return null;
                }
            });
        }
    }

    /**
     * {@link javax.smartcardio.TerminalFactorySpi} implementation.
     * Applications do not access this class directly, instead see {@link javax.smartcardio.TerminalFactory}.
     */
    @SuppressWarnings("unused")
    public static final class Factory extends TerminalFactorySpi {
        private final CardTerminals cardTerminals;

        public Factory(Object params) {
            String[] names;
            if (params == null) {
                names = new String[]{"jCardSim.Terminal"};
            } else if (params instanceof String) {
                names = new String[]{(String) params};
            } else if (params instanceof String[]) {
                names = (String[]) params;
            } else {
                throw new IllegalArgumentException("Illegal parameter: " + params);
            }
            cardTerminals = terminals(names);
        }

        @Override
        protected CardTerminals engineTerminals() {
            return cardTerminals;
        }
    }

    static boolean waitForLatch(AutoResetEvent autoResetEvent, long timeoutMilliseconds) throws InterruptedException {
        if (timeoutMilliseconds < 0) {
            throw new IllegalArgumentException("timeout is negative");
        }
        if (timeoutMilliseconds == 0) { // wait forever
            boolean success;
            do {
                success = autoResetEvent.await(1, TimeUnit.MINUTES);
            } while (!success);
            return true;
        }
        return autoResetEvent.await(timeoutMilliseconds, TimeUnit.MILLISECONDS);
    }

    static final class CardTerminalsImpl extends CardTerminals {
        private final AtomicBoolean waitCalled = new AtomicBoolean(false);
        private final AutoResetEvent terminalsChangeAutoResetEvent = new AutoResetEvent();
        private final ArrayList<CardTerminalImpl> simulatedTerminals;
        private final HashMap<CardTerminal, State> terminalStateMap;

        CardTerminalsImpl(String[] names) {
            simulatedTerminals = new ArrayList<CardTerminalImpl>(names.length);
            terminalStateMap = new HashMap<CardTerminal, State>(names.length);
            for (String name : names) {
                simulatedTerminals.add(new CardTerminalImpl(name, terminalStateMap, terminalsChangeAutoResetEvent));
            }
        }

        @Override
        public synchronized List<CardTerminal> list(State state) throws CardException {
            if (state == null) {
                throw new NullPointerException("state");
            }
            synchronized (terminalStateMap) {
                final ArrayList<CardTerminal> result = new ArrayList<CardTerminal>(simulatedTerminals.size());

                for (CardTerminal terminal : simulatedTerminals) {
                    State terminalState = terminalStateMap.get(terminal);
                    switch (state) {
                        case ALL:
                            result.add(terminal);
                            break;
                        case CARD_ABSENT:
                            if (!terminal.isCardPresent() && terminalState != State.CARD_REMOVAL) {
                                result.add(terminal);
                            }
                            break;
                        case CARD_PRESENT:
                            if (terminal.isCardPresent() && terminalState != State.CARD_INSERTION) {
                                result.add(terminal);
                            }
                            break;
                        case CARD_INSERTION:
                            if (waitCalled.get()) {
                                if (terminalState == State.CARD_INSERTION) {
                                    terminalStateMap.put(terminal, State.CARD_PRESENT);
                                    result.add(terminal);
                                }
                            } else if (terminal.isCardPresent()) {
                                result.add(terminal);
                            }
                            break;
                        case CARD_REMOVAL:
                            if (waitCalled.get()) {
                                if (terminalState == State.CARD_REMOVAL) {
                                    terminalStateMap.put(terminal, State.CARD_ABSENT);
                                    result.add(terminal);
                                }
                            } else if (!terminal.isCardPresent()) {
                                result.add(terminal);
                            }
                            break;
                    }
                }
                return Collections.unmodifiableList(result);
            }
        }

        @Override
        public boolean waitForChange(long timeoutMilliseconds) throws CardException {
            try {
                return waitForLatch(terminalsChangeAutoResetEvent, timeoutMilliseconds);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } finally {
                waitCalled.set(true);
            }
        }
    }

    static final class CardTerminalImpl extends CardTerminal {
        private final String name;
        private final Map<CardTerminal, CardTerminals.State> terminalStateMap;
        private final AutoResetEvent terminalsChangeAutoResetEvent;
        private final AutoResetEvent cardPresent = new AutoResetEvent();
        private final AutoResetEvent cardAbsent = new AutoResetEvent();
        private final AtomicReference<CardSimulator> cardSimulatorReference = new AtomicReference<CardSimulator>();

        CardTerminalImpl(String name, Map<CardTerminal, CardTerminals.State> terminalStateMap, AutoResetEvent terminalsChangeAutoResetEvent) {
            this.name = name;
            this.terminalStateMap = terminalStateMap;
            this.terminalsChangeAutoResetEvent = terminalsChangeAutoResetEvent;
            cardAbsent.signal();
            terminalStateMap.put(this, CardTerminals.State.CARD_ABSENT);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Card connect(String protocol) throws CardException {
            CardSimulator cardSimulator = cardSimulatorReference.get();
            if (cardSimulator == null) {
                throw new CardNotPresentException("No card inserted. You need to call CardTerminalSimulator#assignToTerminal");
            }
            return cardSimulator.internalConnect(protocol);
        }

        @Override
        public boolean isCardPresent() throws CardException {
            return cardSimulatorReference.get() != null;
        }

        @Override
        public boolean waitForCardPresent(long timeoutMilliseconds) throws CardException {
            try {
                return waitForLatch(cardPresent, timeoutMilliseconds);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        @Override
        public boolean waitForCardAbsent(long timeoutMilliseconds) throws CardException {
            try {
                return waitForLatch(cardAbsent, timeoutMilliseconds);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        void assignSimulator(CardSimulator cardSimulator) {
            synchronized (terminalStateMap) {
                CardSimulator oldCardSimulator = cardSimulatorReference.getAndSet(cardSimulator);
                boolean change = false;
                boolean present = false;

                if (oldCardSimulator != null) {
                    oldCardSimulator.internalEject(this);
                    change = true;
                }
                if (cardSimulator != null) {
                    present = true;
                    change = true;
                }
                if (change) {
                    if (present) {
                        terminalStateMap.put(this, CardTerminals.State.CARD_INSERTION);
                        cardPresent.signal();
                        cardAbsent.reset();
                    } else {
                        terminalStateMap.put(this, CardTerminals.State.CARD_REMOVAL);
                        cardPresent.reset();
                        cardAbsent.signal();
                    }
                    terminalsChangeAutoResetEvent.signal();
                }
            }
        }

        @Override
        public String toString() {
            return "jCardSim Terminal: " + name;
        }
    }
}
