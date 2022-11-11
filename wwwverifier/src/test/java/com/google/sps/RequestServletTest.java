package com.google.sps;

import static org.mockito.Mockito.doReturn;

// imports for CBOR encoding/decoding
import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

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

// other imports
import java.math.BigInteger;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

// imports from Datastore
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.appengine.api.datastore.Text;

// imports from Identity Credential Library
import com.google.sps.servlets.ConnectionMethod;
import com.google.sps.servlets.ConnectionMethodHttp;
import com.google.sps.servlets.DeviceRequestParser;
import com.google.sps.servlets.EngagementGenerator;
import com.google.sps.servlets.EngagementParser;
import com.google.sps.servlets.OriginInfo;
import com.google.sps.servlets.OriginInfoWebsite;
import com.google.sps.servlets.SessionEncryptionDevice;
import com.google.sps.servlets.TestVectors;
import com.google.sps.servlets.Util;

// imports from custom classes
import com.google.sps.servlets.RequestServlet;
import com.google.sps.servlets.ServletConsts;

@RunWith(JUnit4.class)
public class RequestServletTest {
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;

    private RequestServlet servlet;
    private StringWriter stringWriter;

    private final LocalServiceTestHelper helper =
        new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig());

    private PublicKey eReaderKeyPublic = Util.getPublicKeyFromIntegers(
        new BigInteger(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_X, 16),
        new BigInteger(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_Y, 16));
    private PrivateKey eReaderKeyPrivate = Util.getPrivateKeyFromInteger(new BigInteger(
        TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_D, 16));
    private PublicKey eDeviceKeyPublic = Util.getPublicKeyFromIntegers(
        new BigInteger(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_DEVICE_KEY_X, 16),
        new BigInteger(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_DEVICE_KEY_Y, 16));
    private PrivateKey eDeviceKeyPrivate = Util.getPrivateKeyFromInteger(new BigInteger(
        TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_DEVICE_KEY_D, 16));
    private byte[] encodedSessionTranscriptBytes = Util.fromHex(
        TestVectors.ISO_18013_5_ANNEX_D_SESSION_TRANSCRIPT_BYTES);
    private DataItem sessionTranscript = Util.cborExtractTaggedAndEncodedCbor(
        Util.cborDecode(encodedSessionTranscriptBytes));

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);
        servlet = new RequestServlet();
        helper.setUp();
        servlet.init();
    }

    @After
    public void tearDown() {
        helper.tearDown();
    }

    public void setUpWriter() throws IOException {
        stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        Mockito.when(response.getWriter()).thenReturn(writer);
    }

    @Test
    public void checkSessionCreation() throws IOException {
        setUpWriter();
        doReturn("/" + ServletConsts.SESSION_URL).when(request).getPathInfo();

        servlet.doGet(request, response);

        String[] response = stringWriter.toString().split(ServletConsts.SESSION_SEPARATOR);
        Assert.assertTrue(response.length == 2);
        String generatedMdocUri = response[0];
        String generatedKeyString = response[1];

        Assert.assertTrue(generatedKeyString.length() > 0);
        Assert.assertEquals(generatedMdocUri.substring(0,ServletConsts.MDOC_PREFIX.length()),
            ServletConsts.MDOC_PREFIX);
        String readerEngagement = generatedMdocUri.substring(ServletConsts.MDOC_PREFIX.length());
        String readerEngagementEdited = readerEngagement.replace("\n","");
        EngagementParser parser =
            new EngagementParser(Base64.getUrlDecoder().decode(readerEngagementEdited));
        EngagementParser.Engagement engagement = parser.parse();
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
        setUpWriter();
        String dKeyStr = RequestServlet.createNewSession().split(",")[1];
        doReturn("/" + ServletConsts.RESPONSE_URL + "/" + dKeyStr).when(request).getPathInfo();
        servlet.doGet(request, response);
        String responseStr = stringWriter.toString().trim();
        Assert.assertTrue(responseStr.isEmpty());
    }

    @Test
    public void checkNonEmptyDeviceResponseMessage() throws IOException {
        setUpWriter();
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        String dKeyStr = RequestServlet.createNewSession().split(",")[1];
        Key dKey = com.google.appengine.api.datastore.KeyFactory.stringToKey(dKeyStr);
        Entity entity = RequestServlet.getEntity(dKey);
        String deviceResponseMessage = "Sample Device Response";
        entity.setProperty(ServletConsts.DEV_RESPONSE_PROP, new Text(deviceResponseMessage));
        datastore.put(entity);
  
        doReturn("/" + ServletConsts.RESPONSE_URL + "/" + dKeyStr).when(request).getPathInfo();
        servlet.doGet(request, response);
        String responseStr = stringWriter.toString().trim();
        Assert.assertEquals(responseStr, deviceResponseMessage);
    }

    @Test
    public void checkDeviceRequestGenerationWithWrongOriginInfo() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ServletOutputStream os = createMockOutputStream(baos);
        Mockito.when(response.getOutputStream()).thenReturn(os);
        String dKeyStr = RequestServlet.createNewSession().split(",")[1];
        Key dKey = com.google.appengine.api.datastore.KeyFactory.stringToKey(dKeyStr);

        byte[] re = RequestServlet.generateReaderEngagement(eReaderKeyPublic, dKey);
        RequestServlet.setDatastoreProp(ServletConsts.READER_ENGAGEMENT_PROP, re, dKey);
        RequestServlet.setDatastoreProp(ServletConsts.PUBKEY_PROP, eReaderKeyPublic.getEncoded(), dKey);
        RequestServlet.setDatastoreProp(ServletConsts.PRIVKEY_PROP, eReaderKeyPrivate.getEncoded(), dKey);

        // construct messageData (containing Device Engagement)
        EngagementGenerator eg = new EngagementGenerator(eDeviceKeyPublic, EngagementGenerator.ENGAGEMENT_VERSION_1_1);
        eg.addConnectionMethod(new ConnectionMethodHttp(ServletConsts.ABSOLUTE_URL + "/" + dKeyStr));
        String fakeBaseUrl = "https://fake-mdoc-reader.appspot.com/";
        eg.addOriginInfo(new OriginInfoWebsite(OriginInfo.CAT_DELIVERY, fakeBaseUrl));
        byte[] encodedDeviceEngagement = eg.generate();
        byte[] messageDataBytes = createMockMessageData(ServletConsts.DEV_ENGAGEMENT_KEY, encodedDeviceEngagement);
        ServletInputStream sis = createMockInputStream(new ByteArrayInputStream(messageDataBytes));

        // POST request
        doReturn(messageDataBytes.length).when(request).getContentLength();
        doReturn(sis).when(request).getInputStream();
        doReturn("/" + dKeyStr).when(request).getPathInfo();
        servlet.doPost(request, response);

        byte[] sessionData = baos.toByteArray();

        // parse sessionData to extract DeviceRequest
        byte[] generatedTranscript = RequestServlet.getDatastoreProp(ServletConsts.TRANSCRIPT_PROP, dKey);
        SessionEncryptionDevice sed =
            new SessionEncryptionDevice(eDeviceKeyPrivate, eReaderKeyPublic, generatedTranscript);
        DeviceRequestParser.DeviceRequest dr = new DeviceRequestParser()
            .setDeviceRequest(sed.decryptMessageFromReader(sessionData).getKey())
            .setSessionTranscript(generatedTranscript)
            .parse();

        Assert.assertEquals("1.0", dr.getVersion());
        List<DeviceRequestParser.DocumentRequest> docRequestsList = dr.getDocumentRequests();
        Assert.assertEquals(docRequestsList.size(), 1);
        DeviceRequestParser.DocumentRequest docRequest = docRequestsList.get(0);
        Assert.assertEquals(docRequest.getDocType(), ServletConsts.MDL_DOCTYPE);

        Assert.assertEquals(RequestServlet.getOriginInfoStatus(dKey),
            ServletConsts.OI_FAILURE_START + fakeBaseUrl + ServletConsts.OI_FAILURE_END);
    }

    @Test
    public void checkDeviceRequestGenerationWithCorrectOriginInfo() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ServletOutputStream os = createMockOutputStream(baos);
        Mockito.when(response.getOutputStream()).thenReturn(os);
        String dKeyStr = RequestServlet.createNewSession().split(",")[1];
        Key dKey = com.google.appengine.api.datastore.KeyFactory.stringToKey(dKeyStr);

        byte[] re = RequestServlet.generateReaderEngagement(eReaderKeyPublic, dKey);
        RequestServlet.setDatastoreProp(ServletConsts.READER_ENGAGEMENT_PROP, re, dKey);
        RequestServlet.setDatastoreProp(ServletConsts.PUBKEY_PROP, eReaderKeyPublic.getEncoded(), dKey);
        RequestServlet.setDatastoreProp(ServletConsts.PRIVKEY_PROP, eReaderKeyPrivate.getEncoded(), dKey);

        // construct messageData (containing Device Engagement)
        EngagementGenerator eg = new EngagementGenerator(eDeviceKeyPublic, EngagementGenerator.ENGAGEMENT_VERSION_1_1);
        eg.addConnectionMethod(new ConnectionMethodHttp(ServletConsts.ABSOLUTE_URL + "/" + dKeyStr));
        eg.addOriginInfo(new OriginInfoWebsite(OriginInfo.CAT_DELIVERY, ServletConsts.BASE_URL));
        byte[] encodedDeviceEngagement = eg.generate();
        byte[] messageDataBytes = createMockMessageData(ServletConsts.DEV_ENGAGEMENT_KEY, encodedDeviceEngagement);
        ServletInputStream sis = createMockInputStream(new ByteArrayInputStream(messageDataBytes));

        // POST request
        doReturn(messageDataBytes.length).when(request).getContentLength();
        doReturn(sis).when(request).getInputStream();
        doReturn("/" + dKeyStr).when(request).getPathInfo();
        servlet.doPost(request, response);

        byte[] sessionData = baos.toByteArray();

        // parse sessionData to extract DeviceRequest
        byte[] generatedTranscript = RequestServlet.getDatastoreProp(ServletConsts.TRANSCRIPT_PROP, dKey);
        SessionEncryptionDevice sed =
            new SessionEncryptionDevice(eDeviceKeyPrivate, eReaderKeyPublic, generatedTranscript);
        DeviceRequestParser.DeviceRequest dr = new DeviceRequestParser()
            .setDeviceRequest(sed.decryptMessageFromReader(sessionData).getKey())
            .setSessionTranscript(generatedTranscript)
            .parse();

        Assert.assertEquals("1.0", dr.getVersion());
        List<DeviceRequestParser.DocumentRequest> docRequestsList = dr.getDocumentRequests();
        Assert.assertEquals(docRequestsList.size(), 1);
        DeviceRequestParser.DocumentRequest docRequest = docRequestsList.get(0);
        Assert.assertEquals(docRequest.getDocType(), ServletConsts.MDL_DOCTYPE);
        Assert.assertEquals(RequestServlet.getOriginInfoStatus(dKey), ServletConsts.OI_SUCCESS);
    }

    @Test
    public void checkDeviceRequestGenerationWithTestVector() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ServletOutputStream os = createMockOutputStream(baos);
        Mockito.when(response.getOutputStream()).thenReturn(os);
        String dKeyStr = RequestServlet.createNewSession().split(",")[1];
        Key dKey = com.google.appengine.api.datastore.KeyFactory.stringToKey(dKeyStr);

        byte[] re = RequestServlet.generateReaderEngagement(eReaderKeyPublic, dKey);
        RequestServlet.setDatastoreProp(ServletConsts.READER_ENGAGEMENT_PROP, re, dKey);
        RequestServlet.setDatastoreProp(ServletConsts.PUBKEY_PROP, eReaderKeyPublic.getEncoded(), dKey);
        RequestServlet.setDatastoreProp(ServletConsts.PRIVKEY_PROP, eReaderKeyPrivate.getEncoded(), dKey);

        // construct messageData (containing Device Engagement)
        DataItem deviceEngagementBytes = ((Array) sessionTranscript).getDataItems().get(0);
        byte[] messageDataBytes =
            createMockMessageData(ServletConsts.DEV_ENGAGEMENT_KEY,
                ((ByteString) deviceEngagementBytes).getBytes());
        ServletInputStream sis = createMockInputStream(new ByteArrayInputStream(messageDataBytes));

        // POST request
        doReturn(messageDataBytes.length).when(request).getContentLength();
        doReturn(sis).when(request).getInputStream();
        doReturn("/" + dKeyStr).when(request).getPathInfo();
        servlet.doPost(request, response);

        byte[] sessionData = baos.toByteArray();

        // parse sessionData to extract DeviceRequest
        byte[] generatedTranscript = RequestServlet.getDatastoreProp(ServletConsts.TRANSCRIPT_PROP, dKey);
        SessionEncryptionDevice sed =
            new SessionEncryptionDevice(eDeviceKeyPrivate, eReaderKeyPublic, generatedTranscript);
        DeviceRequestParser.DeviceRequest dr = new DeviceRequestParser()
            .setDeviceRequest(sed.decryptMessageFromReader(sessionData).getKey())
            .setSessionTranscript(generatedTranscript)
            .parse();

        Assert.assertEquals("1.0", dr.getVersion());
        List<DeviceRequestParser.DocumentRequest> docRequestsList = dr.getDocumentRequests();
        Assert.assertEquals(docRequestsList.size(), 1);
        DeviceRequestParser.DocumentRequest docRequest = docRequestsList.get(0);
        Assert.assertEquals(docRequest.getDocType(), ServletConsts.MDL_DOCTYPE);
        Assert.assertEquals(RequestServlet.getOriginInfoStatus(dKey), ServletConsts.OI_QRCODE);
    }

    @Test
    public void checkDeviceResponseParsingWithTestVector() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ServletOutputStream os = createMockOutputStream(baos);
        Mockito.when(response.getOutputStream()).thenReturn(os);
        String dKeyStr = RequestServlet.createNewSession().split(",")[1];
        Key dKey = com.google.appengine.api.datastore.KeyFactory.stringToKey(dKeyStr);
        
        // put items in Datastore
        RequestServlet.setDatastoreProp(ServletConsts.PUBKEY_PROP, eReaderKeyPublic.getEncoded(), dKey);
        RequestServlet.setDatastoreProp(ServletConsts.PRIVKEY_PROP, eReaderKeyPrivate.getEncoded(), dKey);
        RequestServlet.setDatastoreProp(ServletConsts.DEVKEY_PROP, eDeviceKeyPublic.getEncoded(), dKey);
        RequestServlet.setDatastoreProp(ServletConsts.TRANSCRIPT_PROP,
            Util.cborEncode(sessionTranscript), dKey);
        RequestServlet.setOriginInfoStatus(ServletConsts.OI_QRCODE, dKey);
        RequestServlet.setNumPostRequests(1, dKey);
        
        // send POST request
        byte[] sessionData = Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_SESSION_DATA);
        ServletInputStream sis = createMockInputStream(new ByteArrayInputStream(sessionData));
        doReturn(sessionData.length).when(request).getContentLength();
        doReturn(sis).when(request).getInputStream();
        doReturn("/" + dKeyStr).when(request).getPathInfo();
        servlet.doPost(request, response);

        // process response
        byte[] responseMessage = baos.toByteArray();
        SessionEncryptionDevice sed =
            new SessionEncryptionDevice(eDeviceKeyPrivate, eReaderKeyPublic, Util.cborEncode(sessionTranscript));
        Map.Entry<byte[], OptionalLong> responseMessageDecrypted = sed.decryptMessageFromReader(responseMessage);
        Assert.assertEquals(responseMessageDecrypted.getKey(), null);
        Assert.assertEquals(responseMessageDecrypted.getValue(), OptionalLong.of(20));
        String devResponseJSON = RequestServlet.getDeviceResponse(dKey);
        Assert.assertTrue(devResponseJSON.length() > 0);
    }

    public byte[] createMockMessageData(String name, byte[] data) {
        CborBuilder builder = new CborBuilder();
        MapBuilder<CborBuilder> map = builder.addMap();
        map.put(name, data);
        map.end();
        return Util.cborEncode(builder.build().get(0));
    }

    public ServletInputStream createMockInputStream(ByteArrayInputStream bais) {
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

    public ServletOutputStream createMockOutputStream(ByteArrayOutputStream baos) {
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