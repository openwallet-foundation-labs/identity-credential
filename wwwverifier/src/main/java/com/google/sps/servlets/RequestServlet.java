package com.google.sps.servlets;

import co.nstant.in.cbor.CborBuilder;
import com.google.gson.Gson;
import java.util.Base64;
import java.util.OptionalInt;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

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
import javax.servlet.ServletInputStream;

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

    public static DatastoreService datastore;

    @Override
    public void init() {
        datastore = DatastoreServiceFactory.getDatastoreService();
    }

   /**
    * Handles HTTP GET requests, invoked from the front-end through the "Request mDL"
    * or "Get Device Response" buttons.
    *
    * @return String, containing either a generated mdoc:// URI or parsed DeviceResponse data
    */
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("text/html;");
        String pathInfo = request.getPathInfo().substring(1);
        String[] pathArr = pathInfo.split("/");
        if (pathInfo.equals(ServletConsts.NEW_SESSION_URL)) {
            response.getWriter().println(createNewSession());
        } else if (pathArr[0].equals(ServletConsts.RESPONSE_URL)) {
            Key datastoreKey = com.google.appengine.api.datastore.KeyFactory.stringToKey(pathArr[1]);
            response.getWriter().println(getDeviceResponse(datastoreKey));
        }
    }

    public static String createNewSession() {
        Entity entity = new Entity(ServletConsts.ENTITY_NAME);
        entity.setProperty(ServletConsts.TIMESTAMP_PROP,
            new java.sql.Timestamp(System.currentTimeMillis()).toString());
        datastore.put(entity);
        Key datastoreKey = entity.getKey();
        setNumPostRequests(0, datastoreKey);
        String datastoreKeyStr = com.google.appengine.api.datastore.KeyFactory.keyToString(datastoreKey);
        return createMdocUri(datastoreKey) + "," + datastoreKeyStr;
    }

    /**
    * Generates ReaderEngagement CBOR message, and creates a URI from it.
    *
    * @return Generated mdoc:// URI
    */
    public static String createMdocUri(Key datastoreKey) {
        KeyPair keyPair = generateKeyPair();
        byte[] readerEngagement = generateReaderEngagement(keyPair.getPublic(), datastoreKey);
  
        // put ReaderEngagement and generated ephemeral keys into Datastore
        setDatastoreProp(ServletConsts.READER_ENGAGEMENT_PROP, readerEngagement, datastoreKey);
        setDatastoreProp(ServletConsts.PUBLIC_KEY_PROP, keyPair.getPublic().getEncoded(), datastoreKey);
        setDatastoreProp(ServletConsts.PRIVATE_KEY_PROP, keyPair.getPrivate().getEncoded(), datastoreKey);

        String readerEngagementStr = Base64.getUrlEncoder().withoutPadding().encodeToString(readerEngagement);
        String readerEngagementStrEdited = readerEngagementStr.replace("\n","");
  
        return ServletConsts.MDOC_URI_PREFIX + readerEngagementStrEdited;
    }

    /**
     * Retrieves DeviceResponse message from Datastore.
     * 
     * @return String containing DeviceResponse message, or default message if it does not exist
     */
    public static String getDeviceResponse(Key datastoreKey) {
        Entity entity = getEntity(datastoreKey);
        if (entity.hasProperty(ServletConsts.DEVICE_RESPONSE_PROP)) {
            Text deviceResponse = (Text) entity.getProperty(ServletConsts.DEVICE_RESPONSE_PROP);
            return deviceResponse.getValue();
        }
        return "";
    } 

    /**
     * Handles two distinct POST requests:
     * (1) The first POST request sent to the servlet, which contains a SessionEstablishment message
     * (2) The second POST request sent to the servlet, which contains a DeviceResponse message
     * 
     * @param request Either (1) SessionEstablishment or (2) DeviceResponse, as base 64 encoded 
     * (and CBOR encoded) messages
     * @return (1) response, containing a DeviceRequest message; 
     * (2) response, containing parsed data from DeviceResponse as a JSON String
     */
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String pathInfo = request.getPathInfo().substring(1);
        Key datastoreKey = com.google.appengine.api.datastore.KeyFactory.stringToKey(pathInfo);
        if (getNumPostRequests(datastoreKey) == 0) {
            byte[] sessionData = createDeviceRequest(getBytesFromRequest(request), datastoreKey);
            setNumPostRequests(1, datastoreKey);
            response.getOutputStream().write(sessionData);
        } else if (getNumPostRequests(datastoreKey) == 1) {
            String json = parseDeviceResponse(getBytesFromRequest(request), datastoreKey);
            Entity entity = getEntity(datastoreKey);
            entity.setProperty(ServletConsts.DEVICE_RESPONSE_PROP, new Text(json));
            datastore.put(entity);

            setNumPostRequests(2, datastoreKey);

            response.setContentType("application/json;");
            response.getWriter().println(json);
        }
    }

     /**
      * Parses the MessageData CBOR message to isolate DeviceEngagement; DeviceEngagement is then used
      * to create a DeviceRequest.
      *
      * @param messageData CBOR message extracted from POST request
      * @return SessionData CBOR message containing DeviceRequest
      */
    public static byte[] createDeviceRequest(byte[] messageData, Key datastoreKey) {
        byte[] encodedDeviceEngagement = Util.cborMapExtractByteString(Util.cborDecode(messageData), ServletConsts.DEVICE_ENGAGEMENT_KEY);
        PublicKey eReaderKeyPublic = getDecodedPublicKey(getDatastoreProp(ServletConsts.PUBLIC_KEY_PROP, datastoreKey));
        PrivateKey eReaderKeyPrivate = getDecodedPrivateKey(getDatastoreProp(ServletConsts.PRIVATE_KEY_PROP, datastoreKey));
        byte[] readerEngagementBytes = getDatastoreProp(ServletConsts.READER_ENGAGEMENT_PROP, datastoreKey);

        PublicKey eDeviceKeyPublic = new EngagementParser(encodedDeviceEngagement).parse().getESenderKey();
        setDatastoreProp(ServletConsts.DEVICE_KEY_PROP, eDeviceKeyPublic.getEncoded(), datastoreKey);

        byte[] sessionTranscript = Util.cborEncode(new CborBuilder()
            .addArray()
                .add(Util.cborBuildTaggedByteString(encodedDeviceEngagement))
                .add(Util.cborBuildTaggedByteString(Util.cborEncode(Util.cborBuildCoseKey(eReaderKeyPublic))))
                .add(Util.cborBuildTaggedByteString(readerEngagementBytes))
            .end().build().get(0));
        setDatastoreProp(ServletConsts.SESSION_TRANS_PROP, sessionTranscript, datastoreKey);

        SessionEncryptionReader ser = new SessionEncryptionReader(eReaderKeyPrivate, eReaderKeyPublic, eDeviceKeyPublic, sessionTranscript);
        byte[] dr = new DeviceRequestGenerator()
            .setSessionTranscript(sessionTranscript)
            .addDocumentRequest(ServletConsts.MDL_DOCTYPE, createMdlItemsToRequest(), null, null, null)
            .generate();
        return ser.encryptMessageToDevice(dr, OptionalInt.empty());
    }

    /**
     * @return Map of items to request from the mDL app
     */
    public static Map<String, Map<String, Boolean>> createMdlItemsToRequest() {
        Map<String, Map<String, Boolean>> mdlItemsToRequest = new HashMap<>();
        Map<String, Boolean> mdlNsItems = new HashMap<>();
        mdlNsItems.put("family_name", true);
        mdlNsItems.put("portrait", false);
        mdlItemsToRequest.put(ServletConsts.MDL_NAMESPACE, mdlNsItems);
        Map<String, Boolean> aamvaNsItems = new HashMap<>();
        aamvaNsItems.put("real_id", false);
        mdlItemsToRequest.put(ServletConsts.AAMVA_NAMESPACE, aamvaNsItems);
        return mdlItemsToRequest;
    }

    /**
     * @return generated readerEngagement CBOR message, using EngagementGenerator
     */
    public static byte[] generateReaderEngagement(PublicKey publicKey, Key datastoreKey) {
        EngagementGenerator eg = new EngagementGenerator(publicKey, EngagementGenerator.ENGAGEMENT_VERSION_1_1);
        String datastoreKeyStr = com.google.appengine.api.datastore.KeyFactory.keyToString(datastoreKey);
        eg.addConnectionMethod(new ConnectionMethodHttp(ServletConsts.WEBSITE_URL + "/" + datastoreKeyStr));
        return eg.generate();
    }

    /**
     * @return generated ephemeral reader key pair (containing a PublicKey and a PrivateKey)
     */
    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(ServletConsts.KEY_GENERATION_INSTANCE);
            ECGenParameterSpec ecSpec = new ECGenParameterSpec(ServletConsts.KEY_GENERATION_CURVE);
            kpg.initialize(ecSpec);
            return kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            throw new IllegalStateException("Error generating ephemeral key-pair", e);
        }
    }

    /**
     * 
     * @param request HTTP POST request
     * @return byte array with data extracted from {@ request}
     */
    public static byte[] getBytesFromRequest(HttpServletRequest request) {
        int length = request.getContentLength();
        byte[] arr = new byte[length];
        try {
            ServletInputStream stream = request.getInputStream();
            stream.read(arr,0,length);
        } catch (IOException e) {
            throw new IllegalStateException("Error reading request body", e);
        }
        return arr;
    }

    /**
     * Parses DeviceResponse CBOR message and converts its contents into a JSON string.
     * 
     * @param request POST request containing DeviceResponse
     * @return JSON string containing Documents contained within DeviceResponse
     */
    public static String parseDeviceResponse(byte[] messageData, Key datastoreKey) {
        PublicKey eReaderKeyPublic = getDecodedPublicKey(getDatastoreProp(ServletConsts.PUBLIC_KEY_PROP, datastoreKey));
        PrivateKey eReaderKeyPrivate = getDecodedPrivateKey(getDatastoreProp(ServletConsts.PRIVATE_KEY_PROP, datastoreKey));
        PublicKey eDeviceKeyPublic = getDecodedPublicKey(getDatastoreProp(ServletConsts.DEVICE_KEY_PROP, datastoreKey));
        byte[] sessionTranscript = getDatastoreProp(ServletConsts.SESSION_TRANS_PROP, datastoreKey);

        SessionEncryptionReader ser = new SessionEncryptionReader(eReaderKeyPrivate, eReaderKeyPublic, eDeviceKeyPublic, sessionTranscript);
        
        DeviceResponseParser.DeviceResponse dr = new DeviceResponseParser()
            .setDeviceResponse(ser.decryptMessageFromDevice(messageData))
            .setSessionTranscript(sessionTranscript)
            .setEphemeralReaderKey(eReaderKeyPrivate)
            .parse();
        return new Gson().toJson(buildJson(dr.getDocuments()));
    }

    public static ArrayList<String> buildJson(List<DeviceResponseParser.Document> docs) {
        ArrayList<String> arr = new ArrayList<String>();
        arr.add("Number of documents returned: " + docs.size());
        for (DeviceResponseParser.Document doc : docs) {
            arr.add("Doctype: " + doc.getDocType());
            if (doc.getIssuerSignedAuthenticated()) {
                arr.add("Issuer Signed Authenticated");
            }
            if (doc.getDeviceSignedAuthenticatedViaSignature()) {
                arr.add("Device Signed Authenticated (ECDSA");
            } else if (doc.getDeviceSignedAuthenticated()) {
                arr.add("Device Signed Authenticated");
            }
            arr.add("MSO");
            arr.add("Signed: " + doc.getValidityInfoSigned().toString());
            arr.add("Valid From: " + doc.getValidityInfoValidFrom().toString());
            arr.add("Valid Until: " + doc.getValidityInfoValidUntil().toString());
            arr.add("DeviceKey: " + Base64.getEncoder().encodeToString(doc.getDeviceKey().getEncoded()));
            List<String> issuerNamespaces = doc.getIssuerNamespaces();
            for (String namespace : issuerNamespaces) {
                arr.add("Namespace: " + namespace);
                List<String> entryNames = doc.getIssuerEntryNames(namespace);
                for (String name : entryNames) {
                    String nameVal = "";
                    switch (name) {
                        case "portrait":
                            nameVal = Base64.getEncoder().encodeToString(doc.getIssuerEntryByteString(namespace, name));
                            break;
                        case "family_name":
                            nameVal = doc.getIssuerEntryString(namespace, name);
                            break;
                        case "issue_date":
                            nameVal = doc.getIssuerEntryString(namespace, name);
                            break;
                        case "expiry_date":
                            nameVal = doc.getIssuerEntryString(namespace, name);
                            break;
                        case "document_number":
                            nameVal = doc.getIssuerEntryString(namespace, name);
                            break;
                        default:
                            nameVal = Base64.getEncoder().encodeToString(doc.getIssuerEntryData(namespace, name));
                    }
                    arr.add(name + ": " + nameVal);
                }
            }
        }
        return arr;
    }

    /**
     * @return Datastore entity linked to the key created in the init() function.
     */
    public static Entity getEntity(Key datastoreKey) {
        try {
            return datastore.get(datastoreKey);
        } catch (EntityNotFoundException e) {
            throw new IllegalStateException("Entity could not be found in database", e);
        }
    }

    /**
     * @return PublicKey, converted from a byte array @param arr
     */
    public static PublicKey getDecodedPublicKey(byte[] arr) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(ServletConsts.KEY_GENERATION_INSTANCE);
            return keyFactory.generatePublic(new X509EncodedKeySpec(arr));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Error generating public key", e);
        }
    }

    /**
     * @return PrivateKey, converted from a byte array @param arr
     */
    public static PrivateKey getDecodedPrivateKey(byte[] arr) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(ServletConsts.KEY_GENERATION_INSTANCE);
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(arr));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Error generating public key", e);
        }
    }

    /**
     * Updates the entity in Datastore with new data in the form of a byte array
     * 
     * @param propName Name of the property that should be inserted into Datastore
     * @param arr data that should be inserted into Datastore
     */
    public static void setDatastoreProp(String propName, byte[] arr, Key datastoreKey) {
        Entity entity = getEntity(datastoreKey);
        entity.setProperty(propName, new Blob(arr));
        datastore.put(entity);
    }

    /**
     * Retrieves byte array data from Datastore
     * 
     * @param propName Name of the property that should be retrieved from Datastore
     * @return byte array that corresponds to {@ propName}
     */
    public static byte[] getDatastoreProp(String propName, Key datastoreKey) {
        return ((Blob) getEntity(datastoreKey).getProperty(propName)).getBytes();
    }

    public static void setNumPostRequests(int num, Key datastoreKey) {
        Entity entity = getEntity(datastoreKey);
        entity.setProperty(ServletConsts.NUM_REQUESTS_PROP, num);
        datastore.put(entity);
    }

    public static int getNumPostRequests(Key datastoreKey) {
        return ((Long) getEntity(datastoreKey).getProperty(ServletConsts.NUM_REQUESTS_PROP)).intValue();
    }
}