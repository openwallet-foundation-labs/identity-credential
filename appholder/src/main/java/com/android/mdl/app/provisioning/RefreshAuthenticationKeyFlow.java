package com.android.mdl.app.provisioning;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.UnicodeString;

public class RefreshAuthenticationKeyFlow extends BaseFlow {
    private static final String TAG = "RefreshAuthenticationKeyFlow";
    private Context context;
    private String serverUrl;

    private RefreshAuthenticationKeyFlow(@NonNull Context context, String serverUrl) {
        this.context = context;
        this.serverUrl = serverUrl;
    }

    public static @NonNull
    RefreshAuthenticationKeyFlow getInstance(@NonNull Context context, String serverUrl) {
        return new RefreshAuthenticationKeyFlow(context, serverUrl);
    }

    public void setListener(@NonNull Listener listener) {
        this.listener = listener;
    }

    private Listener getListener() {
        return (Listener) listener;
    }

    public void sendMessageCertifyAuthKeys(byte[] credentialKeyCertification) {
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
                        Log.e(TAG, message);
                        getListener().onError(message);
                        return;
                    }

                    getListener().onMessageProveOwnership(challenge);

                },
                error -> listener.onError("" + error.getMessage())) {


            @Override
            public byte[] getBody() {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                CborBuilder builder = new CborBuilder();
                MapBuilder<CborBuilder> map = builder.addMap();

                map.put("messageType", "com.android.identity_credential.CertifyAuthKeys");
                map.put("credentialKey", credentialKeyCertification);

                builder = map.end();

                try {
                    new CborEncoder(outputStream).encode(builder.build());
                } catch (CborException e) {
                    // This should never happen so just adding to Log.
                    String message = "CborEncode Exception: " + e.getMessage();
                    Log.e(TAG, message, e.fillInStackTrace());
                    listener.onError(message);
                }
                return outputStream.toByteArray();
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
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                CborBuilder builder = new CborBuilder();
                MapBuilder<CborBuilder> map = builder.addMap();

                map.put("messageType", "com.android.identity_credential.CertifyAuthKeysProveOwnershipResponse");
                map.put("eSessionId", sessionId);
                map.put("proofOfOwnershipSignature", proofOfOwnership);

                builder = map.end();

                try {
                    new CborEncoder(outputStream).encode(builder.build());
                } catch (CborException e) {
                    // This should never happen so just adding to Log.
                    String message = "CborEncode Exception: " + e.getMessage();
                    Log.e(TAG, message, e.fillInStackTrace());
                    listener.onError(message);
                }
                return outputStream.toByteArray();
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
                        Log.e(TAG, message);
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
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                CborBuilder builder = new CborBuilder();
                MapBuilder<CborBuilder> map = builder.addMap();

                map.put("messageType", "com.android.identity_credential.CertifyAuthKeysSendCerts");
                map.put("eSessionId", sessionId);
                map.put("authKeyCerts", authKeyNeedingCertification);

                builder = map.end();

                try {
                    new CborEncoder(outputStream).encode(builder.build());
                } catch (CborException e) {
                    // This should never happen so just adding to Log.
                    String message = "CborEncode Exception: " + e.getMessage();
                    Log.e(TAG, message, e.fillInStackTrace());
                    listener.onError(message);
                }
                return outputStream.toByteArray();
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
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                CborBuilder builder = new CborBuilder();
                MapBuilder<CborBuilder> map = builder.addMap();

                map.put("messageType", "RequestEndSession");
                map.put("eSessionId", sessionId);

                builder = map.end();

                try {
                    new CborEncoder(outputStream).encode(builder.build());
                } catch (CborException e) {
                    // This should never happen so just adding to Log.
                    String message = "CborEncode Exception: " + e.getMessage();
                    Log.e(TAG, message, e.fillInStackTrace());
                    listener.onError(message);
                }
                return outputStream.toByteArray();
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
