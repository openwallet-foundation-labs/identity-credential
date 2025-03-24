/*
 * Copyright 2011 Licel LLC.
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

import com.licel.jcardsim.io.JavaCardInterface;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Properties;

import com.licel.jcardsim.utils.AIDUtil;
import com.licel.jcardsim.utils.ByteUtil;
import javacard.framework.*;
import org.bouncycastle.util.encoders.Hex;

/**
 * Simulates a JavaCard.
 */
public class Simulator implements JavaCardInterface {

    // default ATR - NXP JCOP 31/36K
    public static final String DEFAULT_ATR = "3BFA1800008131FE454A434F5033315632333298";
    // ATR system property name
    public static final String ATR_SYSTEM_PROPERTY = "com.licel.jcardsim.card.ATR";
    static final String PROPERTY_PREFIX = "com.licel.jcardsim.card.applet.";
    static final String OLD_PROPERTY_PREFIX = "com.licel.jcardsim.smartcardio.applet.";
    // Applet AID system property template
    static final MessageFormat AID_SP_TEMPLATE = new MessageFormat("{0}.AID");
    // Applet ClassName system property template
    static final MessageFormat APPLET_CLASS_SP_TEMPLATE = new MessageFormat("{0}.Class");
    // Applet Class Loader
    final AppletClassLoader cl = new AppletClassLoader(new URL[]{});
    /** The simulator runtime */
    protected final SimulatorRuntime runtime;
    // current protocol
    private String protocol = "T=0";

    /**
     * Create a Simulator object using the default SimulatorRuntime.
     *
     * <ul>
     *     <li>All <code>Simulator</code> instances share one <code>SimulatorRuntime</code>.</li>
     *     <li>SimulatorRuntime#resetRuntime is called</li>
     *     <li>If your want multiple independent simulators use <code>Simulator(SimulatorRuntime)</code></li>
     * </ul>
     */
    public Simulator() {
        this(SimulatorSystem.DEFAULT_RUNTIME, System.getProperties());
    }

    /**
     * Create a Simulator object using a provided Runtime.
     *
     * <ul>
     *     <li>SimulatorRuntime#resetRuntime is called</li>
     * </ul>
     *
     * @param runtime SimulatorRuntime instance to use
     * @throws java.lang.NullPointerException if <code>runtime</code> is null
     */
    public Simulator(SimulatorRuntime runtime) {
        this(runtime, System.getProperties());
    }

    protected Simulator(SimulatorRuntime runtime, Properties properties) {
        if (runtime == null) {
            throw new NullPointerException("runtime");
        }

        this.runtime = runtime;
        synchronized (this.runtime) {
            this.runtime.resetRuntime();
        }

        changeProtocol(protocol);

        // init pre-installed applets
        for (int i = 0; i < 100 && !properties.isEmpty(); i++) {
            String selectedPrefix = PROPERTY_PREFIX;
            String aidPropertyName = PROPERTY_PREFIX + AID_SP_TEMPLATE.format(new Object[]{i});
            String aidPropertyOldName = OLD_PROPERTY_PREFIX + AID_SP_TEMPLATE.format(new Object[]{i});
            String appletAID = properties.getProperty(aidPropertyName);
            if (appletAID == null) {
                appletAID = properties.getProperty(aidPropertyOldName);
                if (appletAID != null) {
                    selectedPrefix = OLD_PROPERTY_PREFIX;
                }
            }
            if (appletAID != null) {
                String appletClassName = properties.getProperty(selectedPrefix + APPLET_CLASS_SP_TEMPLATE.format(new Object[]{i}));
                if (appletClassName != null) {
                    byte[] aidBytes = Hex.decode(appletAID);
                    if (aidBytes == null || aidBytes.length < 5 || aidBytes.length > 16) {
                        // skip incorrect applet
                        continue;
                    }
                    loadApplet(new AID(aidBytes, (short) 0, (byte) aidBytes.length), appletClassName);
                }
            }
        }
    }

