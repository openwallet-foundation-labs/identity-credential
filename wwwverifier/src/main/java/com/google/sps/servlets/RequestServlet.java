package com.android.identity.wwwreader;

import co.nstant.in.cbor.CborBuilder;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.OptionalInt;
import java.util.List;
import java.util.Map;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

// imports for key generation
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.security.PublicKey;
import java.security.PrivateKey;

// Java servlet imports
import java.io.IOException;
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

   /**
    * Handles two types of HTTP GET requests:
    * (1) A request to create a new session, which involves creating a new entity in Datastore
    * (2) A request to retrieve information from DeviceResponse from an existing session
    *
    * @return String, containing either (1) a generated mdoc:// URI and unique Datastore key
    * or (2) parsed DeviceResponse data
    */
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("text/html;");
        String[] pathArr = parsePathInfo(request);
        if (pathArr[0].equals(ServletConsts.SESSION_URL)) {
            response.getWriter().println(createNewSession());
        } else if (pathArr[0].equals(ServletConsts.RESPONSE_URL) && pathArr.length == 2) {
            Key key = com.google.appengine.api.datastore.KeyFactory.stringToKey(pathArr[1]);
            response.getWriter().println(getDeviceResponse(key));
        }
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
     * @return String containing the generated mdoc URL and the unique key tied to the
     * new session's entry in Datastore, separated with a comma
     */
    public static String createNewSession() {
        Entity entity = new Entity(ServletConsts.ENTITY_TYPE);
        entity.setProperty(ServletConsts.TIMESTAMP_PROP,
            new java.sql.Timestamp(System.currentTimeMillis()).toString());
        ds.put(entity);
        Key key = entity.getKey();
        String keyStr = com.google.appengine.api.datastore.KeyFactory.keyToString(key);
        setNumPostRequests(0, key);
        return createMdocUri(key) + ServletConsts.SESSION_SEPARATOR + keyStr;
    }

    /**
    * Generates ReaderEngagement CBOR message, and creates a URI from it.
    *
    * @param key Unique key tied to an existing entity in Datastore
    * @return Generated mdoc:// URI
    */
    private static String createMdocUri(Key key) {
        KeyPair keyPair = generateKeyPair();
        byte[] re = generateReaderEngagement(keyPair.getPublic(), key);
  
        // put ReaderEngagement and generated ephemeral keys into Datastore
        setDatastoreProp(ServletConsts.RE_PROP, re, key);
        setDatastoreProp(ServletConsts.PUBKEY_PROP, keyPair.getPublic().getEncoded(), key);
        setDatastoreProp(ServletConsts.PRIVKEY_PROP, keyPair.getPrivate().getEncoded(), key);

        String reStr = Base64.getUrlEncoder().withoutPadding().encodeToString(re).replace("\n","");
  
        return ServletConsts.MDOC_PREFIX + reStr;
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
            byte[] sessionData = createDeviceRequest(getBytesFromRequest(request), key);
            setNumPostRequests(1, key);
            response.getOutputStream().write(sessionData);
        } else if (getNumPostRequests(key) == 1) {
            byte[] terminationMessage = parseDeviceResponse(getBytesFromRequest(request), key);
            setNumPostRequests(2, key);
            response.getOutputStream().write(terminationMessage);
        } else {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        }
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
        byte[] encodedEngagement = Util.cborMapExtractByteString(Util.cborDecode(messageData),
            ServletConsts.DE_KEY);
        PublicKey eReaderKeyPublic = getPublicKey(getDatastoreProp(ServletConsts.PUBKEY_PROP, key));
        PrivateKey eReaderKeyPrivate = getPrivateKey(getDatastoreProp(ServletConsts.PRIVKEY_PROP, key));

        EngagementParser.Engagement engagement = new EngagementParser(encodedEngagement).parse();
        PublicKey eDeviceKeyPublic = engagement.getESenderKey();
        setDatastoreProp(ServletConsts.DEVKEY_PROP, eDeviceKeyPublic.getEncoded(), key);
        verifyOriginInfo(engagement.getOriginInfos(), key);

        byte[] sessionTranscript = buildSessionTranscript(encodedEngagement, eReaderKeyPublic, key);
        setDatastoreProp(ServletConsts.TRANSCRIPT_PROP, sessionTranscript, key);

        SessionEncryptionReader ser = new SessionEncryptionReader(eReaderKeyPrivate,
            eReaderKeyPublic, eDeviceKeyPublic, sessionTranscript);
        ser.setSendSessionEstablishment(false);
        byte[] dr = new DeviceRequestGenerator()
            .setSessionTranscript(sessionTranscript)
            .addDocumentRequest(ServletConsts.MDL_DOCTYPE, createMdlItemsToRequest(), null, null, null)
            .generate();
        return ser.encryptMessageToDevice(dr, OptionalInt.empty());
    }

    /**
     * Generates session transcript using the device engagement @param de , the
     * reader key @param eReaderKeyPublic , and the reader engagement, which is accessed via
     * the datastore key @param key .
     */
    public static byte[] buildSessionTranscript(byte[] de, PublicKey eReaderKeyPublic, Key key) {
        byte[] re = getDatastoreProp(ServletConsts.RE_PROP, key);
        return Util.cborEncode(new CborBuilder()
            .addArray()
                .add(Util.cborBuildTaggedByteString(de))
                .add(Util.cborBuildTaggedByteString(Util.cborEncode(Util.cborBuildCoseKey(eReaderKeyPublic))))
                .add(Util.cborBuildTaggedByteString(re))
            .end().build().get(0));
    }

    /**
     * Verify that the base URL of the origin info found in @param oiList matches
     * the base URL of the website (ServletConsts.BASE_URL).
     * @param key Unique identifier corresponding to the current session
     */
    private static void verifyOriginInfo(List<OriginInfo> oiList, Key key) {
        if (oiList.size() > 0) {
            String oiUrl = ((OriginInfoWebsite) oiList.get(0)).getBaseUrl();
            if (!oiUrl.equals(ServletConsts.BASE_URL)) {
                setOriginInfoStatus(ServletConsts.OI_FAILURE_START +
                    oiUrl + ServletConsts.OI_FAILURE_END, key);
            } else {
                setOriginInfoStatus(ServletConsts.OI_SUCCESS, key);
            }
        } else {
            setOriginInfoStatus(ServletConsts.OI_FAILURE_START +
                ServletConsts.OI_FAILURE_END.trim(), key);
        }
    }

    /**
     * @return Map of items to request from the mDL app
     */
    private static Map<String, Map<String, Boolean>> createMdlItemsToRequest() {
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
        mdlItemsToRequest.put(ServletConsts.MDL_NAMESPACE, mdlNsItems);
        Map<String, Boolean> aamvaNsItems = new HashMap<>();
        aamvaNsItems.put("DHS_compliance", false);
        aamvaNsItems.put("EDL_credential", false);
        mdlItemsToRequest.put(ServletConsts.AAMVA_NAMESPACE, aamvaNsItems);
        return mdlItemsToRequest;
    }

    /**
     * @return generated readerEngagement CBOR message, using EngagementGenerator
     */
    public static byte[] generateReaderEngagement(PublicKey publicKey, Key key) {
        EngagementGenerator eg = new EngagementGenerator(publicKey,
            EngagementGenerator.ENGAGEMENT_VERSION_1_1);
        eg.addConnectionMethod(new ConnectionMethodHttp(ServletConsts.ABSOLUTE_URL + "/" 
            + com.google.appengine.api.datastore.KeyFactory.keyToString(key)));
        return eg.generate();
    }

    /**
     * @return generated ephemeral reader key pair (containing a PublicKey and a PrivateKey)
     */
    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(ServletConsts.KEYGEN_INSTANCE);
            ECGenParameterSpec ecSpec = new ECGenParameterSpec(ServletConsts.KEYGEN_CURVE);
            kpg.initialize(ecSpec);
            return kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            throw new IllegalStateException("Error generating ephemeral key-pair", e);
        }
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
    private static byte[] parseDeviceResponse(byte[] messageData, Key key) {
        PublicKey eReaderKeyPublic =
            getPublicKey(getDatastoreProp(ServletConsts.PUBKEY_PROP, key));
        PrivateKey eReaderKeyPrivate =
            getPrivateKey(getDatastoreProp(ServletConsts.PRIVKEY_PROP, key));
        PublicKey eDeviceKeyPublic =
            getPublicKey(getDatastoreProp(ServletConsts.DEVKEY_PROP, key));
        byte[] sessionTranscript = getDatastoreProp(ServletConsts.TRANSCRIPT_PROP, key);

        SessionEncryptionReader ser = new SessionEncryptionReader(eReaderKeyPrivate,
            eReaderKeyPublic, eDeviceKeyPublic, sessionTranscript);
        ser.setSendSessionEstablishment(false);
        
        DeviceResponseParser.DeviceResponse dr = new DeviceResponseParser()
            .setDeviceResponse(ser.decryptMessageFromDevice(messageData))
            .setSessionTranscript(sessionTranscript)
            .setEphemeralReaderKey(eReaderKeyPrivate)
            .parse();
        String json = new Gson().toJson(buildArrayFromDocuments(dr.getDocuments(), key));
        setDeviceResponse(json, key);

        return ser.encryptMessageToDevice(null, OptionalInt.of(20));
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
        ArrayList<String> arr = new ArrayList<String>();
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
            arr.add(ServletConsts.CHECKMARK + "DeviceKey: " +
                numBytesAsString(doc.getDeviceKey().getEncoded()));
            List<String> issuerNamespaces = doc.getIssuerNamespaces();
            for (String namespace : issuerNamespaces) {
                arr.add(ServletConsts.BOLD + "Namespace: " + ServletConsts.BOLD + namespace);
                List<String> entryNames = doc.getIssuerEntryNames(namespace);
                for (String entry : entryNames) {
                    String val = "";
                    switch (entry) {
                        case "portrait":
                            val = numBytesAsString(doc.getIssuerEntryByteString(namespace, entry));
                            break;
                        case "family_name":
                        case "given_name":
                        case "issuing_authority":
                        case "issue_date":
                        case "expiry_date":
                        case "DHS_compliance":
                        case "EDL_credential":
                        case "document_number":
                            val = doc.getIssuerEntryString(namespace, entry);
                            break;
                        case "sex":
                            val = Long.toString(doc.getIssuerEntryNumber(namespace, entry));
                            break;
                        default:
                            val = Base64.getEncoder().encodeToString(
                                doc.getIssuerEntryData(namespace, entry));
                    }
                    arr.add(ServletConsts.CHECKMARK + entry + ": " + val);
                }
            }
        }
        return arr;
    }

    /**
     * @return A String in the form of "(# bytes)", representing the number of bytes
     * in @param arr
     */
    private static String numBytesAsString(byte[] arr) {
        return "(" + Integer.toString(arr.length) + " bytes)";
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
    private static String timestampToString(com.android.identity.wwwreader.Timestamp ts) {
        DateFormat df = new SimpleDateFormat("MM/dd/yyyy");
        return df.format(new Date(ts.toEpochMilli()));
    }

    /**
     * @return PublicKey, converted from a byte array @param arr
     */
    private static PublicKey getPublicKey(byte[] arr) {
        try {
            KeyFactory kf = KeyFactory.getInstance(ServletConsts.KEYGEN_INSTANCE);
            return kf.generatePublic(new X509EncodedKeySpec(arr));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Unexpected error", e);
        }
    }

    /**
     * @return PrivateKey, converted from a byte array @param arr
     */
    private static PrivateKey getPrivateKey(byte[] arr) {
        try {
            KeyFactory kf = KeyFactory.getInstance(ServletConsts.KEYGEN_INSTANCE);
            return kf.generatePrivate(new PKCS8EncodedKeySpec(arr));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Unexpected error", e);
        }
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
        return new String();
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