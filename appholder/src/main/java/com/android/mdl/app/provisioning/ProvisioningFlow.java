package com.android.mdl.app.provisioning;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.android.identity.AccessControlProfile;
import com.android.identity.AccessControlProfileId;
import com.android.identity.PersonalizationData;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.UnicodeString;
import co.nstant.in.cbor.model.UnsignedInteger;

public class ProvisioningFlow extends BaseFlow {
    private static final String TAG = "ProvisioningFlow";
    private Context context;
    private String serverUrl;
    private String docType;

    public void setListener(@NonNull Listener listener, Context context) {
        this.listener = listener;
        this.context = context;
    }

    private Listener getListener() {
        return (Listener) listener;
    }

    public void sendMessageStartProvisioning(@NonNull String serverUrl, @Nullable String provisioningCode) {
        this.serverUrl = serverUrl;
        // Instantiate the RequestQueue.
        RequestQueue queue = Volley.newRequestQueue(context);

        // Request a string response from the provided URL.
        CborRequest cborRequest = new CborRequest(
                Request.Method.POST, this.serverUrl,
                response -> {
                    if (!hasValidMessageType((Map) response, "ReadyToProvisionMessage")) {
                        return;
                    }
                    if (!hasValidSessionId((Map) response)) {
                        return;
                    }

                    Log.d(TAG, "Ready to provisioning eSessionId: " + sessionId);

                    getListener().onMessageReadyToProvision();

                },
                error -> getListener().onError("" + error.getMessage())) {


            @Override
            public byte[] getBody() {
                try {
                    Map map = new Map();
                    map.put(new UnicodeString("messageType"), new UnicodeString("StartProvisioning"));
                    map.put(new UnicodeString("provisioningCode"), new UnicodeString(provisioningCode));
                    return CborHelper.encode(map);
                } catch (IllegalArgumentException e) {
                    String message = "Error sending body request, error: " + e.getMessage();
                    Log.e(TAG, message, e.fillInStackTrace());
                    getListener().onError(message);
                }
                return new byte[0];
            }
        };

        // Add the request to the RequestQueue.
        queue.add(cborRequest);
    }