    public AID loadApplet(AID aid, String appletClassName, byte[] appletJarContents) throws SystemException {
        // simple method, but emulate real card login
        // download data
        byte[] aidData = new byte[16];
        aid.getBytes(aidData, (short) 0);
        Class<? extends Applet> appletClass = null;
        try {
            cl.addAppletContents(appletJarContents);
            appletClass = requireExtendsApplet(cl.loadClass(appletClassName));
        } catch (Exception e) {
            SystemException.throwIt(SystemException.ILLEGAL_VALUE);

        }
        if (appletClass != null) {
            return loadApplet(aid, appletClass);
        } else {
            SystemException.throwIt(SystemException.ILLEGAL_VALUE);
            return null;
        }
    }

    public AID loadApplet(AID aid, String appletClassName) throws SystemException {
        Class<? extends Applet> appletClass = null;
        try {
            appletClass = requireExtendsApplet(cl.loadClass(appletClassName));
        } catch (ClassNotFoundException ex) {
            SystemException.throwIt(SystemException.ILLEGAL_VALUE);
        }
        return loadApplet(aid, appletClass);
    }

    /**
     * Load
     * <code>Applet</code> into Simulator
     *
     * @param aid applet aid
     * @param appletClass applet class
     * @return applet <code>AID</code>
     * @throws SystemException if <code>appletClass</code> not instanceof
     * <code>javacard.framework.Applet</code>
     */
    public AID loadApplet(AID aid, Class<? extends Applet> appletClass) throws SystemException {
        synchronized (runtime) {
            runtime.loadApplet(aid, requireExtendsApplet(appletClass));
        }
        return aid;
    }

