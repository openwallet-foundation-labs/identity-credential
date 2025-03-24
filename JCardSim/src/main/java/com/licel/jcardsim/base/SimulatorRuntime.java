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

import javacard.framework.APDU;
import com.licel.jcardsim.utils.AIDUtil;
import com.licel.jcardsim.utils.BiConsumer;
import com.licel.jcardsim.utils.ByteUtil;
import javacard.framework.*;
import javacardx.apdu.ExtendedLength;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base implementation of Java Card Runtime.
 * @see JCSystem
 * @see Applet
 */
public class SimulatorRuntime {
    // holds the Applet registration callback
    protected final ThreadLocal<BiConsumer<Applet,AID>> registrationCallback;
    /** storage for installed applets */
    protected final SortedMap<AID, ApplicationInstance> applets = new TreeMap<AID, ApplicationInstance>(AIDUtil.comparator());
    /** storage for load files */
    protected final SortedMap<AID, LoadFile> loadFiles = new TreeMap<AID, LoadFile>(AIDUtil.comparator());
    /** storage for automatically generated loadFile AIDs */
    protected final SortedMap<AID, AID> generatedLoadFileAIDs = new TreeMap<AID, AID>(AIDUtil.comparator());
    /** method for resetting APDUs */
    protected final Method apduPrivateResetMethod;
    /** outbound response byte array buffer */
    protected final byte[] responseBuffer = new byte[Short.MAX_VALUE + 2];
    /** transient memory */
    protected final TransientMemory transientMemory;
    /** regular APDU */
    protected final APDU shortAPDU;
    /** extended APDU */
    protected final APDU extendedAPDU;

    /** current selected applet */
    protected AID currentAID;
    /** previous selected applet */
    protected AID previousAID;
    /** outbound response byte array buffer size */
    protected short responseBufferSize = 0;
    /** if the applet is currently being selected */
    protected boolean selecting = false;
    /** if extended APDUs are used  */
    protected boolean usingExtendedAPDUs = false;
    /** current protocol */
    protected byte currentProtocol = APDU.PROTOCOL_T0;
    /** current depth of transaction */
    protected byte transactionDepth = 0;
    /** previousActiveObject */
    protected Object previousActiveObject;

    public SimulatorRuntime() {
        this(new TransientMemory());
    }

    @SuppressWarnings("unchecked")
    public SimulatorRuntime(TransientMemory transientMemory) {
        this.transientMemory = transientMemory;
        try {
            Constructor<?>[] constructors = APDU.class.getDeclaredConstructors();

            Constructor<?> ctor = constructors[0].getParameterCount() > 0 ? constructors[0]:constructors[1] ;
            ctor.setAccessible(true);

            shortAPDU = (APDU) ctor.newInstance(false);//new APDU(false);
            extendedAPDU = (APDU) ctor.newInstance(true);//new APDU(true);//

            apduPrivateResetMethod = APDU.class.getDeclaredMethod("internalReset", byte.class, ApduCase.class, byte[].class);
            apduPrivateResetMethod.setAccessible(true);

            Field f = Applet.class.getDeclaredField("registrationCallback");
            f.setAccessible(true);
            registrationCallback = (ThreadLocal<BiConsumer<Applet,AID>>) f.get(null);//Applet.registrationCallback;//(ThreadLocal<BiConsumer<Applet,AID>>) f.get(null);
        } catch (Exception e) {
            throw new RuntimeException("Internal reflection error", e);
        }
    }

    /**
     * Register <code>this</code> with <code>SimulatorRuntime</code>
     */
    protected final void activateSimulatorRuntimeInstance() {
        SimulatorSystem.setCurrentInstance(this);
    }


    /**
     * @return current applet context AID or null
     */
    public AID getAID() {
        return currentAID;
    }

    /**
     * Lookup applet by aid contains in byte array
     * @param buffer the byte array containing the AID bytes
     * @param offset the start of AID bytes in <code>buffer</code>
     * @param length the length of the AID bytes in <code>buffer</code>
     * @return Applet AID or null
     */
    public AID lookupAID(byte buffer[], short offset, byte length) {
        // no construct new AID, iterate applets
        for (AID aid : applets.keySet()) {
            if (aid.equals(buffer, offset, length)) {
                return aid;
            }
        }
        return null;
    }

