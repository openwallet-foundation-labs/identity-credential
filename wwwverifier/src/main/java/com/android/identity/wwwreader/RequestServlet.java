/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.identity.wwwreader;

import com.android.identity.cbor.Cbor;
import com.android.identity.cbor.CborArray;
import com.android.identity.cbor.DiagnosticOption;
import com.android.identity.crypto.Algorithm;
import com.android.identity.crypto.Crypto;
import com.android.identity.crypto.EcPrivateKey;
import com.android.identity.crypto.EcPublicKey;
import com.android.identity.mdoc.connectionmethod.ConnectionMethod;
import com.android.identity.mdoc.connectionmethod.ConnectionMethodHttp;
import com.android.identity.mdoc.engagement.EngagementGenerator;
import com.android.identity.mdoc.engagement.EngagementParser;
import com.android.identity.mdoc.origininfo.OriginInfo;
import com.android.identity.mdoc.origininfo.OriginInfoDomain;
import com.android.identity.mdoc.request.DeviceRequestGenerator;
import com.android.identity.mdoc.response.DeviceResponseParser;
import com.android.identity.mdoc.sessionencryption.SessionEncryption;
import com.android.identity.crypto.EcCurve;
import com.android.identity.util.Timestamp;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

// Java servlet imports
import java.io.IOException;
import java.util.OptionalLong;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

// imports for Datastore
import com.google.appengine.api.datastore.Blob;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.Text;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * This servlet performs three main functions:
 * (1) Generates a mdoc:// URI upon button click, containing a ReaderEngagement CBOR message
 * (2) Parses a POST request containing a SessionEstablishment CBOR message, and generates
 * a DeviceRequest message.
 * (3) Parses a POST request containing a DeviceResponse CBOR message, and sends back a JSON
 * containing data extracted from the message.
 */
@WebServlet("/request-mdl/*")
public class RequestServlet extends HttpServlet {

    public static final DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    
    private static boolean mStoreLogs = false;
    private static final String bucketName = "mdoc-reader-external.appspot.com"; // The ID of your GCS bucket

   /**
    * Handles two types of HTTP GET requests:
    * (1) A request to create a new session, which involves creating a new entity in Datastore
    * (2) A request to retrieve information from DeviceResponse from an existing session
    * (3) A request to retrieve the logs from an existing session in order to be displayed
    *
    * <p> Adds the requested information to the given response as a String, containing either
    * (1) a generated mdoc:// URI and unique Datastore key or
    * (2) parsed DeviceResponse data or
    * (3) formatted logs
    */
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("text/html;");
        String[] pathArr = parsePathInfo(request);

