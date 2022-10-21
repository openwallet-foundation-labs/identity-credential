package com.google.sps.servlets;

import com.google.gson.Gson;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.DataItem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.PublicKey;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.List;
import java.util.OptionalInt;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

// imports for Datastore
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Text;

// imports for Identity Credential Library
import com.google.sps.servlets.DeviceRequestGenerator;
import com.google.sps.servlets.SessionEncryptionReader;
import com.google.sps.servlets.DeviceResponseParser;
import com.google.sps.servlets.EngagementGenerator;
import com.google.sps.servlets.ConnectionMethod;
import com.google.sps.servlets.ConnectionMethodRestApi;
import com.google.sps.servlets.OriginInfoWebsite;
import com.google.sps.servlets.OriginInfo;

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
     * Generates ReaderEngagement CBOR message, and creates a URI from it.
     * 
     * @return Generated mdoc:// URI
     */
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        KeyPair keyPair = generateKeyPair();
        PublicKey publicKey = keyPair.getPublic();
        PrivateKey privateKey = keyPair.getPrivate();

        byte[] readerEngagement = generateReaderEngagement(publicKey);

        String fullURI = ServletConsts.MDOC_URI_PREFIX + base64Encode(readerEngagement);

        // put ReaderEngagement and generated ephemeral keys into Datastore
        putByteArrInDatastore(ServletConsts.READER_ENGAGEMENT_PROP, readerEngagement);
        putByteArrInDatastore(ServletConsts.PUBLIC_KEY_PROP, publicKey.getEncoded());
        putByteArrInDatastore(ServletConsts.PRIVATE_KEY_PROP, privateKey.getEncoded());

        response.setContentType("text/html;");
        response.getWriter().println(fullURI);
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
            SessionEncryptionReader sessionEncryption = parseSessionEstablishment(request);
            byte[] sessionData = generateDeviceRequest(sessionEncryption);
            setDeviceRequestBoolean(true);
            response.setContentType("text/html;");
            response.getWriter().println(base64Encode(sessionData));
        } else {
            String json = parseDeviceResponse(request);
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
        EngagementGenerator generator = new EngagementGenerator(publicKey, EngagementGenerator.ENGAGEMENT_VERSION_1_1);
        generator.addConnectionMethod(new ConnectionMethodRestApi(ServletConsts.WEBSITE_URL_SERVLET));
        generator.addOriginInfo(new OriginInfoWebsite(OriginInfo.CAT_DELIVERY, ServletConsts.WEBSITE_URL));
        return generator.generate();
    }

    /**
     * @return generated ephemeral reader key pair (containing a PublicKey and a PrivateKey)
     */
    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(ServletConsts.KEY_GENERATION_INSTANCE);
            ECGenParameterSpec ecSpec = new ECGenParameterSpec(ServletConsts.KEY_GENERATION_CURVE);
            keyPairGenerator.initialize(ecSpec);
            return keyPairGenerator.generateKeyPair();
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            throw new IllegalStateException("Error generating ephemeral key-pair", e);
        }
    }

    /**
     * Parses the Session Establishment CBOR message to isolate DeviceEngagement, and then uses it
     * to get the session transcript.
     * 
     * @param request POST request containing SessionEstablishment
     * @return SessionEncryptionReader, to be used to create DeviceRequest in the future.
     */
    public SessionEncryptionReader parseSessionEstablishment(HttpServletRequest request) {
        // parse Session Establishment CBOR message
        byte[] sessionEstablishmentBytes = base64Decode(request.getParameter(ServletConsts.SESSION_ESTABLISHMENT_PARAM));
        DataItem deviceEngagementItem = CBORDecode(sessionEstablishmentBytes).get(ServletConsts.DEVICE_ENGAGEMENT_INDEX);
        byte[] deviceEngagement = ((ByteString) deviceEngagementItem).getBytes();

        // retrieve items from Datastore
        byte[] readerEngagement = CBOREncode(getPropertyFromDatastore(ServletConsts.READER_ENGAGEMENT_PROP));
        PublicKey publicKey = getPublicKeyFromString(getPropertyFromDatastore(ServletConsts.PUBLIC_KEY_PROP));
        PrivateKey privateKey = getPrivateKeyFromString(getPropertyFromDatastore(ServletConsts.PRIVATE_KEY_PROP));
    
        SessionEncryptionReader sessionEncryption = new SessionEncryptionReader(
            privateKey, publicKey, deviceEngagement, readerEngagement);

        // put sessionTranscript in datastore
        putByteArrInDatastore(ServletConsts.SESSION_TRANS_PROP, sessionEncryption.getSessionTranscript());

        return sessionEncryption;
    }

    /**
     * Generates Device Request using the session transcript extracted from the initial SessionEstablishment
     * message.
     * 
     * @param sessionEncryption SessionEncryptionReader object, to be used to create DeviceRequest
     * @return DeviceRequest, to be sent back as a response to the initial POST request
     */
    public byte[] generateDeviceRequest(SessionEncryptionReader sessionEncryption) {
        byte[] sessionTranscript = base64Decode(getPropertyFromDatastore(ServletConsts.SESSION_TRANS_PROP));
        byte[] deviceRequest = new DeviceRequestGenerator().setSessionTranscript(sessionTranscript).generate();
        return sessionEncryption.encryptMessageToDevice(deviceRequest, OptionalInt.empty());
    }

    /**
     * Parses DeviceResponse CBOR message and converts its contents into a JSON string.
     * 
     * @param request POST request containing DeviceResponse
     * @return JSON string containing Documents contained within DeviceResponse
     */
    public String parseDeviceResponse(HttpServletRequest request) {
        byte[] deviceResponse = base64Decode(request.getParameter(ServletConsts.DEVICE_RESPONSE_PARAM));

        // retrieve items from Datastore
        PrivateKey privateKey = getPrivateKeyFromString(getPropertyFromDatastore(ServletConsts.PRIVATE_KEY_PROP));
        byte[] sessionTranscript = base64Decode(getPropertyFromDatastore(ServletConsts.SESSION_TRANS_PROP));
        
        DeviceResponseParser.DeviceResponse dr = new DeviceResponseParser()
            .setDeviceResponse(deviceResponse)
            .setSessionTranscript(sessionTranscript)
            .setEphemeralReaderKey(privateKey)
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
     * @return a CBOR encoding of @param str
     */
    public byte[] CBOREncode(String str) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            new CborEncoder(baos).encode(new CborBuilder().add(str).build());
            return baos.toByteArray();
        } catch (CborException e) {
            throw new IllegalStateException("Error with CBOR encoding", e);
        }
    }

    /**
     * @return a List of DataItem objects extracted by decoding the previously CBOR encoded @param arr
     */
    public List<DataItem> CBORDecode(byte[] arr) {
        ByteArrayInputStream bais = new ByteArrayInputStream(arr);
        try {
            return new CborDecoder(bais).decode();
        } catch (CborException e) {
            throw new IllegalStateException("Error with CBOR decoding", e);
        }
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