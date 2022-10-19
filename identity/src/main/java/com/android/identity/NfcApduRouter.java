package com.android.identity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.Executor;

/**
 * Mechanism used for interfacing with the NFC subsystem.
 *
 * <p>The application using the Identity Credential Library must implement this and connect
 * it to its internal APDU routing system.
 */
public abstract class NfcApduRouter {

    // Defined by NFC Forum
    public static final byte[] AID_FOR_TYPE_4_TAG_NDEF_APPLICATION = Util.fromHex("D2760000850101");

    // Defined by 18013-5 Section 8.3.3.1.2 Data retrieval using near field communication (NFC)
    public static final byte[] AID_FOR_MDL_DATA_TRANSFER = Util.fromHex("A0000002480400");

    private ArrayList<Pair<Listener, Executor>> mListeners = new ArrayList<>();

    public void addListener(@Nullable Listener listener, @Nullable Executor executor) {
        if (listener != null && executor == null) {
            throw new IllegalStateException("Cannot have non-null listener with null executor");
        }
        mListeners.add(new Pair<>(listener, executor));
    }

    /**
     * Remove a previously registered listener.
     *
     * <p>Trying to remove a listener multiple times is permitted, only the first call will
     * have an effect.
     *
     * @param listener
     * @param executor
     */
    public void removeListener(@Nullable Listener listener, @Nullable Executor executor) {
        mListeners.removeIf(listenerPair -> (listenerPair.first == listener
                && listenerPair.second == executor));
    }

    /**
     * Sends a response APDU back to the remote device.
     *
     * @param responseApdu A byte-array containing the response APDU.
     */
    public abstract void sendResponseApdu(@NonNull byte[] responseApdu);

    /**
     * Adds a received APDU to the queue of received APDUs.
     *
     * <p>This will notify listeners.
     *
     * @param receivedApdu an APDU received
     */
    public void addReceivedApdu(@NonNull byte[] aid, @NonNull byte[] receivedApdu) {
        for (Pair<Listener, Executor> listenerPair : Collections.synchronizedList(mListeners)) {
            Listener listener = listenerPair.first;
            Executor executor = listenerPair.second;
            executor.execute(() -> listener.onApduReceived(aid, receivedApdu));
        }
    }

    /**
     * Adds a deactivated notification to the queue of received APDUs.
     *
     * <p>This will notify listeners.
     *
     * @param reason the reason for deactivation
     */
    public void addDeactivated(@NonNull byte[] aid, int reason) {
        for (Pair<Listener, Executor> listenerPair : Collections.synchronizedList(mListeners)) {
            Listener listener = listenerPair.first;
            Executor executor = listenerPair.second;
            executor.execute(() -> listener.onDeactivated(aid, reason));
        }
    }

    public interface Listener {
        void onApduReceived(@NonNull byte[] aid, @NonNull byte[] apdu);
        void onDeactivated(@NonNull byte[] aid, int reason);
    }
}