    /**
     * Lookup applet by aid
     * @param lookupAid applet AID
     * @return ApplicationInstance or null
     */
    public ApplicationInstance lookupApplet(AID lookupAid) {
        for (AID aid : applets.keySet()) {
            if (aid.equals(lookupAid)) {
                return applets.get(aid);
            }
        }
        return null;
    }

    /**
     * @return previous selected applet context AID or null
     */
    public AID getPreviousContextAID() {
        return previousAID;
    }

    /**
     * Return <code>Applet</code> by it's AID or null
     * @param aid applet <code>AID</code>
     * @return Applet or null
     */
    protected Applet getApplet(AID aid) {
        if (aid == null) {
            return null;
        }
        ApplicationInstance a = lookupApplet(aid);
        if(a == null) return null;
        else return a.getApplet();
    }

    /**
     * Load applet
     * @param aid Applet AID
     * @param appletClass Applet class
     */
    public void loadApplet(AID aid, Class<? extends Applet> appletClass) {
        if (generatedLoadFileAIDs.keySet().contains(aid)) {
            throw new SystemException(SystemException.ILLEGAL_AID);
        }
        // generate a load file AID
        byte[] generated = new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0, 0};
        Util.setShort(generated, (short) 3, (short) generatedLoadFileAIDs.size());
        AID generatedAID = AIDUtil.create(generated);

