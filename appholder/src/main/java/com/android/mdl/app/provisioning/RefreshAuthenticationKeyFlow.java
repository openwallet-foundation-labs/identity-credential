package com.android.mdl.app.provisioning;

import static com.android.mdl.app.util.LogginExtensionsKt.log;
import static com.android.mdl.app.util.LogginExtensionsKt.logError;

import android.content.Context;

import androidx.annotation.NonNull;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

import java.util.ArrayList;
import java.util.List;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.UnicodeString;

public class RefreshAuthenticationKeyFlow extends BaseFlow {
    private final Context context;
    private final String serverUrl;

    private RefreshAuthenticationKeyFlow(@NonNull Context context, String serverUrl) {
        this.context = context;
        this.serverUrl = serverUrl;
    }

    public static @NonNull
    RefreshAuthenticationKeyFlow getInstance(@NonNull Context context, String serverUrl) {
        return new RefreshAuthenticationKeyFlow(context, serverUrl);
    }

    private Listener getListener() {
        return (Listener) listener;
    }

    public void setListener(@NonNull Listener listener) {
        this.listener = listener;
    }

    public void sendMessageCertifyAuthKeys(byte[] credentialKey) {
        // Instantiate the RequestQueue.
        RequestQueue queue = Volley.newRequestQueue(context);

        // Request a string response from the provided URL.
        CborRequest cborRequest = new CborRequest(
                Request.Method.POST, serverUrl,
                response -> {
                    if (!hasValidMessageType((Map) response, "com.android.identity_credential.CertifyAuthKeysProveOwnership")) {
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
                    map.put(new UnicodeString("messageType"), new UnicodeString("com.android.identity_credential.CertifyAuthKeys"));
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
                    if (!hasValidMessageType((Map) response, "com.android.identity_credential.CertifyAuthKeysReady")) {
                        return;
                    }
                    if (!hasValidSessionId((Map) response)) {
                        return;
                    }

                    getListener().onMessageCertifyAuthKeysReady();

                },
                error -> listener.onError("" + error.getMessage())) {


            @Override
            public byte[] getBody() {
                try {
                    Map map = new Map();
                    map.put(new UnicodeString("messageType"), new UnicodeString("com.android.identity_credential.CertifyAuthKeysProveOwnershipResponse"));
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

    public void sendMessageAuthKeyNeedingCertification(byte[] authKeyNeedingCertification) {
        // Instantiate the RequestQueue.
        RequestQueue queue = Volley.newRequestQueue(context);

        // Request a string response from the provided URL.
        CborRequest cborRequest = new CborRequest(
                Request.Method.POST, serverUrl,
                response -> {
                    if (!hasValidMessageType((Map) response, "com.android.identity_credential.CertifyAuthKeysResponse")) {
                        return;
                    }
                    if (!hasValidSessionId((Map) response)) {
                        return;
                    }

                    List<DataItem> staticAuthDataCborList = ((Array) ((Map) response).get(new UnicodeString("staticAuthDatas"))).getDataItems();
                    if (staticAuthDataCborList == null || staticAuthDataCborList.isEmpty()) {
                        String message = "Response error staticAuthDatas expected found null or empty";
                        logError(this, message);
                        getListener().onError(message);
                        return;
                    }

                    List<byte[]> staticAuthDataList = new ArrayList<>();

                    staticAuthDataCborList.forEach(dataItem -> {
                        byte[] staticAuthDataBytes = ((ByteString) dataItem).getBytes();
                        staticAuthDataList.add(staticAuthDataBytes);
                    });

                    getListener().onMessageStaticAuthData(staticAuthDataList);

                },
                error -> listener.onError("" + error.getMessage())) {

            @Override
            public byte[] getBody() {
                try {
                    Map map = new Map();
                    map.put(new UnicodeString("messageType"), new UnicodeString("com.android.identity_credential.CertifyAuthKeysSendCerts"));
                    map.put(new UnicodeString("eSessionId"), new UnicodeString(sessionId));
                    map.put(new UnicodeString("authKeyCerts"), CborHelper.decode(authKeyNeedingCertification));
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

    public void sendMessageRequestEndSession() {
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
                error -> listener.onError("" + error.getMessage())) {

            @Override
            public byte[] getBody() {
                try {
                    Map map = new Map();
                    map.put(new UnicodeString("messageType"), new UnicodeString("RequestEndSession"));
                    map.put(new UnicodeString("eSessionId"), new UnicodeString(sessionId));
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

        void onMessageCertifyAuthKeysReady();

        void onMessageStaticAuthData(@NonNull List<byte[]> staticAuthDataList);
    }
}