    public AID createApplet(AID aid, byte bArray[], short bOffset,
            byte bLength) throws SystemException {

        try {
            synchronized (runtime) {
                runtime.installApplet(aid, bArray, bOffset, bLength);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            SystemException.throwIt(SimulatorSystem.SW_APPLET_CREATION_FAILED);
        }
        return aid;
    }

    /**
     * Install
     * <code>Applet</code> into Simulator without installing data
     *
     * @param aid applet aid or null
     * @param appletClass applet class
     * @return applet <code>AID</code>
     * @throws SystemException if <code>appletClass</code> not instanceof
     * <code>javacard.framework.Applet</code>
     */
    public AID installApplet(AID aid, Class<? extends Applet> appletClass) throws SystemException {
        return installApplet(aid, appletClass, new byte[]{}, (short) 0, (byte) 0);
    }

    /**
     * Install
     * <code>Applet</code> into Simulator. This method is equal to:
     * <code>
     * loadApplet(...);
     * createApplet(...);
     * </code>
     *
     * @param aid applet aid or null
     * @param appletClass applet class
     * @param bArray the array containing installation parameters
     * @param bOffset the starting offset in bArray
     * @param bLength the length in bytes of the parameter data in bArray
     * @return applet <code>AID</code>
     * @throws SystemException if <code>appletClass</code> not instanceof
     * <code>javacard.framework.Applet</code>
     */
    public AID installApplet(AID aid, Class<? extends Applet> appletClass, byte bArray[], short bOffset,
            byte bLength) throws SystemException {
        synchronized (runtime) {
            loadApplet(aid, appletClass);
            return createApplet(aid, bArray, bOffset, bLength);
        }
    }

    public AID installApplet(AID aid, String appletClassName, byte bArray[], short bOffset,
            byte bLength) throws SystemException {
        synchronized (runtime) {
            loadApplet(aid, appletClassName);
            return createApplet(aid, bArray, bOffset, bLength);
        }
    }

    public AID installApplet(AID aid, String appletClassName, byte[] appletContents, byte bArray[], short bOffset,
            byte bLength) throws SystemException {
        synchronized (runtime) {
            loadApplet(aid, appletClassName, appletContents);
            return createApplet(aid, bArray, bOffset, bLength);
        }
    }

    /**
     * Delete an applet
     * @param aid applet aid
     */
    public void deleteApplet(AID aid) {
        synchronized (runtime) {
            runtime.deleteApplet(aid);
        }
    }

    public boolean selectApplet(AID aid) throws SystemException {
        byte[] resp = selectAppletWithResult(aid);
        return ByteUtil.getSW(resp) == ISO7816.SW_NO_ERROR;
    }
    
    public byte[] selectAppletWithResult(AID aid) throws SystemException {
        synchronized (runtime) {
            return runtime.transmitCommand(AIDUtil.select(aid));
        }
    }

    public byte[] transmitCommand(byte[] command) {
        synchronized (runtime) {
            return runtime.transmitCommand(command);
        }
    }

    public void reset() {
        synchronized (runtime) {
            runtime.reset();
        }
    }

    public final void resetRuntime() {
        synchronized (runtime) {
            runtime.resetRuntime();
        }
    }

    public byte[] getATR() {
        return Hex.decode(System.getProperty(ATR_SYSTEM_PROPERTY, DEFAULT_ATR));
    }

    protected byte getProtocolByte(String protocol) {
        if (protocol == null) {
            throw new NullPointerException("protocol");
        }
        String p = protocol.toUpperCase(Locale.ENGLISH).replace(" ","");
        byte protocolByte;

        if (p.equals("T=0") || p.equals("*")) {
            protocolByte = APDU.PROTOCOL_T0;
        }
        else if (p.equals("T=1")) {
            protocolByte = APDU.PROTOCOL_T1;
        }
        else if (p.equals("T=CL,TYPE_A,T0") || p.equals("T=CL")) {
            protocolByte = APDU.PROTOCOL_MEDIA_CONTACTLESS_TYPE_A;
            protocolByte |= APDU.PROTOCOL_T0;
        }
        else if (p.equals("T=CL,TYPE_A,T1")) {
            protocolByte = APDU.PROTOCOL_MEDIA_CONTACTLESS_TYPE_A;
            protocolByte |= APDU.PROTOCOL_T1;
        }
        else if (p.equals("T=CL,TYPE_B,T0")) {
            protocolByte = APDU.PROTOCOL_MEDIA_CONTACTLESS_TYPE_B;
            protocolByte |= APDU.PROTOCOL_T0;
        }
        else if (p.equals("T=CL,TYPE_B,T1")) {
            protocolByte = APDU.PROTOCOL_MEDIA_CONTACTLESS_TYPE_B;
            protocolByte |= APDU.PROTOCOL_T1;
        }
        else {
            throw new IllegalArgumentException("Unknown protocol: " + protocol);
        }
        return protocolByte;
    }

    /**
     * @see com.licel.jcardsim.io.JavaCardInterface#changeProtocol(String)
     */
    public void changeProtocol(String protocol) {
        synchronized (runtime) {
            runtime.changeProtocol(getProtocolByte(protocol));
            this.protocol = protocol;
        }
    }

    /**
     * @see com.licel.jcardsim.io.JavaCardInterface#getProtocol()
     */
    public String getProtocol() {
        return protocol;
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Applet> requireExtendsApplet(Class<?> aClass) {
        if (!Applet.class.isAssignableFrom(aClass)) {
            throw new SystemException(SystemException.ILLEGAL_VALUE);
        }
        return (Class<? extends Applet>) aClass;
    }

    class AppletClassLoader extends URLClassLoader {

        AppletClassLoader(URL[] urls) {
            super(urls, Simulator.class.getClassLoader());
        }

        void addAppletContents(byte[] appletJarContents) throws IOException {
            File downloadedAppletJar = File.createTempFile("applet", "contents");
            downloadedAppletJar.deleteOnExit();
            FileOutputStream fos = new FileOutputStream(downloadedAppletJar);
            fos.write(appletJarContents);
            fos.close();
            addURL(downloadedAppletJar.toURI().toURL());

        }
    }
}