        generatedLoadFileAIDs.put(aid, generatedAID);
        loadLoadFile(new LoadFile(generatedAID, generatedAID, appletClass));
    }

    /**
     * Load a LoadFile
     * @param loadFile LoadFile to load
     */
    public void loadLoadFile(LoadFile loadFile) {
        AID key = loadFile.getAid();
        if (loadFiles.keySet().contains(key) || applets.keySet().contains(key)) {
            throw new IllegalStateException("LoadFile AID already used");
        }
        loadFiles.put(key, loadFile);
    }

    /**
     * Delete applet
     * @param aid Applet AID to delete
     */
    protected void deleteApplet(AID aid) {
        activateSimulatorRuntimeInstance();
        ApplicationInstance applicationInstance = lookupApplet(aid);
        if (applicationInstance == null) {
            throw new SystemException(SystemException.ILLEGAL_AID);
        }

        applets.remove(aid);
        Applet applet = applicationInstance.getApplet();
        if (applet == null) {
            return;
        }

        if (getApplet(currentAID) == applet) {
            deselect(applicationInstance);
        }

        if (applet instanceof AppletEvent) {
            try {
                ((AppletEvent) applet).uninstall();
            }
            catch (Exception e) {
                // ignore all
            }
        }
    }

    /**
     * Check if applet is currently being selected
     * @param aThis applet
     * @return true if applet is being selected
     */
    public boolean isAppletSelecting(Object aThis) {
        return aThis == getApplet(getAID()) && selecting;
    }

    /**
     * Transmit APDU to previous selected applet
     * @param command command apdu
     * @return response apdu
     */
    public byte[] transmitCommand(byte[] command) throws SystemException {
        activateSimulatorRuntimeInstance();

        final ApduCase apduCase = ApduCase.getCase(command);
        final byte[] theSW = new byte[2];
        byte[] response;

        Applet applet = getApplet(getAID());

        selecting = false;
        // check if there is an applet to be selected
        if (!apduCase.isExtended() && isAppletSelectionApdu(command)) {
            AID newAid = findAppletForSelectApdu(command, apduCase);
            if (newAid != null) {
                deselect(lookupApplet(getAID()));
                currentAID = newAid;
                applet = getApplet(getAID());
                selecting = true;
            }
            else if (applet == null) {
                Util.setShort(theSW, (short) 0, ISO7816.SW_APPLET_SELECT_FAILED);
                return theSW;
            }
        }

        if (applet == null) {
            Util.setShort(theSW, (short) 0, ISO7816.SW_COMMAND_NOT_ALLOWED);
            return theSW;
        }

        if (apduCase.isExtended()) {
            if (applet instanceof ExtendedLength) {
                usingExtendedAPDUs = true;
            }
            else {
                Util.setShort(theSW, (short)0, ISO7816.SW_WRONG_LENGTH);
                return theSW;
            }
        }
        else {
            usingExtendedAPDUs = false;
        }

        responseBufferSize = 0;
        APDU apdu = getCurrentAPDU();
        try {
            if (selecting) {
                boolean success;
                try {
                    success = applet.select();
                }
                catch (Exception e) {
                    success = false;
                }
                if (!success) {
                    throw new ISOException(ISO7816.SW_APPLET_SELECT_FAILED);
                }
            }

            // set apdu
            resetAPDU(apdu, apduCase, command);

            applet.process(apdu);
            Util.setShort(theSW, (short) 0, (short) 0x9000);
        } catch (Throwable e) {
            Util.setShort(theSW, (short) 0, ISO7816.SW_UNKNOWN);
            if (e instanceof ISOException) {
                Util.setShort(theSW, (short) 0, ((ISOException) e).getReason());
            }
        }
        finally {
            selecting = false;
            resetAPDU(apdu, null, null);
        }

        // if theSW = 0x61XX or 0x9XYZ than return data (ISO7816-3)
        if(theSW[0] == 0x61 || theSW[0] == 0x62 || theSW[0] == 0x63 || (theSW[0] >= (byte)0x90 && theSW[0] <= (byte)0x9F) || isNotAbortingCase(theSW) ) {
            response = new byte[responseBufferSize + 2];
            Util.arrayCopyNonAtomic(responseBuffer, (short) 0, response, (short) 0, responseBufferSize);
            Util.arrayCopyNonAtomic(theSW, (short) 0, response, responseBufferSize, (short) 2);
        }
        else {
            response = theSW;
        }

        return response;
    }

    /**
     * Check if secure channel is not aborted
     * This method must be override in subclass that have secure channel abort checking
     * @param SW Status word
     * @return True if secure channel is not aborted
     */
    protected boolean isNotAbortingCase(byte[] SW) {
        return false;
    }

    protected AID findAppletForSelectApdu(byte[] selectApdu, ApduCase apduCase) {
        if (apduCase == ApduCase.Case1 || apduCase == ApduCase.Case2) {
            // on a regular Smartcard we would select the CardManager applet
            // in this case we just select the first applet
            return applets.isEmpty() ? null : applets.firstKey();
        }

        for (AID aid : applets.keySet()) {
            if (aid.equals(selectApdu, ISO7816.OFFSET_CDATA, selectApdu[ISO7816.OFFSET_LC])) {
                return aid;
            }
        }

        for (AID aid : applets.keySet()) {
            if (aid.partialEquals(selectApdu, ISO7816.OFFSET_CDATA, selectApdu[ISO7816.OFFSET_LC])) {
                return aid;
            }
        }

        return null;
    }

    protected void deselect(ApplicationInstance applicationInstance) {
        activateSimulatorRuntimeInstance();
        if (applicationInstance != null) {
            try {
                Applet applet = applicationInstance.getApplet();
                applet.deselect();
            } catch (Exception e) {
                // ignore all
            }
        }
        if (getTransactionDepth() != 0) {
            abortTransaction();
        }
        transientMemory.clearOnDeselect();
    }

    /**
     * Copy response bytes to internal buffer
     * @param buffer source byte array
     * @param bOff the starting offset in buffer
     * @param len the length in bytes of the response
     */
    public void sendAPDU(byte[] buffer, short bOff, short len) {
        responseBufferSize = Util.arrayCopyNonAtomic(buffer, bOff, responseBuffer, responseBufferSize, len);
    }

    /**
     * powerdown/powerup
     */
    public void reset() {
        Arrays.fill(responseBuffer, (byte) 0);
        transactionDepth = 0;
        responseBufferSize = 0;
        currentAID = null;
        previousAID = null;
        transientMemory.clearOnReset();
    }

    public void resetRuntime() {
        activateSimulatorRuntimeInstance();
        Iterator<AID> aids = applets.keySet().iterator();
        ArrayList<AID> aidsToTrash = new ArrayList<AID>();
        while (aids.hasNext()) {
            AID aid = aids.next();
            aidsToTrash.add(aid);
        }
        for (AID anAidsToTrash : aidsToTrash) {
            deleteApplet(anAidsToTrash);
        }

        loadFiles.clear();
        generatedLoadFileAIDs.clear();
        Arrays.fill(responseBuffer, (byte) 0);
        transactionDepth = 0;
        responseBufferSize = 0;
        currentAID = null;
        previousAID = null;
        transientMemory.clearOnReset();
        transientMemory.forgetBuffers();
    }

    public TransientMemory getTransientMemory() {
        return transientMemory;
    }

    protected void resetAPDU(APDU apdu, ApduCase apduCase, byte[] buffer) {
        try {
            apduPrivateResetMethod.invoke(apdu, currentProtocol, apduCase, buffer);
            //apdu.internalReset(currentProtocol,apduCase,buffer);
        } catch (Exception e) {
            throw new RuntimeException("Internal reflection error", e);
        }
    }

    public APDU getCurrentAPDU() {
        return usingExtendedAPDUs ? extendedAPDU : shortAPDU;
    }

    /**
     * Change protocol
     * @param protocol protocol bits
     * @see javacard.framework.APDU#getProtocol()
     */
    public void changeProtocol(byte protocol) {
        this.currentProtocol = protocol;
        resetAPDU(shortAPDU, null, null);
        resetAPDU(extendedAPDU, null, null);
    }

    public byte getAssignedChannel() {
        return 0; // basic channel
    }

    /**
     * @see javacard.framework.JCSystem#beginTransaction()
     */
    public void beginTransaction() {
        if (transactionDepth != 0) {
            TransactionException.throwIt(TransactionException.IN_PROGRESS);
        }
        transactionDepth = 1;
    }

    /**
     * @see javacard.framework.JCSystem#abortTransaction()
     */
    public void abortTransaction() {
        if (transactionDepth == 0) {
            TransactionException.throwIt(TransactionException.NOT_IN_PROGRESS);
        }
        transactionDepth = 0;
    }

    /**
     * @see javacard.framework.JCSystem#commitTransaction()
     */
    public void commitTransaction() {
        if (transactionDepth == 0) {
            TransactionException.throwIt(TransactionException.NOT_IN_PROGRESS);
        }
        transactionDepth = 0;
    }

    /**
     * @see javacard.framework.JCSystem#getTransactionDepth()
     * @return 1 if transaction in progress, 0 if not
     */
    public byte getTransactionDepth() {
        return transactionDepth;
    }

    /**
     * @see javacard.framework.JCSystem#getUnusedCommitCapacity()
     * @return The current implementation always returns 32767
     */
    public short getUnusedCommitCapacity() {
        return Short.MAX_VALUE;
    }

    /**
     * @see javacard.framework.JCSystem#getMaxCommitCapacity()
     * @return The current implementation always returns 32767
     */
    public short getMaxCommitCapacity() {
        return Short.MAX_VALUE;
    }

    /**
     * @see javacard.framework.JCSystem#getAvailableMemory(byte)
     * @return The current implementation always returns 32767
     */
    public short getAvailablePersistentMemory() {
        return Short.MAX_VALUE;
    }

    /**
     * @see javacard.framework.JCSystem#getAvailableMemory(byte)
     * @return The current implementation always returns 32767
     */
    public short getAvailableTransientResetMemory() {
        return Short.MAX_VALUE;
    }

    /**
     * @see javacard.framework.JCSystem#getAvailableMemory(byte)
     * @return The current implementation always returns 32767
     */
    public short getAvailableTransientDeselectMemory() {
        return Short.MAX_VALUE;
    }

    /**
     * @see javacard.framework.JCSystem#getAppletShareableInterfaceObject(javacard.framework.AID, byte)
     * @param serverAID the AID of the server applet
     * @param parameter optional parameter data
     * @return the shareable interface object or <code>null</code>
     */
    public Shareable getSharedObject(AID serverAID, byte parameter) {
        Applet serverApplet = getApplet(serverAID);
        if (serverApplet != null) {
            return serverApplet.getShareableInterfaceObject(getAID(),
                    parameter);
        }
        return null;
    }

    /**
     * @see javacard.framework.JCSystem#isObjectDeletionSupported()
     * @return always false
     */
    public boolean isObjectDeletionSupported() {
        return false;
    }

    /**
     * @see javacard.framework.JCSystem#requestObjectDeletion()
     */
    public void requestObjectDeletion() {
        if (!isObjectDeletionSupported()) {
            throw new SystemException(SystemException.ILLEGAL_USE);
        }
    }

    public void setJavaOwner(Object obj, Object owner) {}

    public Object getJavaOwner(Object obj) {
        return obj;
    }

    public short getJavaContext(Object obj) {
        return 0;
    }

    public Object getPreviousActiveObject() {
        return previousActiveObject;
    }

    public void setPreviousActiveObject(Object previousActiveObject) {
        this.previousActiveObject = previousActiveObject;
    }

    protected static boolean isAppletSelectionApdu(byte[] apdu) {
        final byte channelMask = (byte) 0xFC; // mask out %b000000xx
        final byte p2Mask = (byte) 0xE3; // mask out %b000xxx00

        final byte cla = (byte) (apdu[ISO7816.OFFSET_CLA] & channelMask);
        final byte ins = apdu[ISO7816.OFFSET_INS];
        final byte p1 = apdu[ISO7816.OFFSET_P1];
        final byte p2 = (byte) (apdu[ISO7816.OFFSET_P2] & p2Mask);

        return cla == ISO7816.CLA_ISO7816 && ins == ISO7816.INS_SELECT &&
                p1 == 4 && p2 == 0;
    }

    public void installApplet(final AID appletAid, byte[] bArray, short bOffset, byte bLength) {
        AID generatedAID = generatedLoadFileAIDs.get(appletAid);
        
        if (generatedAID == null || !loadFiles.keySet().contains(generatedAID)) {
            throw new SystemException(SystemException.ILLEGAL_AID);
        }
        installApplet(generatedAID, generatedAID, appletAid, bArray, bOffset, bLength);
    }

    public void installApplet(AID loadFileAID, AID moduleAID, final AID appletAID,
                              byte[] bArray, short bOffset, byte bLength) {
        activateSimulatorRuntimeInstance();
        LoadFile loadFile = loadFiles.get(loadFileAID);
        if (loadFile == null) {
            throw new IllegalArgumentException("LoadFile AID not found " + AIDUtil.toString(loadFileAID));
        }
        Module module = loadFile.getModule(moduleAID);
        if (module == null) {
            throw new IllegalArgumentException("Module AID not found " + AIDUtil.toString(moduleAID));
        }

        Class<? extends Applet> appletClass = module.getAppletClass();
        Method initMethod;
        try {
            initMethod = appletClass.getMethod("install",
                    new Class[]{byte[].class, short.class, byte.class});
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Class does not provide install method");
        }

        final AtomicInteger callCount = new AtomicInteger(0);
        registrationCallback.set(new BiConsumer<Applet,AID>() {
            public void accept(Applet applet, AID installAID) {
                // disallow second call to register
                if (callCount.incrementAndGet() != 1) {
                    throw new SystemException(SystemException.ILLEGAL_AID);
                }

                // register applet
                if (installAID != null) {
                    applets.put(installAID, new ApplicationInstance(installAID, applet));
                }
                else {
                    applets.put(appletAID, new ApplicationInstance(appletAID, applet));
                }
            }
        });

        try {
            initMethod.invoke(null, bArray, bOffset, bLength);
        }
        catch (InvocationTargetException e) {
            try {
                ISOException isoException = (ISOException) e.getCause();
                throw isoException;
            } catch (ClassCastException cce){
                throw new SystemException(SystemException.ILLEGAL_AID);
            }
        }
        catch (Exception e) {
            throw new SystemException(SystemException.ILLEGAL_AID);
        }
        finally {
            registrationCallback.set(null);
        }

        if (callCount.get() != 1) {
            throw new SystemException(SystemException.ILLEGAL_AID);
        }
    }

    /** Represents an Applet instance */
    public static class ApplicationInstance {
        private final AID aid;
        private final Applet applet;

        public ApplicationInstance(AID aid, Applet applet) {
            this.aid = aid;
            this.applet = applet;
        }

        public Applet getApplet(){
            return applet;
        }
        
        public AID getAID() {
            return aid;
        }
        
        @Override
        public String toString() {
            return String.format("ApplicationInstance (%s)", AIDUtil.toString(aid));
        }
    }
}
