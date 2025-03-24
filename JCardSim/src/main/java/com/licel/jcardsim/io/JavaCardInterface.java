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

import javacard.framework.AID;
import javacard.framework.SystemException;

/**
 * Interface with JavaCard-specific functions.
 * @author LICEL LLC
 */
public interface JavaCardInterface extends CardInterface {

    /**
     * Load
     * <code>Applet</code> into Simulator
     *
     * @param aid applet aid
     * @param appletClassName fully qualified applet class name Strin
     * @param appletJarContents contains a byte array containing a jar file with an applet and its dependent classes
     * @return applet <code>AID</code>
     * @throws SystemException if <code>appletClass</code> not instanceof
     * <code>javacard.framework.Applet</code>
     */
    public AID loadApplet(AID aid, String appletClassName, byte[] appletJarContents) throws SystemException;

    /**
     * Load
     * <code>Applet</code> into Simulator
     *
     * @param aid applet aid
     * @param appletClassName fully qualified applet class name String
     * @return applet <code>AID</code>
     * @throws SystemException if <code>appletClass</code> not instanceof
     * <code>javacard.framework.Applet</code>
     */
    public AID loadApplet(AID aid, String appletClassName);

    /**
     * Create
     * <code>Applet</code> instance in Simulator
     * @param aid applet aid
     * @param bArray the array containing installation parameters
     * @param bOffset the starting offset in bArray
     * @param bLength the length in bytes of the parameter data in bArray
     * @return applet <code>AID</code>
     * @throws SystemException if exception in <code>Applet.install(..)</code>
     * method occurs.
     */
    public AID createApplet(AID aid, byte bArray[], short bOffset,
            byte bLength) throws SystemException;

    /**
     * Install
     * <code>Applet</code> into Simulator.
     * This method is equal to:
     * <code>
     * loadApplet(...);
     * createApplet(...);
     * </code>
     * @param aid applet aid or null
     * @param appletClassName fully qualified applet class name Strin
     * @param bArray the array containing installation parameters
     * @param bOffset the starting offset in bArray
     * @param bLength the length in bytes of the parameter data in bArray
     * @return applet <code>AID</code>
     * @throws SystemException if <code>appletClass</code> not instanceof
     * <code>javacard.framework.Applet</code>
     */
    public AID installApplet(AID aid, String appletClassName, byte bArray[], short bOffset,
            byte bLength) throws SystemException;

    /**
     * Install
     * <code>Applet</code> into Simulator.
     * This method is equal to:
     * <code>
     * loadApplet(...);
     * createApplet(...);
     * </code>
     * @param aid applet aid or null
     * @param appletClassName fully qualified applet class name Strin
     * @param appletJarContents Contains a byte array containing a jar file with an applet and its dependent classes
     * @param bArray the array containing installation parameters
     * @param bOffset the starting offset in bArray
     * @param bLength the length in bytes of the parameter data in bArray
     * @return applet <code>AID</code>
     * @throws SystemException if <code>appletClass</code> not instanceof
     * <code>javacard.framework.Applet</code>
     */
    public AID installApplet(AID aid, String appletClassName, byte[] appletJarContents, byte bArray[], short bOffset,
            byte bLength) throws SystemException;

    /**
     * Select applet by it's AID
     * It's method must be called before start working with applet instance
     * @param aid appletId
     * @return true if applet selection success
     */
    public boolean selectApplet(AID aid);

    /**
     * Select applet by it's AID
     * It's method must be called before start working with applet instance
     * @param aid appletId
     * @return byte array
     */
    public byte[] selectAppletWithResult(AID aid);

    /**
     * Switch protocol
     *
     * Supported protocols are:
     * <ul>
     *     <li><code>T=0</code> (alias: <code>*</code>)</li>
     *     <li><code>T=1</code></li>
     *     <li><code>T=CL, TYPE_A, T0</code> (alias: <code>T=CL</code>)</li>
     *     <li><code>T=CL, TYPE_A, T1</code></li>
     *     <li><code>T=CL, TYPE_B, T0</code></li>
     *     <li><code>T=CL, TYPE_B, T1</code></li>
     * </ul>
     *
     * @param protocol protocol to use
     * @throws java.lang.IllegalArgumentException for unknown protocols
     */
    public void changeProtocol(String protocol);

    /**
     * @return the current protocol string
     * @see #changeProtocol(String)
     */
    public String getProtocol();
}