        if (pathArr[0].equals(ServletConsts.SESSION_URL)) {
            Map<String, String[]> urlQueryParams = request.getParameterMap();
            String[] requestedAttributes = urlQueryParams.getOrDefault(
                    ServletConsts.REQUESTED_ATTRIBUTES_QUERY, new String[]{"all"});
            if (requestedAttributes.length != 1) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            response.getWriter().println(createNewSession(requestedAttributes[0]));

        } else if (pathArr[0].equals(ServletConsts.RESPONSE_URL) && pathArr.length == 2) {
            Key key = com.google.appengine.api.datastore.KeyFactory.stringToKey(pathArr[1]);
            response.getWriter().println(getDeviceResponse(key));

        } else if (pathArr[0].equals(ServletConsts.DISPLAY_LOGS_URL) && pathArr.length == 2) {
            Key key = com.google.appengine.api.datastore.KeyFactory.stringToKey(pathArr[1]);
            response.getWriter().println(getLogs(key));
        }
    }

    /**
     * Handles two distinct POST requests:
     * (1) The first POST request sent to the servlet, which contains a MessageData message
     * (2) The second POST request sent to the servlet, which contains a DeviceResponse message
     *
     * @param request encoded CBOR message
     * @return (1) response, containing a DeviceRequest message as an encoded byte array;
     * (2) response, containing a termination message with status code 20
     */
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String[] pathArr = parsePathInfo(request);
        Key key = com.google.appengine.api.datastore.KeyFactory.stringToKey(pathArr[0]);
        if (getNumPostRequests(key) == 0) {
            byte[] sessionData;
            try {
                sessionData = createDeviceRequest(getBytesFromRequest(request), key);
            } catch (Exception e) {
                log(key, String.format("[%s] Check: Provide \"RestApi\" request: Failed\n",
                        millisToSec(System.currentTimeMillis())));
                log(key, String.format("[%s] Comment: %s\n", millisToSec(System.currentTimeMillis()), e));
                throw e;
            }
            log(key, String.format("[%s] Check: Provide \"RestApi\" request: OK\n",
                    millisToSec(System.currentTimeMillis())));
            setNumPostRequests(1, key);
            response.getOutputStream().write(sessionData);

        } else if (getNumPostRequests(key) == 1) {
            log(key, String.format("[%s] Check: Receive \"RestApi\" response: OK\n",
                    millisToSec(System.currentTimeMillis())));
            byte[] terminationMessage = parseDeviceResponse(getBytesFromRequest(request), key);
            setNumPostRequests(2, key);
            response.getOutputStream().write(terminationMessage);

        } else {
            log(key, String.format("[%s] Check: Receive \"RestApi\" response: FAILED\n",
                    millisToSec(System.currentTimeMillis())));
            log(key, String.format("[%s] Comment: Received more than one POST request\n",
                    millisToSec(System.currentTimeMillis())));
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    /**
     * Set whether the logs generated should be stored in cloud storage
     *
     * @param storeLogs whether logs should be stored
     */
    public void setStoreLogs(boolean storeLogs) {
        mStoreLogs = storeLogs;
    }

    /**
     * @param request Either a GET or POST request
     * @return a String array containing the parsed path information, which includes at least
     * one of (1) a String specifying the type of GET request (2) a unique key that corresponds
     * to a previously created session
     */
    public static String[] parsePathInfo(HttpServletRequest request) {
        String pathInfo = request.getPathInfo().substring(1);
        return pathInfo.split("/");
    }

    /**
     * Creates a new Entity in Datastore for the new session and generates ReaderEngagement.
     *
     * @param requestedAttributes either "SCE_REST_1" or "SCE_REST_2", in order to indicate what
     *                            attributes should be included in the request
     * @return String containing the generated mdoc URL and the unique key tied to the
     * new session's entry in Datastore, separated with a comma
     */
    public static String createNewSession(String requestedAttributes) {
        Entity entity = new Entity(ServletConsts.ENTITY_TYPE);
        entity.setProperty(ServletConsts.TIMESTAMP_PROP,
            new java.sql.Timestamp(System.currentTimeMillis()).toString());
        entity.setUnindexedProperty(ServletConsts.REQ_ATTR_PROP, requestedAttributes);
        ds.put(entity);
        Key key = entity.getKey();
        
        String keyStr = com.google.appengine.api.datastore.KeyFactory.keyToString(key);
        setNumPostRequests(0, key);
        String toReturn = createMdocUri(key) + ServletConsts.SESSION_SEPARATOR + keyStr;

        log(key, "Verifier: RO-16\n");
        log(key, "Protocol: RestApi\n");
        log(key, "Transaction: " + key.getId() + "\n");
        log(key, "Started: " + millisToSec(System.currentTimeMillis()) + "\n");
        String scenario = requestedAttributes.equals("all")? "SCE_REST_1" : "SCE_REST_2";
        log(key, "Scenario: " + scenario + "\n");

        return toReturn;
    }

    private static String millisToSec(long timeMillis) {
        String millisString = Long.toString(timeMillis);
        int len = millisString.length();
        return millisString.substring(0, len-3) + "." + millisString.substring(len-3, len);
    }

    /**
    * Generates ReaderEngagement CBOR message, and creates a URI from it.
    *
    * @param key Unique key tied to an existing entity in Datastore
    * @return Generated mdoc:// URI
    */
    private static String createMdocUri(Key key) {
        EcPrivateKey readerKey = Crypto.createEcPrivateKey(EcCurve.P256);
        byte[] re = generateReaderEngagement(readerKey.getPublicKey(), key);

        // put ReaderEngagement and generated ephemeral keys into Datastore
        setDatastoreProp(ServletConsts.RE_PROP, re, key);
        setDatastoreProp(ServletConsts.PRIVKEY_PROP, Cbor.encode(readerKey.toCoseKey(Map.of()).getToDataItem()), key);

        String reStr = Base64.getUrlEncoder().withoutPadding().encodeToString(re).replace("\n","");
  
        return ServletConsts.MDOC_PREFIX + reStr;
    }

     /**
      * Parses the MessageData CBOR message to isolate DeviceEngagement. DeviceEngagement
      * is then used to create a DeviceRequest CBOR message.
      *
      * @param messageData CBOR message extracted from POST request
      * @param key Key corresponding to an entity in Datastore
      * @return SessionData CBOR message containing DeviceRequest
      */
    private static byte[] createDeviceRequest(byte[] messageData, Key key) {
        byte[] encodedEngagement =
                Cbor.decode(messageData).get("deviceEngagementBytes").getAsBstr();
        log(key, String.format("[%s] Comment: Received Engagement CBOR %s\n",
            millisToSec(System.currentTimeMillis()), cborPrettyPrint(encodedEngagement)));
        EcPrivateKey eReaderKey = getPrivateKey(getDatastoreProp(ServletConsts.PRIVKEY_PROP, key));

        EngagementParser.Engagement engagement = new EngagementParser(encodedEngagement).parse();
        EcPublicKey eDeviceKeyPublic = engagement.getESenderKey();
        byte[] encodedEDeviceKeyPublic = Cbor.encode(eDeviceKeyPublic.toCoseKey(Map.of()).getToDataItem());
        setDatastoreProp(ServletConsts.DEVKEY_PROP, encodedEDeviceKeyPublic, key);
        verifyOriginInfo(engagement.getOriginInfos(), key);

        byte[] sessionTranscript = buildSessionTranscript(encodedEngagement, eReaderKey.getPublicKey(), key);
        setDatastoreProp(ServletConsts.TRANSCRIPT_PROP, sessionTranscript, key);

        SessionEncryption ser = new SessionEncryption(
                SessionEncryption.Role.MDOC_READER,
                eReaderKey,
                eDeviceKeyPublic,
                sessionTranscript);
        ser.setSendSessionEstablishment(false);
        byte[] dr = new DeviceRequestGenerator(sessionTranscript)
            .addDocumentRequest(
                    ServletConsts.MDL_DOCTYPE,
                    createMdlItemsToRequest(key),
                    null,
                    null,
                    Algorithm.UNSET,
                    null)
            .generate();
        return ser.encryptMessage(dr, null);
    }

    /**
     * Generates session transcript using the device engagement
     * @param de the reader key
     * @param eReaderKeyPublic the reader engagement
     * @param key the datastore key
     */
    public static byte[] buildSessionTranscript(byte[] de, EcPublicKey eReaderKeyPublic, Key key) {
        byte[] re = getDatastoreProp(ServletConsts.RE_PROP, key);
        return Cbor.encode(CborArray.Companion.builder()
                        .addTaggedEncodedCbor(de)
                        .addTagged(24, eReaderKeyPublic.toCoseKey(Map.of()).getToDataItem())
                        .addTaggedEncodedCbor(re)
                        .end().build());
    }

    /**
     * Verify that the base URL of the origin info found in the given origin infos matches
     * the base URL of the website (ServletConsts.BASE_URL).
     *
     * @param oiList list of origin info objects
     * @param key Unique identifier corresponding to the current session
     */
    private static void verifyOriginInfo(List<OriginInfo> oiList, Key key) {
        if (oiList.size() > 0) {
            String oiUrl = ((OriginInfoDomain) oiList.get(0)).getUrl();
            if (!oiUrl.equals(ServletConsts.BASE_URL)) {
                setOriginInfoStatus(ServletConsts.OI_FAILURE_START +
                    oiUrl + ServletConsts.OI_FAILURE_END, key);
                log(key, String.format("[%s] Comment: OriginInfo URL %s\n",
                        millisToSec(System.currentTimeMillis()), oiUrl));
            } else {
                setOriginInfoStatus(ServletConsts.OI_SUCCESS, key);
            }
        } else {
            setOriginInfoStatus(ServletConsts.OI_FAILURE_START +
                ServletConsts.OI_FAILURE_END.trim(), key);
            log(key, String.format("[%s] Comment: OriginInfo is incorrectly formatted. " +
                    "We expect OriginInfo = {\"cat\": uint, \"type\": uint, \"details\": tstr} in " +
                    "accordance with the recent CD consultation of 18013-7.\n",
                    millisToSec(System.currentTimeMillis())));
        }
    }

    /**
     * @return Map of items to request from the mDL app
     */
    private static Map<String, Map<String, Boolean>> createMdlItemsToRequest(Key key) {
        Entity entity = getEntity(key);
        String requestedAttributes = (String) entity.getProperty(ServletConsts.REQ_ATTR_PROP);
        if (requestedAttributes.equals("age")){
            return createMdlItemsToRequestAge18();
        } 
        return createMdlItemsToRequestFull();
        
    }

    private static Map<String, Map<String, Boolean>> createMdlItemsToRequestFull() {
        Map<String, Map<String, Boolean>> mdlItemsToRequest = new HashMap<>();
        Map<String, Boolean> mdlNsItems = new HashMap<>();
        mdlNsItems.put("sex", false);
        mdlNsItems.put("portrait", false);
        mdlNsItems.put("given_name", false);
        mdlNsItems.put("issue_date", false);
        mdlNsItems.put("expiry_date", false);
        mdlNsItems.put("family_name", false);
        mdlNsItems.put("document_number", false);
        mdlNsItems.put("issuing_authority", false);
        mdlNsItems.put("birth_date", false);
        mdlNsItems.put("issuing_country", false);
        mdlNsItems.put("driving_privileges", false);
        mdlNsItems.put("un_distinguishing_sign", false);
        mdlItemsToRequest.put(ServletConsts.MDL_NAMESPACE, mdlNsItems);

        Map<String, Boolean> aamvaNsItems = new HashMap<>();
        aamvaNsItems.put("DHS_compliance", false);
        aamvaNsItems.put("EDL_credential", false);
        mdlItemsToRequest.put(ServletConsts.AAMVA_NAMESPACE, aamvaNsItems);

        return mdlItemsToRequest;
    }

    private static Map<String, Map<String, Boolean>> createMdlItemsToRequestAge18() {
        Map<String, Map<String, Boolean>> mdlItemsToRequest = new HashMap<>();
        Map<String, Boolean> mdlNsItems = new HashMap<>();
        mdlNsItems.put("age_over_18", false);
        mdlItemsToRequest.put(ServletConsts.MDL_NAMESPACE, mdlNsItems);
        return mdlItemsToRequest;
    }

    /**
     * @return generated readerEngagement CBOR message, using EngagementGenerator
     */
    public static byte[] generateReaderEngagement(EcPublicKey publicKey, Key key) {
        EngagementGenerator eg = new EngagementGenerator(publicKey,
                EngagementGenerator.ENGAGEMENT_VERSION_1_1);
        List<ConnectionMethod> connectionMethods = new ArrayList<>();
        connectionMethods.add(new ConnectionMethodHttp(ServletConsts.ABSOLUTE_URL + "/"
                + com.google.appengine.api.datastore.KeyFactory.keyToString(key)));
        eg.addConnectionMethods(connectionMethods);
        return eg.generate();
    }

    /**
     * @return byte array with data extracted from HTTP POST request @param request
     */
    private static byte[] getBytesFromRequest(HttpServletRequest request) {
        byte[] arr = new byte[request.getContentLength()];
        try {
            request.getInputStream().read(arr);
            return arr;
        } catch (IOException e) {
            throw new IllegalStateException("Error reading request body", e);
        }
    }

    /**
     * Parses DeviceResponse CBOR message and converts its contents into a JSON string.
     * 
     * @param messageData CBOR message containing DeviceResponse to be decoded and parsed
     * @param key Unique key of the entity in Datastore that corresponds to the current
     * session
     * 
     * @return byte array containing an empty SessionData message with status code 20
     * to indicate termination.
     */
    private static byte[] parseDeviceResponse(byte[] messageData, Key key) throws IOException {
        EcPrivateKey eReaderKey = getPrivateKey(getDatastoreProp(ServletConsts.PRIVKEY_PROP, key));
        EcPublicKey eDeviceKeyPublic =
            getPublicKey(getDatastoreProp(ServletConsts.DEVKEY_PROP, key));
        byte[] sessionTranscript = getDatastoreProp(ServletConsts.TRANSCRIPT_PROP, key);

        SessionEncryption ser;
        DeviceResponseParser.DeviceResponse dr;
        try {
            ser = new SessionEncryption(SessionEncryption.Role.MDOC_READER,
                    eReaderKey,
                    eDeviceKeyPublic,
                    sessionTranscript);
            ser.setSendSessionEstablishment(false);
            
            dr = new DeviceResponseParser(
                    ser.decryptMessage(messageData).getFirst(),
                    sessionTranscript)
                .setEphemeralReaderKey(eReaderKey)
                .parse(); 
        } catch (Exception e) {
            log(key, String.format("[%s] Check: Decrypt response: FAILED\n",
                    millisToSec(System.currentTimeMillis())));
            log(key, String.format("[%s] Comment: %s\n", millisToSec(System.currentTimeMillis()), e));
            throw new IOException(e);
        }
        log(key, String.format("[%s] Check: Decrypt response: OK\n",
                millisToSec(System.currentTimeMillis())));
        String json = new Gson().toJson(buildArrayFromDocuments(dr.getDocuments(), key));
        setDeviceResponse(json, key);

        return ser.encryptMessage(null, 20L);
    }

    /**
     * Extracts specified fields from each DeviceResponseParser.Document object within @param docs
     * 
     * @param docs List of Document objects containing Mdoc information that had been requested
     * from the app in an earlier POST request
     * @param key Unique identifier that corresponds to the current session
     * @return ArrayList of String data extracted from @param docs that will be displayed
     * on the website
     */
    private static ArrayList<String> buildArrayFromDocuments(List<DeviceResponseParser.Document> docs, Key key) {
        ArrayList<String> arr = new ArrayList<>();
        List<String> entriesForVData3Check = new ArrayList<>();

        arr.add(getOriginInfoStatus(key));
        arr.add("Number of documents returned: " + docs.size());
        for (DeviceResponseParser.Document doc : docs) {
            arr.add("Doctype: " + doc.getDocType());
            if (doc.getIssuerSignedAuthenticated()) {
                arr.add(ServletConsts.CHECKMARK + "Issuer Signed Authenticated");
            }

            if (doc.getDeviceSignedAuthenticatedViaSignature()) {
                arr.add(ServletConsts.CHECKMARK + "Device Signed Authenticated (ECDSA)");
            } else if (doc.getDeviceSignedAuthenticated()) {
                arr.add(ServletConsts.CHECKMARK + "Device Signed Authenticated");
            }

            arr.add("MSO");
            arr.add(ServletConsts.CHECKMARK + "Signed: "
                + timestampToString(doc.getValidityInfoSigned()));
            arr.add(ServletConsts.CHECKMARK + "Signed: "
                + timestampToString(doc.getValidityInfoValidFrom()));
            arr.add(ServletConsts.CHECKMARK + "Signed: "
                + timestampToString(doc.getValidityInfoValidUntil()));
            arr.add(ServletConsts.CHECKMARK + "DeviceKey: "
                    + doc.getDeviceKey());
            List<String> issuerNamespaces = doc.getIssuerNamespaces();
            for (String namespace : issuerNamespaces) {
                arr.add(ServletConsts.BOLD + "Namespace: " + ServletConsts.BOLD + namespace);
                List<String> entryNames = doc.getIssuerEntryNames(namespace);
                for (String entry : entryNames) {
                    String val = "";
                    switch (entry) {
                        case "portrait":
                            val = numBytesAsString(doc.getIssuerEntryByteString(namespace, entry));
                            byte[] byteArr = doc.getIssuerEntryByteString(namespace, entry);
                            String bytes = Base64.getEncoder().withoutPadding().encodeToString(byteArr);
                            arr.add("portraitBytes" + ": " + bytes);
                            break;
                        case "issue_date":
                        case "expiry_date":
                            byte[] value = doc.getIssuerEntryData(namespace, entry);
                            val = Cbor.decode(value).getAsTagged().getAsTstr();
                            break;
                        case "family_name":
                        case "given_name":
                        case "issuing_authority":
                        case "DHS_compliance":
                        case "EDL_credential":
                        case "document_number":
                        case "un_distinguishing_sign":
                        case "birth_date":
                        case "issuing_country":
                            val = doc.getIssuerEntryString(namespace, entry);
                            break;
                        case "sex":
                            val = Long.toString(doc.getIssuerEntryNumber(namespace, entry));
                            break;
                        case "age_over_18":
                            val = doc.getIssuerEntryBoolean(namespace, entry)? "True" : "False";
                            break;
                        default:
                            val = Base64.getEncoder().encodeToString(
                                doc.getIssuerEntryData(namespace, entry));
                    }
                    arr.add(ServletConsts.CHECKMARK + entry + ": " + val);
                    entriesForVData3Check.add(entry);
                }
            }
        }

        Entity entity = getEntity(key);
        String requestedAttributes = (String) entity.getProperty(ServletConsts.REQ_ATTR_PROP);
        List<String> requestedAll = Arrays.asList("sex", "portrait", "given_name", "issue_date",
                "expiry_date", "family_name", "document_number", "issuing_authority", "birth_date",
                "issuing_country", "driving_privileges", "un_distinguishing_sign", "DHS_compliance",
                "EDL_credential");
        List<String> expected = requestedAttributes.equals("age")?
                Collections.singletonList("age_over_18") : requestedAll;

        boolean vData3Check;
        if (requestedAttributes.equals("age")){
            vData3Check = entriesForVData3Check.contains("age_over_18");
            vData3Check &= entriesForVData3Check.size() == 1;
        } else {
            vData3Check = entriesForVData3Check.containsAll(requestedAll);
            vData3Check &= entriesForVData3Check.size() == requestedAll.size();
        }

        if (!vData3Check) {
            log(key, String.format("[%s] Check: Receive expected data set: FAILED\n",
                    millisToSec(System.currentTimeMillis())));
            log(key, String.format("[%s] Comment: Received %s, Expected %s\n",
                millisToSec(System.currentTimeMillis()), entriesForVData3Check, expected));
        } else {
            log(key, String.format("[%s] Check: Receive expected data set: OK\n",
                    millisToSec(System.currentTimeMillis())));
        }
        return arr;
    }

    /**
     * @return A String in the form of "(# bytes)", representing the number of bytes
     * in @param arr
     */
    private static String numBytesAsString(byte[] arr) {
        return "(" + arr.length + " bytes)";
    }

    /**
     * @return Datastore entity linked to the key @param key created at the start
     * of the session
     */
    private static Entity getEntity(Key key) {
        try {
            return ds.get(key);
        } catch (EntityNotFoundException e) {
            throw new IllegalStateException("Entity could not be found in database", e);
        }
    }

    /**
     * @return String converted from a Timestamp object @param ts
     */
    private static String timestampToString(Timestamp ts) {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        return df.format(new Date(ts.toEpochMilli()));
    }

    /**
     * @return PublicKey, converted from a byte array @param arr
     */
    private static EcPublicKey getPublicKey(byte[] arr) {
        return Cbor.decode(arr).getAsCoseKey().getEcPublicKey();
    }

    /**
     * @return PrivateKey, converted from a byte array @param arr
     */
    private static EcPrivateKey getPrivateKey(byte[] arr) {
        return Cbor.decode(arr).getAsCoseKey().getEcPrivateKey();
    }

    /**
     * Updates the entity in Datastore with new data in the form of a byte array
     * 
     * @param propName Name of the property that should be inserted into Datastore
     * @param arr data that should be inserted into Datastore
     * @param key Unique key of the entity in Datastore that should be updated
     */
    public static void setDatastoreProp(String propName, byte[] arr, Key key) {
        Entity entity = getEntity(key);
        entity.setProperty(propName, new Blob(arr));
        ds.put(entity);
    }

    /**
     * Retrieves byte array data from Datastore
     * 
     * @param propName Name of the property that should be retrieved from Datastore
     * @param key Unique key of entity in Datastore
     */
    public static byte[] getDatastoreProp(String propName, Key key) {
        return ((Blob) getEntity(key).getProperty(propName)).getBytes();
    }

    /**
     * @param num Number of POST requests that have been sent to the current session so far
     * @param key Unique key of the entity in Datastore that should be updated
     */
    public static void setNumPostRequests(int num, Key key) {
        Entity entity = getEntity(key);
        entity.setProperty(ServletConsts.NUM_POSTS_PROP, num);
        ds.put(entity);
    }

    /**
     * @param key Unique identifier that corresponds to the current session
     * @return (as int) number of POST requests that have been sent to the current session so far
     */
    private static int getNumPostRequests(Key key) {
        return ((Long) getEntity(key).getProperty(ServletConsts.NUM_POSTS_PROP)).intValue();
    }

    /**
     * @param text Parsed Device Response message
     * @param key Unique identifier that corresponds to the current session
     */
    public static void setDeviceResponse(String text, Key key) {
        Entity entity = getEntity(key);
        entity.setProperty(ServletConsts.DEV_RESPONSE_PROP, new Text(text));
        ds.put(entity);
    }

    /**
     * Appends to a log for a particular key
     * 
     * @param key key corresponding to an entity in Datastore
     * @param textToLog the text to be appended to the log
     */
    public static void log(Key key, String textToLog) {
        Entity entity = getEntity(key);
        
        if (entity.hasProperty(ServletConsts.LOGS_PROP)) {
            String existingLog = ((Text) entity.getProperty(ServletConsts.LOGS_PROP)).getValue();
            textToLog = existingLog + textToLog;
        } 
        entity.setUnindexedProperty(ServletConsts.LOGS_PROP, new Text(textToLog));
        ds.put(entity);

        try {
            uploadObjectFromMemory(String.format("M-2-%d.txt", key.getId()), textToLog);
        } catch (IOException ignored) { }
    }

    /**
     * Retrieves logs from Datastore.
     * 
     * @param key key corresponding to an entity in Datastore
     * @return String containing logs, or an empty string if it does not exist
     */
    public static String getLogs(Key key) {
        Entity entity = getEntity(key);
        if (entity.hasProperty(ServletConsts.LOGS_PROP)) {
            Text log = (Text) entity.getProperty(ServletConsts.LOGS_PROP);
            return log.getValue().replaceAll("\\\\u0027", "'"); // render apostrophe
        }
        return "";
    }

    private static String cborPrettyPrint(byte[] encodedCbor) {
        return Cbor.toDiagnostics(encodedCbor,
                Set.of(DiagnosticOption.PRETTY_PRINT, DiagnosticOption.EMBEDDED_CBOR));
    }

    /**
     * Uploads data to Google Cloud storage bucket
     * 
     * @param objectName The ID of your GCS object
     * @param contents The string of contents you wish to upload
     * @throws IOException When creating a new blob encounters an I/O error
     */
    public static void uploadObjectFromMemory(String objectName, String contents) throws IOException {
        if (mStoreLogs) {
            // The ID of your GCP project
            String projectId = System.getenv("GOOGLE_CLOUD_PROJECT");

            Storage storage = StorageOptions.newBuilder().setProjectId(projectId).build().getService();
            BlobId blobId = BlobId.of(bucketName, objectName);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
            byte[] content = contents.getBytes(StandardCharsets.UTF_8);
            storage.createFrom(blobInfo, new ByteArrayInputStream(content));
        }
    }

    /**
     * Retrieves DeviceResponse message from Datastore.
     * 
     * @param key key corresponding to an entity in Datastore
     * @return String containing DeviceResponse message, or an empty string if it does not exist
     */
    public static String getDeviceResponse(Key key) {
        Entity entity = getEntity(key);
        if (entity.hasProperty(ServletConsts.DEV_RESPONSE_PROP)) {
            Text deviceResponse = (Text) entity.getProperty(ServletConsts.DEV_RESPONSE_PROP);
            return deviceResponse.getValue().replaceAll("\\\\u0027", "'"); // render apostrophe
        }
        return "";
    }

    /**
     * Sets property in Datastore indicating whether the OriginInfo base URL from the
     * DeviceEngagement message received from the app matches the website's base URL.
     * 
     * @param status String message to be stored in Datastore
     * @param key Key corresponding to an entity in Datastore assigned to the current session
     */
    public static void setOriginInfoStatus(String status, Key key) {
        Entity entity = getEntity(key);
        entity.setProperty(ServletConsts.OI_PROP, status);
        ds.put(entity);
    }

    /**
     * @param key Key corresponding to an entity in Datastore assigned to the current session
     * @return String message indicating whether the OriginInfo base URL from the app
     * matches the website's base URL
     */
    public static String getOriginInfoStatus(Key key) {
        return (String) getEntity(key).getProperty(ServletConsts.OI_PROP);
    }
}