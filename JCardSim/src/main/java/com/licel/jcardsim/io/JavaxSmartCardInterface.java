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
import com.licel.jcardsim.base.SimulatorRuntime;

import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;

/**
 * Simulator with javacardx.smartcardio Command/Response support.
 *
 * NOTE: New code should use {@link com.licel.jcardsim.smartcardio.CardSimulator} instead.
 */
public class JavaxSmartCardInterface extends Simulator {
    /**
     * Create a JavaxSmartCardInterface object using the default SimulatorRuntime.
     *
     * <ul>
     *     <li>All <code>JavaxSmartCardInterface</code> instances share one <code>SimulatorRuntime</code>.</li>
     *     <li>SimulatorRuntime#resetRuntime is called</li>
     *     <li>If your want multiple independent simulators use <code>JavaxSmartCardInterface(SimulatorRuntime)</code></li>
     * </ul>
     */
    public JavaxSmartCardInterface() {
        super();
    }

    /**
     * Create a JavaxSmartCardInterface object using a provided Runtime.
     *
     * <ul>
     *     <li>SimulatorRuntime#resetRuntime is called</li>
     * </ul>
     *
     * @param runtime SimulatorRuntime instance to use
     * @throws java.lang.NullPointerException if <code>runtime</code> is null
     */
    public JavaxSmartCardInterface(SimulatorRuntime runtime) {
        super(runtime);
    }

    /**
     * Wrapper for transmitCommand(byte[])
     * @param commandApdu CommandAPDU
     * @return ResponseAPDU
     */
    public ResponseAPDU transmitCommand(CommandAPDU commandApdu) {
        return new ResponseAPDU(transmitCommand(commandApdu.getBytes()));
    }
}