    public void sendMessageStartIdentityCredentialProvision() {
        if (this.serverUrl == null) {
            String message = "sendMessageStartIdentityCredentialProvision serverUrl is null";
            Log.e(TAG, message);
            getListener().onError(message);
            return;
        }

        // Instantiate the RequestQueue.
        RequestQueue queue = Volley.newRequestQueue(context);

        // Request a string response from the provided URL.
        CborRequest cborRequest = new CborRequest(
                Request.Method.POST, serverUrl,
                response -> {
                    if (!hasValidMessageType((Map) response, "com.android.identity_credential.ProvisioningResponse")) {
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
                    docType = ((UnicodeString) ((Map) response).get(new UnicodeString("docType"))).getString();
                    if (docType == null || !docType.equals("org.iso.18013.5.1.mDL")) {
                        String message = "Response error docType not supported: '" + docType + "'";
                        Log.e(TAG, message);
                        getListener().onError(message);
                        return;
                    }

                    Log.d(TAG, "Provisioning Response eSessionId: " + sessionId +
                            " challenge: " + Arrays.toString(challenge) +
                            " docType: " + docType);

                    getListener().onMessageProvisioningResponse(docType, challenge);

                },
                error -> getListener().onError("" + error.getMessage())) {


            @Override
            public byte[] getBody() {
                try {
                    Map map = new Map();
                    map.put(new UnicodeString("messageType"), new UnicodeString("com.android.identity_credential.StartProvisioning"));
                    map.put(new UnicodeString("eSessionId"), new UnicodeString(sessionId));
                    return CborHelper.encode(map);
                } catch (IllegalArgumentException e) {
                    String message = "Error sending body request, error: " + e.getMessage();
                    Log.e(TAG, message, e.fillInStackTrace());
                    getListener().onError(message);
                }
                return new byte[0];
            }
        };

        // Add the request to the RequestQueue.
        queue.add(cborRequest);
    }

    public void sendMessageSetCertificateChain(byte[] credentialKeyCertification) {
        if (this.serverUrl == null) {
            String message = "sendMessageSetCertificateChain serverUrl is null";
            Log.e(TAG, message);
            getListener().onError(message);
            return;
        }

        // Instantiate the RequestQueue.
        RequestQueue queue = Volley.newRequestQueue(context);

        // Request a string response from the provided URL.
        CborRequest cborRequest = new CborRequest(
                Request.Method.POST, serverUrl,
                response -> {
                    if (!hasValidMessageType((Map) response, "com.android.identity_credential.DataToProvisionMessage")) {
                        return;
                    }
                    if (!hasValidSessionId((Map) response)) {
                        return;
                    }

                    List<DataItem> accessControlProfiles = ((Array) ((Map) response).get(new UnicodeString("accessControlProfiles"))).getDataItems();
                    if (accessControlProfiles == null || accessControlProfiles.isEmpty()) {
                        String message = "Response error accessControlProfiles expected found null or empty";
                        Log.e(TAG, message);
                        getListener().onError(message);
                        return;
                    }
                    Map nameSpaces = (Map) ((Map) response).get(new UnicodeString("nameSpaces"));
                    if (nameSpaces == null || nameSpaces.getKeys().isEmpty()) {
                        String message = "Response error nameSpaces expected found null or empty";
                        Log.e(TAG, message);
                        getListener().onError(message);
                        return;
                    }

                    PersonalizationData.Builder personalizationDataBuilder = new PersonalizationData.Builder();
                    HashMap<String, String> names = new HashMap<>();

                    // Parse Cbor Structure to object

                    // Parse accessControlProfiles
                    accessControlProfiles.forEach(profileDataItem -> {
                        Map profileMap = (Map) profileDataItem;
                        int id = ((UnsignedInteger) profileMap.get(new UnicodeString("id"))).getValue().intValue();
                        boolean userAuthenticationRequired = profileMap.get(new UnicodeString("userAuthenticationRequired")) == SimpleValue.TRUE;
                        int timeoutMillis = ((UnsignedInteger) profileMap.get(new UnicodeString("timeoutMillis"))).getValue().intValue();

                        AccessControlProfile accessControlProfile = new AccessControlProfile
                                .Builder(new AccessControlProfileId(id))
                                .setUserAuthenticationRequired(userAuthenticationRequired)
                                .setUserAuthenticationTimeout(timeoutMillis)
                                .build();
                        personalizationDataBuilder.addAccessControlProfile(accessControlProfile);
                    });

                    //Parse documentItems
                    nameSpaces.getKeys().forEach(nameSpaceKey -> {
                        Array documentItems = (Array) nameSpaces.get(nameSpaceKey);
                        String nameSpace = ((UnicodeString) nameSpaceKey).getString();

                        documentItems.getDataItems().forEach(docItemDataItem -> {
                            Map docItemMap = (Map) docItemDataItem;
                            //{"name": "family_name", "value": "Mustermann", "accessControlProfiles": [0]}
                            String name = ((UnicodeString) docItemMap.get(new UnicodeString("name"))).getString();
                            DataItem value = docItemMap.get(new UnicodeString("value"));
                            List<DataItem> docAccessControlProfiles = ((Array) docItemMap.get(new UnicodeString("accessControlProfiles"))).getDataItems();

                            List<AccessControlProfileId> accessControlProfileIds = new ArrayList<>();
                            docAccessControlProfiles.forEach(idDataItem -> {
                                int id = ((UnsignedInteger) idDataItem).getValue().intValue();
                                accessControlProfileIds.add(new AccessControlProfileId(id));
                            });

                            switch (value.getMajorType()) {
                                case UNICODE_STRING:
                                    personalizationDataBuilder.putEntryString(
                                            nameSpace,
                                            name,
                                            accessControlProfileIds,
                                            ((UnicodeString) value).getString()
                                    );
                                    names.put(name, ((UnicodeString) value).getString());
                                    break;
                                case BYTE_STRING:
                                    personalizationDataBuilder.putEntryBytestring(
                                            nameSpace,
                                            name,
                                            accessControlProfileIds,
                                            ((ByteString) value).getBytes()
                                    );
                                    break;
                                case SPECIAL:
                                    personalizationDataBuilder.putEntryBoolean(
                                            nameSpace,
                                            name,
                                            accessControlProfileIds,
                                            value == SimpleValue.TRUE
                                    );
                                    break;
                            }
                        });
                    });

                    String visibleName = (names.get("given_name") + "0").charAt(0) + ". " + names.get("family_name");

                    getListener().onMessageDataToProvision(visibleName, personalizationDataBuilder.build());

                },
                error -> getListener().onError("" + error.getMessage())) {


            @Override
            public byte[] getBody() {
                try {
                    Map map = new Map();
                    map.put(new UnicodeString("messageType"), new UnicodeString("com.android.identity_credential.SetCertificateChain"));
                    map.put(new UnicodeString("eSessionId"), new UnicodeString(sessionId));
                    map.put(new UnicodeString("credentialKeyCertificateChain"), new ByteString(credentialKeyCertification));
                    return CborHelper.encode(map);
                } catch (IllegalArgumentException e) {
                    String message = "Error sending body request, error: " + e.getMessage();
                    Log.e(TAG, message, e.fillInStackTrace());
                    getListener().onError(message);
                }
                return new byte[0];
            }
        };

        // Add the request to the RequestQueue.
        queue.add(cborRequest);
    }

    public void sendMessageProofOfProvisioning(byte[] proofOfProvisioning) {
        if (this.serverUrl == null) {
            String message = "sendMessageProofOfProvisioning serverUrl is null";
            Log.e(TAG, message);
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
                    map.put(new UnicodeString("messageType"), new UnicodeString("com.android.identity_credential.SetProofOfProvisioning"));
                    map.put(new UnicodeString("eSessionId"), new UnicodeString(sessionId));
                    map.put(new UnicodeString("proofOfProvisioningSignature"), CborHelper.decode(proofOfProvisioning));
                    return CborHelper.encode(map);
                } catch (IllegalArgumentException e) {
                    String message = "Error sending body request, error: " + e.getMessage();
                    Log.e(TAG, message, e.fillInStackTrace());
                    getListener().onError(message);
                }
                return new byte[0];
            }
        };

        // Add the request to the RequestQueue.
        queue.add(cborRequest);
    }

    public interface Listener extends BaseFlow.Listener {

        void onMessageReadyToProvision();

        void onMessageProvisioningResponse(@NonNull String docType, @NonNull byte[] challenge);

        void onMessageDataToProvision(String visibleName, PersonalizationData personalizationData);
    }
}
