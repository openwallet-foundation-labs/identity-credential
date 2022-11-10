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
import javax.sql.DataSource;
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

    public static DatastoreService ds;

    @Override
    public void init() {
        ds = DatastoreServiceFactory.getDatastoreService();
    }

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
        String pathInfo = request.getPathInfo().substring(1);
        String[] pathArr = pathInfo.split("/");
        if (pathInfo.equals(ServletConsts.SESSION_URL)) {
            response.getWriter().println(createNewSession());
        } else if (pathArr[0].equals(ServletConsts.RESPONSE_URL)) {
            Key dKey = 
                com.google.appengine.api.datastore.KeyFactory.stringToKey(pathArr[1]);
            response.getWriter().println(getDeviceResponse(dKey));
        }
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
        Key dKey = entity.getKey();
        String dKeyStr = com.google.appengine.api.datastore.KeyFactory.keyToString(dKey);
        setNumPostRequests(0, dKey);
        return createMdocUri(dKey) + ServletConsts.SESSION_SEPARATOR + dKeyStr;
    }

    /**
    * Generates ReaderEngagement CBOR message, and creates a URI from it.
    *
    * @param dKey Unique key tied to an existing entity in Datastore
    * @return Generated mdoc:// URI
    */
    public static String createMdocUri(Key dKey) {
        KeyPair keyPair = generateKeyPair();
        byte[] readerEngagement = generateReaderEngagement(keyPair.getPublic(), dKey);
  
        // put ReaderEngagement and generated ephemeral keys into Datastore
        setDatastoreProp(ServletConsts.READER_ENGAGEMENT_PROP, readerEngagement, dKey);
        setDatastoreProp(ServletConsts.PUBKEY_PROP, keyPair.getPublic().getEncoded(), dKey);
        setDatastoreProp(ServletConsts.PRIVKEY_PROP, keyPair.getPrivate().getEncoded(), dKey);

        String readerEngagementStr =
            Base64.getUrlEncoder().withoutPadding().encodeToString(readerEngagement);
        String readerEngagementStrEdited = readerEngagementStr.replace("\n","");
  
        return ServletConsts.MDOC_PREFIX + readerEngagementStrEdited;
    }

    /**
     * Retrieves DeviceResponse message from Datastore.
     * 
     * @param dKey key corresponding to an entity in Datastore
     * @return String containing DeviceResponse message, or an empty string if it does not exist
     */
    public static String getDeviceResponse(Key dKey) {
        Entity entity = getEntity(dKey);
        if (entity.hasProperty(ServletConsts.DEV_RESPONSE_PROP)) {
            Text deviceResponse = (Text) entity.getProperty(ServletConsts.DEV_RESPONSE_PROP);
            return deviceResponse.getValue();
        }
        return new String();
    } 

    /**
     * Handles two distinct POST requests:
     * (1) The first POST request sent to the servlet, which contains a MessageData message
     * (2) The second POST request sent to the servlet, which contains a DeviceResponse message
     * 
     * @param request Either (1) MessageData, containing DeviceEngagement or (2) DeviceResponse,
     * both in the form of byte arrays
     * @return (1) response, containing a DeviceRequest message as an encoded byte array (or a 403 error); 
     * (2) response, containing a termination message with status code 20
     */
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String pathInfo = request.getPathInfo().substring(1);
        Key dKey = com.google.appengine.api.datastore.KeyFactory.stringToKey(pathInfo);
        if (getNumPostRequests(dKey) == 0) {
            byte[] sessionData = createDeviceRequest(getBytesFromRequest(request), dKey);
            setNumPostRequests(1, dKey);
            if (sessionData == null) {
                response.sendError(403);
            } else {
                response.getOutputStream().write(sessionData);
            }
        } else if (getNumPostRequests(dKey) == 1) {
            byte[] terminationMessage = parseDeviceResponse(getBytesFromRequest(request), dKey);
            setNumPostRequests(2, dKey);
            response.getOutputStream().write(terminationMessage);
        }
    }

     /**
      * Parses the MessageData CBOR message to isolate DeviceEngagement. DeviceEngagement
      * is then used to create a DeviceRequest CBOR message.
      *
      * @param messageData CBOR message extracted from POST request
      * @param dKey Key corresponding to an entity in Datastore
      * @return SessionData CBOR message containing DeviceRequest
      */
    public static byte[] createDeviceRequest(byte[] messageData, Key dKey) {
        byte[] encodedDeviceEngagement =
            Util.cborMapExtractByteString(Util.cborDecode(messageData),
                ServletConsts.DEV_ENGAGEMENT_KEY);
        PublicKey eReaderKeyPublic =
            getDecodedPublicKey(getDatastoreProp(ServletConsts.PUBKEY_PROP, dKey));
        PrivateKey eReaderKeyPrivate =
            getDecodedPrivateKey(getDatastoreProp(ServletConsts.PRIVKEY_PROP, dKey));
        byte[] readerEngagementBytes =
            getDatastoreProp(ServletConsts.READER_ENGAGEMENT_PROP, dKey);

        EngagementParser.Engagement de = new EngagementParser(encodedDeviceEngagement).parse();
        PublicKey eDeviceKeyPublic = de.getESenderKey();
        List<OriginInfo> oiList = de.getOriginInfos();
        if (oiList.size() > 0) {
            OriginInfoWebsite oi = (OriginInfoWebsite) oiList.get(0);
            if (!oi.getBaseUrl().equals(ServletConsts.BASE_URL)) {
                return null;
            }
        }
        setDatastoreProp(ServletConsts.DEVKEY_PROP, eDeviceKeyPublic.getEncoded(), dKey);

        byte[] sessionTranscript = Util.cborEncode(new CborBuilder()
            .addArray()
                .add(Util.cborBuildTaggedByteString(encodedDeviceEngagement))
                .add(Util.cborBuildTaggedByteString(Util.cborEncode(Util.cborBuildCoseKey(eReaderKeyPublic))))
                .add(Util.cborBuildTaggedByteString(readerEngagementBytes))
            .end().build().get(0));
        setDatastoreProp(ServletConsts.TRANSCRIPT_PROP, sessionTranscript, dKey);

        SessionEncryptionReader ser = new SessionEncryptionReader(eReaderKeyPrivate,
            eReaderKeyPublic, eDeviceKeyPublic, sessionTranscript);
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
    public static byte[] generateReaderEngagement(PublicKey publicKey, Key dKey) {
        EngagementGenerator eg = new EngagementGenerator(publicKey,
            EngagementGenerator.ENGAGEMENT_VERSION_1_1);
        String dKeyStr = com.google.appengine.api.datastore.KeyFactory.keyToString(dKey);
        eg.addConnectionMethod(new ConnectionMethodHttp(ServletConsts.ABSOLUTE_URL + "/" + dKeyStr));
        return eg.generate();
    }

    /**
     * @return generated ephemeral reader key pair (containing a PublicKey and a PrivateKey)
     */
    public static KeyPair generateKeyPair() {
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
     * @param messageData CBOR message containing DeviceResponse to be decoded and parsed
     * @param dKey Unique key of the entity in Datastore that corresponds to the current
     * session
     * 
     * @return byte array containing a termination message
     */
    public static byte[] parseDeviceResponse(byte[] messageData, Key dKey) {
        PublicKey eReaderKeyPublic =
            getDecodedPublicKey(getDatastoreProp(ServletConsts.PUBKEY_PROP, dKey));
        PrivateKey eReaderKeyPrivate =
            getDecodedPrivateKey(getDatastoreProp(ServletConsts.PRIVKEY_PROP, dKey));
        PublicKey eDeviceKeyPublic =
            getDecodedPublicKey(getDatastoreProp(ServletConsts.DEVKEY_PROP, dKey));
        byte[] sessionTranscript = getDatastoreProp(ServletConsts.TRANSCRIPT_PROP, dKey);

        SessionEncryptionReader ser = new SessionEncryptionReader(eReaderKeyPrivate,
            eReaderKeyPublic, eDeviceKeyPublic, sessionTranscript);
        
        DeviceResponseParser.DeviceResponse dr = new DeviceResponseParser()
            .setDeviceResponse(ser.decryptMessageFromDevice(messageData))
            .setSessionTranscript(sessionTranscript)
            .setEphemeralReaderKey(eReaderKeyPrivate)
            .parse();
        String json = new Gson().toJson(buildJson(dr.getDocuments()));

        Entity entity = getEntity(dKey);
        entity.setProperty(ServletConsts.DEV_RESPONSE_PROP, new Text(json));
        ds.put(entity);

        return ser.encryptMessageToDevice(null, OptionalInt.of(20));
    }

    /**
     * Extracts specified fields from each DeviceResponseParser.Document object within {@ docs}
     * 
     * @param docs List of Document objects containing Mdoc information that had been requested
     * from the app in an earlier POST request
     * @return ArrayList of String data extracted from {@ docs} that will be displayed on the website
     */
    public static ArrayList<String> buildJson(List<DeviceResponseParser.Document> docs) {
        ArrayList<String> arr = new ArrayList<String>();
        arr.add("Number of documents returned: " + docs.size());
        for (DeviceResponseParser.Document doc : docs) {
            arr.add("Doctype: " + doc.getDocType());
            if (doc.getIssuerSignedAuthenticated()) {
                arr.add("Issuer Signed Authenticated");
            }
            if (doc.getDeviceSignedAuthenticatedViaSignature()) {
                arr.add("Device Signed Authenticated (ECDSA)");
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
     * @return Datastore entity linked to the key @param dKey created at the start
     * of the session
     */
    public static Entity getEntity(Key dKey) {
        try {
            return ds.get(dKey);
        } catch (EntityNotFoundException e) {
            throw new IllegalStateException("Entity could not be found in database", e);
        }
    }

    /**
     * @return PublicKey, converted from a byte array @param arr
     */
    public static PublicKey getDecodedPublicKey(byte[] arr) {
        try {
            KeyFactory keyFactory =
                KeyFactory.getInstance(ServletConsts.KEYGEN_INSTANCE);
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
            KeyFactory keyFactory =
                KeyFactory.getInstance(ServletConsts.KEYGEN_INSTANCE);
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
     * @param dKey Unique key of the entity in Datastore that should be updated
     */
    public static void setDatastoreProp(String propName, byte[] arr, Key dKey) {
        Entity entity = getEntity(dKey);
        entity.setProperty(propName, new Blob(arr));
        ds.put(entity);
    }

    /**
     * Retrieves byte array data from Datastore
     * 
     * @param propName Name of the property that should be retrieved from Datastore
     * @param dKey Unique key of the entity in Datastore that should be updated
     * @return byte array that corresponds to {@ propName}
     */
    public static byte[] getDatastoreProp(String propName, Key dKey) {
        return ((Blob) getEntity(dKey).getProperty(propName)).getBytes();
    }

    /**
     * @param num Number of POST requests that have been sent to the current session so far
     * @param dKey Unique key of the entity in Datastore that should be updated
     */
    public static void setNumPostRequests(int num, Key dKey) {
        Entity entity = getEntity(dKey);
        entity.setProperty(ServletConsts.NUM_POSTS_PROP, num);
        ds.put(entity);
    }

    /**
     * @param dKey Unique key of the entity in the Datastore that should be updated
     * @return (as int) number of POST requests that have been sent to the current session so far
     */
    public static int getNumPostRequests(Key dKey) {
        return ((Long) getEntity(dKey).getProperty(ServletConsts.NUM_POSTS_PROP)).intValue();
    }
}