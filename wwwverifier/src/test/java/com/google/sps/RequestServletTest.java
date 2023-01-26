package com.android.identity.wwwreader;

// imports for CBOR encoding/decoding
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

// imports for HTTP requests
import java.io.PrintWriter;
import java.io.StringWriter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.ReadListener;
import javax.servlet.WriteListener;

// key generation imports
import java.security.PublicKey;
import java.security.PrivateKey;

// unit testing imports
import org.junit.After;
import org.junit.Before;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.doReturn;

// imports from Datastore
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.Text;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;

// other imports
import java.math.BigInteger;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

@RunWith(JUnit4.class)
public class RequestServletTest {
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;

    private RequestServlet servlet;
    private StringWriter stringWriter;
    private ByteArrayOutputStream byteWriter;

    private final LocalServiceTestHelper helper =
        new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig());

    private PublicKey eReaderKeyPublic = Util.getPublicKeyFromIntegers(
        new BigInteger(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_X, 16),
        new BigInteger(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_Y, 16));
    private PrivateKey eReaderKeyPrivate = Util.getPrivateKeyFromInteger(
        new BigInteger(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_D, 16));
    private PublicKey eDeviceKeyPublic = Util.getPublicKeyFromIntegers(
        new BigInteger(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_DEVICE_KEY_X, 16),
        new BigInteger(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_DEVICE_KEY_Y, 16));
    private PrivateKey eDeviceKeyPrivate = Util.getPrivateKeyFromInteger(
        new BigInteger(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_DEVICE_KEY_D, 16));
    private byte[] encodedSessionTranscriptBytes = Util.fromHex(
        TestVectors.ISO_18013_5_ANNEX_D_SESSION_TRANSCRIPT_BYTES);
    private DataItem sessionTranscript = Util.cborExtractTaggedAndEncodedCbor(
        Util.cborDecode(encodedSessionTranscriptBytes));

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);
        servlet = new RequestServlet();
        helper.setUp();
    }

    @After
    public void tearDown() {
        helper.tearDown();
    }

    @Test
    public void checkSessionCreation() throws IOException {
        setUpStringWriter();
        sendGetNewSessionRequest();

        String[] response = stringWriter.toString().split(ServletConsts.SESSION_SEPARATOR);
        Assert.assertTrue(response.length == 2);
        String generatedMdocUri = response[0];
        String generatedKeyString = response[1];

        Assert.assertTrue(generatedKeyString.length() > 0);
        Assert.assertEquals(generatedMdocUri.substring(0,ServletConsts.MDOC_PREFIX.length()),
            ServletConsts.MDOC_PREFIX);
        String readerEngagement = generatedMdocUri.substring(ServletConsts.MDOC_PREFIX.length());
        String readerEngagementEdited = readerEngagement.replace("\n","");
        EngagementParser.Engagement engagement =
            new EngagementParser(Base64.getUrlDecoder().decode(readerEngagementEdited)).parse();
        Assert.assertEquals(engagement.getVersion(), EngagementGenerator.ENGAGEMENT_VERSION_1_1);
        List<ConnectionMethod> connectionMethods = engagement.getConnectionMethods();
        Assert.assertEquals(connectionMethods.size(), 1);
        ConnectionMethodHttp connectionMethod = (ConnectionMethodHttp) connectionMethods.get(0);
        String generatedUriWebsite = connectionMethod.getUriWebsite();
        Assert.assertEquals(generatedUriWebsite.trim(),
            ServletConsts.ABSOLUTE_URL + "/" + generatedKeyString.trim());
    }

    @Test
    public void checkEmptyDeviceResponseMessage() throws IOException {
        setUpStringWriter();
        String dKeyStr = createSessionKey();
        sendGetDeviceResponseRequest(dKeyStr);
        String responseStr = stringWriter.toString().trim();
        Assert.assertTrue(responseStr.isEmpty());
    }

    @Test
    public void checkNonEmptyDeviceResponseMessage() throws IOException {
        setUpStringWriter();
        String dKeyStr = createSessionKey();
        Key dKey = com.google.appengine.api.datastore.KeyFactory.stringToKey(dKeyStr);
        String deviceResponseMessage = "Sample Device Response";
        RequestServlet.setDeviceResponse(deviceResponseMessage, dKey);
        sendGetDeviceResponseRequest(dKeyStr);
        String responseStr = stringWriter.toString().trim();
        Assert.assertEquals(responseStr, deviceResponseMessage);
    }

    @Test
    public void checkDeviceRequestGenerationWithWrongOriginInfo() throws IOException {
        setUpByteWriter();
        String dKeyStr = createSessionKey();
        Key dKey = com.google.appengine.api.datastore.KeyFactory.stringToKey(dKeyStr);

        byte[] re = RequestServlet.generateReaderEngagement(eReaderKeyPublic, dKey);
        RequestServlet.setDatastoreProp(ServletConsts.RE_PROP, re, dKey);
        RequestServlet.setDatastoreProp(ServletConsts.PUBKEY_PROP,
            eReaderKeyPublic.getEncoded(), dKey);
        RequestServlet.setDatastoreProp(ServletConsts.PRIVKEY_PROP,
            eReaderKeyPrivate.getEncoded(), dKey);

        // construct messageData (containing Device Engagement)
        EngagementGenerator eg = new EngagementGenerator(eDeviceKeyPublic,
            EngagementGenerator.ENGAGEMENT_VERSION_1_1);
        eg.addConnectionMethod(new ConnectionMethodHttp(ServletConsts.ABSOLUTE_URL + "/" + dKeyStr));
        String fakeBaseUrl = "https://fake-mdoc-reader.appspot.com/";
        eg.addOriginInfo(new OriginInfoWebsite(OriginInfo.CAT_DELIVERY, fakeBaseUrl));
        byte[] encodedDeviceEngagement = eg.generate();
        byte[] messageDataBytes = createMessageData(encodedDeviceEngagement);

        sendPostRequest(messageDataBytes, dKeyStr);

        byte[] sessionData = byteWriter.toByteArray();

        // parse sessionData to extract DeviceRequest
        byte[] generatedTranscript =
            RequestServlet.getDatastoreProp(ServletConsts.TRANSCRIPT_PROP, dKey);
        SessionEncryptionDevice sed =
            new SessionEncryptionDevice(eDeviceKeyPrivate, eReaderKeyPublic, generatedTranscript);
        DeviceRequestParser.DeviceRequest dr = new DeviceRequestParser()
            .setDeviceRequest(sed.decryptMessageFromReader(sessionData).getKey())
            .setSessionTranscript(generatedTranscript)
            .parse();

        Assert.assertEquals(EngagementGenerator.ENGAGEMENT_VERSION_1_0, dr.getVersion());
        List<DeviceRequestParser.DocumentRequest> docRequestsList = dr.getDocumentRequests();
        Assert.assertEquals(docRequestsList.size(), 1);
        DeviceRequestParser.DocumentRequest docRequest = docRequestsList.get(0);
        Assert.assertEquals(docRequest.getDocType(), ServletConsts.MDL_DOCTYPE);

        Assert.assertEquals(RequestServlet.getOriginInfoStatus(dKey),
            ServletConsts.OI_FAILURE_START + fakeBaseUrl + ServletConsts.OI_FAILURE_END);
    }

    @Test
    public void checkDeviceRequestGenerationWithCorrectOriginInfo() throws IOException {
        setUpByteWriter();
        String dKeyStr = createSessionKey();
        Key dKey = com.google.appengine.api.datastore.KeyFactory.stringToKey(dKeyStr);

        byte[] re = RequestServlet.generateReaderEngagement(eReaderKeyPublic, dKey);
        RequestServlet.setDatastoreProp(ServletConsts.RE_PROP, re, dKey);
        RequestServlet.setDatastoreProp(ServletConsts.PUBKEY_PROP,
            eReaderKeyPublic.getEncoded(), dKey);
        RequestServlet.setDatastoreProp(ServletConsts.PRIVKEY_PROP,
            eReaderKeyPrivate.getEncoded(), dKey);

        // construct messageData (containing Device Engagement)
        EngagementGenerator eg = new EngagementGenerator(eDeviceKeyPublic,
            EngagementGenerator.ENGAGEMENT_VERSION_1_1);
        eg.addConnectionMethod(new ConnectionMethodHttp(ServletConsts.ABSOLUTE_URL + "/" + dKeyStr));
        eg.addOriginInfo(new OriginInfoWebsite(OriginInfo.CAT_DELIVERY, ServletConsts.BASE_URL));
        byte[] encodedDeviceEngagement = eg.generate();
        byte[] messageDataBytes = createMessageData(encodedDeviceEngagement);

        sendPostRequest(messageDataBytes, dKeyStr);

        byte[] sessionData = byteWriter.toByteArray();

        // parse sessionData to extract DeviceRequest
        byte[] generatedTranscript =
            RequestServlet.getDatastoreProp(ServletConsts.TRANSCRIPT_PROP, dKey);
        SessionEncryptionDevice sed =
            new SessionEncryptionDevice(eDeviceKeyPrivate, eReaderKeyPublic, generatedTranscript);
        DeviceRequestParser.DeviceRequest dr = new DeviceRequestParser()
            .setDeviceRequest(sed.decryptMessageFromReader(sessionData).getKey())
            .setSessionTranscript(generatedTranscript)
            .parse();

        Assert.assertEquals(EngagementGenerator.ENGAGEMENT_VERSION_1_0, dr.getVersion());
        List<DeviceRequestParser.DocumentRequest> docRequestsList = dr.getDocumentRequests();
        Assert.assertEquals(docRequestsList.size(), 1);
        DeviceRequestParser.DocumentRequest docRequest = docRequestsList.get(0);
        Assert.assertEquals(docRequest.getDocType(), ServletConsts.MDL_DOCTYPE);
        Assert.assertEquals(RequestServlet.getOriginInfoStatus(dKey), ServletConsts.OI_SUCCESS);
    }

    @Test
    public void checkDeviceRequestGenerationWithTestVector() throws IOException {
        setUpByteWriter();
        String dKeyStr = createSessionKey();
        Key dKey = com.google.appengine.api.datastore.KeyFactory.stringToKey(dKeyStr);

        byte[] re = RequestServlet.generateReaderEngagement(eReaderKeyPublic, dKey);
        RequestServlet.setDatastoreProp(ServletConsts.RE_PROP, re, dKey);
        RequestServlet.setDatastoreProp(ServletConsts.PUBKEY_PROP,
            eReaderKeyPublic.getEncoded(), dKey);
        RequestServlet.setDatastoreProp(ServletConsts.PRIVKEY_PROP,
            eReaderKeyPrivate.getEncoded(), dKey);

        // construct messageData (containing Device Engagement)
        DataItem deviceEngagementBytes = ((Array) sessionTranscript).getDataItems().get(0);
        byte[] messageDataBytes = createMessageData(((ByteString) deviceEngagementBytes).getBytes());

        sendPostRequest(messageDataBytes, dKeyStr);

        byte[] sessionData = byteWriter.toByteArray();

        // parse sessionData to extract DeviceRequest
        byte[] generatedTranscript =
            RequestServlet.getDatastoreProp(ServletConsts.TRANSCRIPT_PROP, dKey);
        SessionEncryptionDevice sed =
            new SessionEncryptionDevice(eDeviceKeyPrivate, eReaderKeyPublic, generatedTranscript);
        DeviceRequestParser.DeviceRequest dr = new DeviceRequestParser()
            .setDeviceRequest(sed.decryptMessageFromReader(sessionData).getKey())
            .setSessionTranscript(generatedTranscript)
            .parse();

        Assert.assertEquals(EngagementGenerator.ENGAGEMENT_VERSION_1_0, dr.getVersion());
        List<DeviceRequestParser.DocumentRequest> docRequestsList = dr.getDocumentRequests();
        Assert.assertEquals(docRequestsList.size(), 1);
        DeviceRequestParser.DocumentRequest docRequest = docRequestsList.get(0);
        Assert.assertEquals(docRequest.getDocType(), ServletConsts.MDL_DOCTYPE);
        Assert.assertEquals(RequestServlet.getOriginInfoStatus(dKey),
            ServletConsts.OI_FAILURE_START + ServletConsts.OI_FAILURE_END.trim());
    }

    @Test
    public void checkDeviceResponseParsingWithTestVector() throws IOException {
        setUpByteWriter();
        String dKeyStr = createSessionKey();
        Key dKey = com.google.appengine.api.datastore.KeyFactory.stringToKey(dKeyStr);
        
        // put items in Datastore
        RequestServlet.setDatastoreProp(ServletConsts.PUBKEY_PROP,
            eReaderKeyPublic.getEncoded(), dKey);
        RequestServlet.setDatastoreProp(ServletConsts.PRIVKEY_PROP,
            eReaderKeyPrivate.getEncoded(), dKey);
        RequestServlet.setDatastoreProp(ServletConsts.DEVKEY_PROP,
            eDeviceKeyPublic.getEncoded(), dKey);
        RequestServlet.setDatastoreProp(ServletConsts.TRANSCRIPT_PROP,
            Util.cborEncode(sessionTranscript), dKey);
        RequestServlet.setOriginInfoStatus(ServletConsts.OI_FAILURE_START +
            ServletConsts.OI_FAILURE_END.trim(), dKey);
        RequestServlet.setNumPostRequests(1, dKey);
        
        byte[] sessionData = Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_SESSION_DATA);
        sendPostRequest(sessionData, dKeyStr);

        // process response
        byte[] responseMessage = byteWriter.toByteArray();
        SessionEncryptionDevice sed = new SessionEncryptionDevice(eDeviceKeyPrivate,
            eReaderKeyPublic, Util.cborEncode(sessionTranscript));
        Map.Entry<byte[], OptionalLong> responseMessageDecrypted =
            sed.decryptMessageFromReader(responseMessage);
        Assert.assertEquals(responseMessageDecrypted.getKey(), null);
        Assert.assertEquals(responseMessageDecrypted.getValue(), OptionalLong.of(20));
        String devResponseJSON = RequestServlet.getDeviceResponse(dKey);
        Assert.assertTrue(devResponseJSON.length() > 0);
    }

    /**
     * @param deviceEngagementBytes CBOR encoded Device Engagement data
     * @return CBOR encoded MessageData message, containing Device Engagement
     */
    private byte[] createMessageData(byte[] deviceEngagementBytes) {
        return Util.cborEncode(new CborBuilder()
            .addMap()
                .put(ServletConsts.DE_KEY, deviceEngagementBytes)
            .end().build().get(0));
    }

    /**
     * Sends a mock POST request, containing either a MessageData message or
     * a DeviceResponse message
     * 
     * @param data Data that should be sent through the request as a stream of bytes
     * @param sessionKey Unique identifier corresponding to the current session
     */
    private void sendPostRequest(byte[] data, String sessionKey) throws IOException {
        ServletInputStream sis = createMockInputStream(new ByteArrayInputStream(data));
        doReturn(data.length).when(request).getContentLength();
        doReturn(sis).when(request).getInputStream();
        doReturn("/" + sessionKey).when(request).getPathInfo();
        servlet.doPost(request, response);
    }

    /**
     * Sends a mock GET request to obtain the parsed DeviceResponse message tied to
     * {@ sessionKey}, if it exists
     * 
     * @param sessionKey Unique identifier corresponding to the current session
     */
    private void sendGetDeviceResponseRequest(String sessionKey) throws IOException {
        doReturn("/" + ServletConsts.RESPONSE_URL + "/" + sessionKey).when(request).getPathInfo();
        servlet.doGet(request, response);
    }

    /**
     * Sends a mock GET request to create a new session
     */
    private void sendGetNewSessionRequest() throws IOException {
        doReturn("/" + ServletConsts.SESSION_URL).when(request).getPathInfo();
        servlet.doGet(request, response);
    }

    /**
     * Creates a new session, and returns a unique identifier associated with it
     */
    private String createSessionKey() {
        return RequestServlet.createNewSession().split(",")[1];
    }

    /**
     * Sets up an output stream of bytes to be written into via {@ response}
     */
    private void setUpByteWriter() throws IOException {
        byteWriter = new ByteArrayOutputStream();
        ServletOutputStream os = createMockOutputStream(byteWriter);
        Mockito.when(response.getOutputStream()).thenReturn(os);
    }

    /**
     * Sets up a character stream to be written into via {@ response}
     */
    private void setUpStringWriter() throws IOException {
        stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        Mockito.when(response.getWriter()).thenReturn(writer);
    }

    /**
     * Helper method that converts a ByteArrayInputStream object to a ServletInputStream object
     */
    private ServletInputStream createMockInputStream(ByteArrayInputStream bais) {
        return new ServletInputStream() {
            private ReadListener readListener = null;
    
            @Override
            public boolean isFinished() {
                return false;
            }
    
            @Override
            public boolean isReady() {
                return isFinished();
            }
    
            @Override
            public void setReadListener(ReadListener readListener) {
                this.readListener = readListener;
            }
    
            @Override
            public int read() throws IOException {
                return bais.read();
            }
        };
    }

    /**
     * Helper method that converts a ByteArrayOutputStream object to a ServletOutputStream object
     */
    private ServletOutputStream createMockOutputStream(ByteArrayOutputStream baos) {
        return new ServletOutputStream() {
            private WriteListener writeListener = null;

            @Override
            public void write(int b) throws IOException {
                baos.write(b);
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
                this.writeListener = writeListener;
            }

            @Override
            public boolean isReady() {
                return true;
            }
        };
    }
}