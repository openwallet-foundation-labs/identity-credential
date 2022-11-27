package com.android.mdl.app.provisioning;

import static com.android.mdl.app.util.LogginExtensionsKt.log;
import static com.android.mdl.app.util.LogginExtensionsKt.logError;

import android.content.Context;

import androidx.annotation.NonNull;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.UnicodeString;

public class DeleteFlow extends BaseFlow {
    private final Context context;
    private final String serverUrl;

    private DeleteFlow(@NonNull Context context, String serverUrl) {
        this.context = context;
        this.serverUrl = serverUrl;
    }

    public static @NonNull
    DeleteFlow getInstance(@NonNull Context context, String serverUrl) {
        return new DeleteFlow(context, serverUrl);
    }

    private Listener getListener() {
        return (Listener) listener;
    }

    public void setListener(@NonNull Listener listener) {
        this.listener = listener;
    }

    public void sendMessageDelete(byte[] credentialKey) {
        // Instantiate the RequestQueue.
        RequestQueue queue = Volley.newRequestQueue(context);

        // Request a string response from the provided URL.
        CborRequest cborRequest = new CborRequest(
                Request.Method.POST, serverUrl,
                response -> {
                    if (!hasValidMessageType((Map) response, "com.android.identity_credential.DeleteCredentialProveOwnership")) {
                        return;
                    }
                    if (!hasValidSessionId((Map) response)) {
                        return;
                    }

                    byte[] challenge = ((ByteString) ((Map) response).get(new UnicodeString("challenge"))).getBytes();
                    if (challenge == null || challenge.length == 0) {
                        String message = "Response error challenge expected found null or empty";
                        logError(this, message);
                        getListener().onError(message);
                        return;
                    }

                    getListener().onMessageProveOwnership(challenge);

                },
                error -> listener.onError("" + error.getMessage())) {


            @Override
            public byte[] getBody() {
                try {
                    Map map = new Map();
                    map.put(new UnicodeString("messageType"), new UnicodeString("com.android.identity_credential.DeleteCredential"));
                    map.put(new UnicodeString("credentialKey"), CborHelper.decode(credentialKey));
                    return CborHelper.encode(map);
                } catch (IllegalArgumentException e) {
                    String message = "Error sending body request, error: " + e.getMessage();
                    log(this, message, e.fillInStackTrace());
                    getListener().onError(message);
                }
                return new byte[0];
            }
        };

        // Add the request to the RequestQueue.
        queue.add(cborRequest);
    }

    public void sendMessageProveOwnership(byte[] proofOfOwnership) {
        // Instantiate the RequestQueue.
        RequestQueue queue = Volley.newRequestQueue(context);

        // Request a string response from the provided URL.
        CborRequest cborRequest = new CborRequest(
                Request.Method.POST, serverUrl,
                response -> {
                    if (!hasValidMessageType((Map) response, "com.android.identity_credential.DeleteCredentialReadyForDeletion")) {
                        return;
                    }
                    if (!hasValidSessionId((Map) response)) {
                        return;
                    }
                    byte[] challenge = ((ByteString) ((Map) response).get(new UnicodeString("challenge"))).getBytes();
                    if (challenge == null || challenge.length == 0) {
                        String message = "Response error challenge expected found null or empty";
                        logError(this, message);
                        getListener().onError(message);
                        return;
                    }

                    getListener().onMessageReadyForDeletion(challenge);
                },
                error -> listener.onError("" + error.getMessage())) {


            @Override
            public byte[] getBody() {
                try {
                    Map map = new Map();
                    map.put(new UnicodeString("messageType"), new UnicodeString("com.android.identity_credential.DeleteCredentialProveOwnershipResponse"));
                    map.put(new UnicodeString("eSessionId"), new UnicodeString(sessionId));
                    map.put(new UnicodeString("proofOfOwnershipSignature"), CborHelper.decode(proofOfOwnership));
                    return CborHelper.encode(map);
                } catch (IllegalArgumentException e) {
                    String message = "Error sending body request, error: " + e.getMessage();
                    log(this, message, e.fillInStackTrace());
                    getListener().onError(message);
                }
                return new byte[0];
            }
        };

        // Add the request to the RequestQueue.
        queue.add(cborRequest);
    }

    public void sendMessageDeleted(@NonNull byte[] proofOfDeletion) {
        if (this.serverUrl == null) {
            String message = "sendMessageProofOfProvisioning serverUrl is null";
            logError(this, message);
            getListener().onError(message);
            return;
        }

        // Instantiate the RequestQueue.
        RequestQueue queue = Volley.newRequestQueue(context);

        // Request a string response from the provided URL.
        CborRequest cborRequest = new CborRequest(
                Request.Method.POST, serverUrl,
                response -> {
                    if (!hasValidMessageType((Map) response, "EndSessionMessage")) {
                        return;
                    }
                    if (!hasValidSessionId((Map) response)) {
                        return;
                    }
                    String reason = ((UnicodeString) ((Map) response).get(new UnicodeString("reason"))).getString();
                    if (reason != null && reason.equals("Success")) {
                        getListener().onMessageSessionEnd(reason);
                    } else {
                        getListener().onError("EndSessionMessage expected 'Success' found '" + reason + "'");
                    }
                },
                error -> getListener().onError("" + error.getMessage())) {


            @Override
            public byte[] getBody() {
                try {
                    Map map = new Map();
                    map.put(new UnicodeString("messageType"), new UnicodeString("com.android.identity_credential.DeleteCredentialDeleted"));
                    map.put(new UnicodeString("eSessionId"), new UnicodeString(sessionId));
                    map.put(new UnicodeString("proofOfDeletionSignature"), CborHelper.decode(proofOfDeletion));
                    return CborHelper.encode(map);
                } catch (IllegalArgumentException e) {
                    String message = "Error sending body request, error: " + e.getMessage();
                    log(this, message, e.fillInStackTrace());
                    getListener().onError(message);
                }
                return new byte[0];
            }
        };

        // Add the request to the RequestQueue.
        queue.add(cborRequest);
    }

    public interface Listener extends BaseFlow.Listener {

        void onMessageProveOwnership(@NonNull byte[] challenge);

        void onMessageReadyForDeletion(@NonNull byte[] challenge);
    }
}
