package com.google.sps.servlets;

import com.google.gson.Gson;
import java.util.Base64;
import java.util.OptionalInt;

// imports for CBOR encoding/decoding
import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

// imports for key generation
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.PublicKey;
import java.security.PrivateKey;

// Java servlet imports
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletInputStream;
import java.io.InputStream;
import java.io.OutputStream;

// imports for Datastore
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
        putByteArrInDatastore(ServletConsts.READER_ENGAGEMENT_PROP, readerEngagement);
        putByteArrInDatastore(ServletConsts.PUBLIC_KEY_PROP, keyPair.getPublic().getEncoded());
        putByteArrInDatastore(ServletConsts.PRIVATE_KEY_PROP, keyPair.getPrivate().getEncoded());

        String readerEngagementStr = Base64.getEncoder().withoutPadding().encodeToString(arr);
  
        return ServletConsts.MDOC_URI_PREFIX + base64Encode(readerEngagementStr);
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
            byte[] messageData = getBytesFromRequest(request);
            SessionEncryptionReader sessionEncryption = createSessionEncryptionReader(messageData);
            byte[] sessionData = generateDeviceRequest(sessionEncryption);
            setDeviceRequestBoolean(true);
            response.setContentType("text/html;");
            //response.getWriter().println(base64Encode(sessionData));
            
            response.getOutputStream().write(sessionData,0,sessionData.length);
        } else {
            byte[] messageData = getBytesFromRequest(request);
            String json = parseDeviceResponse(messageData);
            Entity entity = getEntity();
            entity.setProperty(ServletConsts.DEVICE_RESPONSE_PROP, new Text(json));
            datastore.put(entity);

            response.setContentType("application/json;");
            response.getWriter().println(json);
        }
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

    public byte[] getBytesFromRequest(HttpServletRequest request) {
        byte[] arr = new byte[request.getContentLength()];
        try {
            ServletInputStream stream = request.getInputStream();
            stream.read(arr);
        } catch (IOException e) {
            throw new IllegalStateException("Error reading request body", e);
        }
        return arr;
    }

    /**
     * Parses the MessageData CBOR message to isolate DeviceEngagement, and then uses it
     * to get the session transcript.
     * 
     * @param request POST request containing MessageData
     * @return SessionEncryptionReader, to be used to create DeviceRequest in the future.
     */
    public SessionEncryptionReader createSessionEncryptionReader(byte[] data) {
        byte[] encodedDeviceEngagement = Util.cborMapExtractByteString(Util.cborDecode(data), ServletConsts.DEVICE_ENGAGEMENT_KEY);
        putByteArrInDatastore("Device Engagement", encodedDeviceEngagement);

        // retrieve keys from datastore
        PublicKey eReaderKeyPublic = getPublicKeyFromString(getPropertyFromDatastore(ServletConsts.PUBLIC_KEY_PROP));
        PrivateKey eReaderKeyPrivate = getPrivateKeyFromString(getPropertyFromDatastore(ServletConsts.PRIVATE_KEY_PROP));

        // get eDeviceKey
        EngagementParser.Engagement deviceEngagement = new EngagementParser(encodedDeviceEngagement).parse();
        PublicKey eDeviceKey = deviceEngagement.getESenderKey();
        putByteArrInDatastore("Device Key", eDeviceKey.getEncoded());

        byte[] sessionTranscript = createSessionTranscript(encodedDeviceEngagement, eReaderKeyPublic);

        return new SessionEncryptionReader(eReaderKeyPrivate, eReaderKeyPublic, eDeviceKey, sessionTranscript);
    }

    public byte[] createSessionTranscript(byte[] encodedDeviceEngagement, PublicKey eReaderKeyPublic) {
        byte[] readerEngagementBytes = base64Decode(getPropertyFromDatastore(ServletConsts.READER_ENGAGEMENT_PROP));
        byte[] eReaderKeyBytes = Util.cborEncode(Util.cborBuildCoseKey(eReaderKeyPublic));

        byte[] sessionTranscript = Util.cborEncode(new CborBuilder()
            .addArray()
                .add(Util.cborBuildTaggedByteString(encodedDeviceEngagement))
                .add(Util.cborBuildTaggedByteString(eReaderKeyBytes))
                .add(Util.cborBuildTaggedByteString(readerEngagementBytes))
            .end()
            .build().get(0));

        putByteArrInDatastore(ServletConsts.SESSION_TRANS_PROP, sessionTranscript);

        return sessionTranscript;
    }

    /**
     * Generates Device Request using the session transcript extracted from the initial SessionEstablishment
     * message.
     * 
     * @param sessionEncryption SessionEncryptionReader object, to be used to create DeviceRequest
     * @return DeviceRequest, to be sent back as a response to the initial POST request
     */
    public byte[] generateDeviceRequest(SessionEncryptionReader reader) {
        byte[] sessionTranscript = base64Decode(getPropertyFromDatastore(ServletConsts.SESSION_TRANS_PROP));
        byte[] deviceRequest = new DeviceRequestGenerator().setSessionTranscript(sessionTranscript).generate();
        return reader.encryptMessageToDevice(deviceRequest, OptionalInt.empty());
    }

    /**
     * Parses DeviceResponse CBOR message and converts its contents into a JSON string.
     * 
     * @param request POST request containing DeviceResponse
     * @return JSON string containing Documents contained within DeviceResponse
     */
    public String parseDeviceResponse(byte[] data) {
        PublicKey eReaderKeyPublic = getPublicKeyFromString(getPropertyFromDatastore(ServletConsts.PUBLIC_KEY_PROP));
        PrivateKey eReaderKeyPrivate = getPrivateKeyFromString(getPropertyFromDatastore(ServletConsts.PRIVATE_KEY_PROP));
        PublicKey eDeviceKeyPublic = getPublicKeyFromString(getPropertyFromDatastore("Device Key"));
        byte[] sessionTranscript = base64Decode(getPropertyFromDatastore(ServletConsts.SESSION_TRANS_PROP));

        SessionEncryptionReader reader = new SessionEncryptionReader(eReaderKeyPrivate, eReaderKeyPublic, eDeviceKeyPublic, sessionTranscript);
        byte[] deviceResponseBytes = reader.decryptMessageFromDevice(data);
        
        DeviceResponseParser.DeviceResponse dr = new DeviceResponseParser()
            .setDeviceResponse(deviceResponseBytes)
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
     * @return PublicKey, converted from a String @param str
     */
    public PublicKey getPublicKeyFromString(String str) {
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(base64Decode(str));
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(ServletConsts.KEY_GENERATION_INSTANCE);
            return keyFactory.generatePublic(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Error generating public key", e);
        }
    }

    /**
     * @return PrivateKey, converted from a String @param str
     */
    public PrivateKey getPrivateKeyFromString(String str) {
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(base64Decode(str));
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(ServletConsts.KEY_GENERATION_INSTANCE);
            return keyFactory.generatePrivate(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Error generating public key", e);
        }
    }

    /**
     * Updates the entity in Datastore with new data.
     * 
     * @param propName Name of the property that should be inserted into Datastore
     * @param arr data that should be inserted into Datastore
     */
    public static void putByteArrInDatastore(String propName, byte[] arr) {
        Entity entity = getEntity();
        entity.setProperty(propName, base64Encode(arr));
        datastore.put(entity);
    }

    /**
     * Retrieves data from Datastore.
     * 
     * @param propName Name of the property that should be retrieved from Datastore
     * @return String data from Datastore corresponding to @param propName
     */
    public String getPropertyFromDatastore(String propertyName) {
        Entity entity = getEntity();
        return (String) entity.getProperty(propertyName);
    }

    /**
     * @return Byte array from a base 64 decoded String @param str
     */
    public static byte[] base64Decode(String str) {
        return Base64.getMimeDecoder().decode(str.getBytes());
    }

    /**
     * @return String from a base 64 encoded byte array @param arr
     */
    public static String base64Encode(byte[] arr) {
        return Base64.getEncoder().encodeToString(arr);
    }
}