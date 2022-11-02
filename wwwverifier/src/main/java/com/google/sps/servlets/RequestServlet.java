package com.google.sps.servlets;

import co.nstant.in.cbor.CborBuilder;
import com.google.gson.Gson;
import java.util.Base64;
import java.util.OptionalInt;
import java.util.Map;
import java.util.HashMap;

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
@WebServlet("/request-mdl")
public class RequestServlet extends HttpServlet {

    public static DatastoreService datastore;
    public static Key datastoreKey;

    /**
     * Initializes Datastore, and creates an entity within it that will store various
     * pieces of data (ephemeral keys, ReaderEngagement, etc.) in the future.
     */
    @Override
    public void init() {
        datastore = DatastoreServiceFactory.getDatastoreService();
        Entity entity = new Entity(ServletConsts.ENTITY_NAME);
        datastore.put(entity);
        datastoreKey = entity.getKey();
        setDeviceRequestBoolean(false);
    }

   /**
    * Handles HTTP GET requests, invoked from the front-end through the "Request mDL"
    * or "Get Device Response" buttons.
    *
    * @return String, containing either a generated mdoc:// URI or parsed DeviceResponse data
    */
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String requestType = request.getParameter(ServletConsts.GET_PARAM);
        response.setContentType("text/html;");
        if (requestType.equals(ServletConsts.GET_PARAM_URI)) {
            response.getWriter().println(createMdocUri());
        } else if (requestType.equals(ServletConsts.GET_PARAM_RESPONSE)) {
            response.getWriter().println(getDeviceResponse());
        } else {
            response.getWriter().println("Invalid GET request");
        }
    }

    /**
    * Generates ReaderEngagement CBOR message, and creates a URI from it.
    *
    * @return Generated mdoc:// URI
    */
    public String createMdocUri() {
        KeyPair keyPair = generateKeyPair();
        byte[] readerEngagement = generateReaderEngagement(keyPair.getPublic());
  
        // put ReaderEngagement and generated ephemeral keys into Datastore
        setDatastoreProp(ServletConsts.READER_ENGAGEMENT_PROP, readerEngagement);
        setDatastoreProp(ServletConsts.PUBLIC_KEY_PROP, keyPair.getPublic().getEncoded());
        setDatastoreProp(ServletConsts.PRIVATE_KEY_PROP, keyPair.getPrivate().getEncoded());

        String readerEngagementStr = Base64.getUrlEncoder().withoutPadding().encodeToString(readerEngagement);
        String readerEngagementStrEdited = readerEngagementStr.replace("\n","");
  
        return ServletConsts.MDOC_URI_PREFIX + readerEngagementStrEdited;
    }

    /**
     * Retrieves DeviceResponse message from Datastore.
     * 
     * @return String containing DeviceResponse message, or default message if it does not exist
     */
    public String getDeviceResponse() {
        Entity entity = getEntity();
        if (!entity.hasProperty(ServletConsts.DEVICE_RESPONSE_PROP)) {
            return ServletConsts.DEFAULT_RESPONSE_MSSG;
        } else {
            Text deviceResponse = (Text) entity.getProperty(ServletConsts.DEVICE_RESPONSE_PROP);
            return deviceResponse.getValue();
        }
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
        if (!getDeviceRequestBoolean()) {
            byte[] sessionData = createDeviceRequest(getBytesFromRequest(request));
            setDeviceRequestBoolean(true);
            response.getOutputStream().write(sessionData);
        } else {
            String json = parseDeviceResponse(getBytesFromRequest(request));
            Entity entity = getEntity();
            entity.setProperty(ServletConsts.DEVICE_RESPONSE_PROP, new Text(json));
            datastore.put(entity);

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
    public byte[] createDeviceRequest(byte[] messageData) {
        byte[] encodedDeviceEngagement = Util.cborMapExtractByteString(Util.cborDecode(messageData), ServletConsts.DEVICE_ENGAGEMENT_KEY);
        PublicKey eReaderKeyPublic = getDecodedPublicKey(getDatastoreProp(ServletConsts.PUBLIC_KEY_PROP));
        PrivateKey eReaderKeyPrivate = getDecodedPrivateKey(getDatastoreProp(ServletConsts.PRIVATE_KEY_PROP));
        byte[] readerEngagementBytes = getDatastoreProp(ServletConsts.READER_ENGAGEMENT_PROP);

        PublicKey eDeviceKeyPublic = new EngagementParser(encodedDeviceEngagement).parse().getESenderKey();
        setDatastoreProp(ServletConsts.DEVICE_KEY_PROP, eDeviceKeyPublic.getEncoded());

        byte[] sessionTranscript = Util.cborEncode(new CborBuilder()
            .addArray()
                .add(Util.cborBuildTaggedByteString(encodedDeviceEngagement))
                .add(Util.cborBuildTaggedByteString(Util.cborEncode(Util.cborBuildCoseKey(eReaderKeyPublic))))
                .add(Util.cborBuildTaggedByteString(readerEngagementBytes))
            .end().build().get(0));
        setDatastoreProp(ServletConsts.SESSION_TRANS_PROP, sessionTranscript);

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
    public Map<String, Map<String, Boolean>> createMdlItemsToRequest() {
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
    public static byte[] generateReaderEngagement(PublicKey publicKey) {
        EngagementGenerator eg = new EngagementGenerator(publicKey, EngagementGenerator.ENGAGEMENT_VERSION_1_1);
        eg.addConnectionMethod(new ConnectionMethodHttp(ServletConsts.WEBSITE_URL));
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
    public byte[] getBytesFromRequest(HttpServletRequest request) {
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
    public String parseDeviceResponse(byte[] messageData) {
        PublicKey eReaderKeyPublic = getDecodedPublicKey(getDatastoreProp(ServletConsts.PUBLIC_KEY_PROP));
        PrivateKey eReaderKeyPrivate = getDecodedPrivateKey(getDatastoreProp(ServletConsts.PRIVATE_KEY_PROP));
        PublicKey eDeviceKeyPublic = getDecodedPublicKey(getDatastoreProp(ServletConsts.DEVICE_KEY_PROP));
        byte[] sessionTranscript = getDatastoreProp(ServletConsts.SESSION_TRANS_PROP);

        SessionEncryptionReader ser = new SessionEncryptionReader(eReaderKeyPrivate, eReaderKeyPublic, eDeviceKeyPublic, sessionTranscript);
        
        DeviceResponseParser.DeviceResponse dr = new DeviceResponseParser()
            .setDeviceResponse(ser.decryptMessageFromDevice(messageData))
            .setSessionTranscript(sessionTranscript)
            .setEphemeralReaderKey(eReaderKeyPrivate)
            .parse();
        return new Gson().toJson(dr.getDocuments());
    }

    /**
     * @return Datastore entity linked to the key created in the init() function.
     */
    public static Entity getEntity() {
        try {
            return datastore.get(datastoreKey);
        } catch (EntityNotFoundException e) {
            throw new IllegalStateException("Entity could not be found in database", e);
        }
    }

    /**
     * @return Boolean reflecting whether or not DeviceRequest has already been sent as a response
     * to a POST request; this is used to identify how the doPost() function should process incoming
     * POST requests.
     */
    public boolean getDeviceRequestBoolean() {
        return (boolean) getEntity().getProperty(ServletConsts.BOOLEAN_PROP);
    }

    /**
     * Marks in database whether DeviceRequest has been sent as a response to a POST request
     * @param bool
     */
    public static void setDeviceRequestBoolean(boolean bool) {
        Entity entity = getEntity();
        entity.setProperty(ServletConsts.BOOLEAN_PROP, bool);
        datastore.put(entity);
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
    public static void setDatastoreProp(String propName, byte[] arr) {
        Entity entity = getEntity();
        entity.setProperty(propName, new Blob(arr));
        datastore.put(entity);
    }

    /**
     * Retrieves byte array data from Datastore
     * 
     * @param propName Name of the property that should be retrieved from Datastore
     * @return byte array that corresponds to {@ propName}
     */
    public static byte[] getDatastoreProp(String propName) {
        return ((Blob) getEntity().getProperty(propName)).getBytes();
    }
}