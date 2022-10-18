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
import java.security.PublicKey;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.List;
import java.util.ArrayList;
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
import com.android.identity.DeviceRequestGenerator;
import com.android.identity.SessionEncryptionReader;
import com.android.identity.DeviceResponseParser;

@WebServlet("/request-mdl")
public class RequestServlet extends HttpServlet {

    private DatastoreService datastore;
    public static Key datastoreKey;

    @Override
    public void init() {
        datastore = DatastoreServiceFactory.getDatastoreService();
        Entity entity = new Entity(ServletConsts.ENTITY_NAME);
        datastore.put(entity);
        datastoreKey = entity.getKey();
    }
    
    /** doGet is triggered by the user clicking the "Request mDL" button 
     *  This function generates a mdoc:// URI
    */
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ReaderEngagementGenerator generator = new ReaderEngagementGenerator();
        byte[] readerEngagement = new ReaderEngagementGenerator().generate();
        String fullURI = ServletConsts.MDOC_URI_PREFIX + base64Encode(readerEngagement);

        // put everything in Datastore
        putByteArrInDatastore(ServletConsts.READER_ENGAGEMENT_PROP, readerEngagement);
        putByteArrInDatastore(ServletConsts.PUBLIC_KEY_PROP, generator.getPublicKey().getEncoded());
        putByteArrInDatastore(ServletConsts.PRIVATE_KEY_PROP, generator.getPrivateKey().getEncoded());
        setDeviceRequestBoolean(false);

        response.setContentType("text/html;");
        response.getWriter().println(fullURI);
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!getDeviceRequestBoolean()) {
            byte[] sessionData = parseSessionEstablishmentAndGenerateDeviceRequest(request);
            response.setContentType("text/html;");
            response.getWriter().println(base64Encode(sessionData));
        } else {
            String json = parseDeviceResponse(request);

            // put device response in datastore
            Entity entity = getEntity();
            entity.setProperty(ServletConsts.DEVICE_RESPONSE_PROP, new Text(json));
            datastore.put(entity);

            // send response back
            response.setContentType("application/json;");
            response.getWriter().println(json);
        }
    }

    public byte[] parseSessionEstablishmentAndGenerateDeviceRequest(HttpServletRequest request) {
        // decode Session Establishment CBOR message
        byte[] sessionEstablishmentBytes = base64Decode(request.getParameter(ServletConsts.SESSION_ESTABLISHMENT_PARAM));
        DataItem deviceEngagementItem = CBORDecode(sessionEstablishmentBytes).get(ServletConsts.DEVICE_ENGAGEMENT_INDEX);
        byte[] deviceEngagement = ((ByteString) deviceEngagementItem).getBytes();

        // retrieve items from Datastore
        byte[] readerEngagement = CBOREncode(getPropertyFromDatastore(ServletConsts.READER_ENGAGEMENT_PROP));
        PublicKey publicKey = getPublicKeyFromString(getPropertyFromDatastore(ServletConsts.PUBLIC_KEY_PROP));
        PrivateKey privateKey = getPrivateKeyFromString(getPropertyFromDatastore(ServletConsts.PRIVATE_KEY_PROP));
    
        SessionEncryptionReader sessionEncryption = new SessionEncryptionReader(
            privateKey, publicKey, deviceEngagement, readerEngagement);
        byte[] sessionTranscript = sessionEncryption.getSessionTranscript();

        // put sessionTranscript in datastore
        putByteArrInDatastore(ServletConsts.SESSION_TRANS_PROP, sessionTranscript);
        setDeviceRequestBoolean(true);

        // generate deviceRequest
        byte[] deviceRequest = new DeviceRequestGenerator().setSessionTranscript(sessionTranscript).generate();
        return sessionEncryption.encryptMessageToDevice(deviceRequest, OptionalInt.empty());
    }

    public String parseDeviceResponse(HttpServletRequest request) {
        PrivateKey privateKey = getPrivateKeyFromString(getPropertyFromDatastore(ServletConsts.PRIVATE_KEY_PROP));
        byte[] sessionTranscript = base64Decode(getPropertyFromDatastore(ServletConsts.SESSION_TRANS_PROP));
        byte[] deviceResponse = base64Decode(request.getParameter(ServletConsts.DEVICE_RESPONSE_PARAM));

        DeviceResponseParser.DeviceResponse dr = new DeviceResponseParser()
            .setDeviceResponse(deviceResponse)
            .setSessionTranscript(sessionTranscript)
            .setEphemeralReaderKey(privateKey)
            .parse();
        return new Gson().toJson(dr.getDocuments());
    }

    public Entity getEntity() {
        try {
            return datastore.get(datastoreKey);
        } catch (EntityNotFoundException e) {
            throw new IllegalStateException("Entity could not be found in database", e);
        }
    }

    public boolean getDeviceRequestBoolean() {
        return (boolean) getEntity().getProperty(ServletConsts.BOOLEAN_PROP);
    }

    public void setDeviceRequestBoolean(boolean bool) {
        Entity entity = getEntity();
        entity.setProperty(ServletConsts.BOOLEAN_PROP, bool);
        datastore.put(entity);
    }

    public byte[] CBOREncode(String str) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            new CborEncoder(baos).encode(new CborBuilder().add(str).build());
            return baos.toByteArray();
        } catch (CborException e) {
            throw new IllegalStateException("Error with CBOR encoding", e);
        }
    }

    public List<DataItem> CBORDecode(byte[] arr) {
        ByteArrayInputStream bais = new ByteArrayInputStream(arr);
        try {
            return new CborDecoder(bais).decode();
        } catch (CborException e) {
            throw new IllegalStateException("Error with CBOR decoding", e);
        }
    }

    public PublicKey getPublicKeyFromString(String str) {
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(base64Decode(str));
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(ServletConsts.KEY_GENERATION_INSTANCE);
            return keyFactory.generatePublic(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Error generating public key", e);
        }
    }

    public PrivateKey getPrivateKeyFromString(String str) {
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(base64Decode(str));
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(ServletConsts.KEY_GENERATION_INSTANCE);
            return keyFactory.generatePrivate(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Error generating public key", e);
        }
    }

    public void putByteArrInDatastore(String propName, byte[] arr) {
        Entity entity = getEntity();
        entity.setProperty(propName, base64Encode(arr));
        datastore.put(entity);
    }

    public String getPropertyFromDatastore(String propertyName) {
        Entity entity = getEntity();
        return (String) entity.getProperty(propertyName);
    }

    public byte[] base64Decode(String str) {
        return Base64.getMimeDecoder().decode(str.getBytes());
    }

    public String base64Encode(byte[] arr) {
        return Base64.getEncoder().encodeToString(arr);
    }
}