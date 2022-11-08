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

// imports from Datastore
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.appengine.api.datastore.Text;

// imports from Identity Credential Library
import com.google.sps.servlets.DeviceRequestParser;
import com.google.sps.servlets.EngagementParser;
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

    private final LocalServiceTestHelper helper = new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig());

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
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        doReturn("/" + ServletConsts.NEW_SESSION_URL).when(request).getPathInfo();
        servlet.doGet(request, response);
        String[] response = stringWriter.toString().split(",");

        Assert.assertTrue(response[1].length() > 0);
        Key dataKey = com.google.appengine.api.datastore.KeyFactory.stringToKey(response[1]);
        String generatedURI = response[0];
        Assert.assertEquals(generatedURI.substring(0,ServletConsts.MDOC_URI_PREFIX.length()), ServletConsts.MDOC_URI_PREFIX);

        String readerEngagement = generatedURI.substring(ServletConsts.MDOC_URI_PREFIX.length());
        String readerEngagementEdited = readerEngagement.replace("\n","");

        EngagementParser parser = new EngagementParser(Base64.getUrlDecoder().decode(readerEngagementEdited));
        EngagementParser.Engagement engagement = parser.parse();
  
        Assert.assertEquals(engagement.getVersion(), "1.1");
        Assert.assertEquals(engagement.getConnectionMethods().size(), 1);
    }

    @Test
    public void checkDefaultDeviceResponseMessage() throws IOException {
        setUpWriter();
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        String dataKeyStr = RequestServlet.createNewSession().split(",")[1];
        doReturn("/" + ServletConsts.RESPONSE_URL + "/" + dataKeyStr).when(request).getPathInfo();
        servlet.doGet(request, response);
        String responseStr = stringWriter.toString().trim();
        Assert.assertEquals(responseStr, "");
    }


    @Test
    public void checkNonEmptyDeviceResponseMessage() throws IOException {
        setUpWriter();
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        String dataKeyStr = RequestServlet.createNewSession().split(",")[1];
        Key dataKey = com.google.appengine.api.datastore.KeyFactory.stringToKey(dataKeyStr);

        Entity entity = RequestServlet.getEntity(dataKey);
        String correctMessage = "Sample Device Response";
        entity.setProperty(ServletConsts.DEVICE_RESPONSE_PROP, new Text(correctMessage));
        datastore.put(entity);
  
        doReturn("/" + ServletConsts.RESPONSE_URL + "/" + dataKeyStr).when(request).getPathInfo();
        servlet.doGet(request, response);
        String responseStr = stringWriter.toString().trim();
        Assert.assertEquals(responseStr, correctMessage);
    }


    @Test
    public void checkDeviceRequestGenerationWithTestVector() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ServletOutputStream os = createMockOutputStream(baos);
        Mockito.when(response.getOutputStream()).thenReturn(os);

        String dataKeyStr = RequestServlet.createNewSession().split(",")[1];
        Key dataKey = com.google.appengine.api.datastore.KeyFactory.stringToKey(dataKeyStr);

        fillDatastoreForDeviceRequestGeneration(dataKey);

        // construct messageData (containing Device Engagement)
        DataItem deviceEngagementBytes = ((Array) sessionTranscript).getDataItems().get(0);
        byte[] messageDataBytes = createMockMessageData(ServletConsts.DEVICE_ENGAGEMENT_KEY, ((ByteString) deviceEngagementBytes).getBytes());
        ServletInputStream sis = createMockInputStream(new ByteArrayInputStream(messageDataBytes));

        // POST request
        doReturn(messageDataBytes.length).when(request).getContentLength();
        doReturn(sis).when(request).getInputStream();
        doReturn("/" + dataKeyStr).when(request).getPathInfo();
        servlet.doPost(request, response);

        byte[] sessionData = baos.toByteArray();

        // parse sessionData to extract DeviceRequest
        byte[] generatedSessionTranscript = RequestServlet.getDatastoreProp(ServletConsts.SESSION_TRANS_PROP, dataKey);
        SessionEncryptionDevice sed = new SessionEncryptionDevice(eDeviceKeyPrivate, eReaderKeyPublic, generatedSessionTranscript);
        DeviceRequestParser.DeviceRequest dr = new DeviceRequestParser()
            .setDeviceRequest(sed.decryptMessageFromReader(sessionData))
            .setSessionTranscript(generatedSessionTranscript)
            .parse();

        Assert.assertEquals("1.0", dr.getVersion());
        Assert.assertEquals(1, dr.getDocumentRequests().size());
    }

    @Test
    public void checkDeviceResponseParsingWithTestVector() throws IOException {
        setUpWriter();
        String dataKeyStr = RequestServlet.createNewSession().split(",")[1];
        Key dataKey = com.google.appengine.api.datastore.KeyFactory.stringToKey(dataKeyStr);
        fillDatastoreForDeviceResponseParsing(dataKey);

        byte[] sessionData = Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_SESSION_DATA);
        
        ServletInputStream sis = createMockInputStream(new ByteArrayInputStream(sessionData));

        doReturn(sessionData.length).when(request).getContentLength();
        doReturn(sis).when(request).getInputStream();
        doReturn("/" + dataKeyStr).when(request).getPathInfo();
        servlet.doPost(request, response);

        // get back response (parsed DeviceResponse documents)
        String documentsJSON = stringWriter.toString();
        Assert.assertTrue(documentsJSON.length() > 0);
    }

    public void fillDatastoreForDeviceRequestGeneration(Key datastoreKey) {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        byte[] readerEngagement = RequestServlet.generateReaderEngagement(eReaderKeyPublic, datastoreKey);

        RequestServlet.setDatastoreProp(ServletConsts.READER_ENGAGEMENT_PROP, readerEngagement, datastoreKey);
        RequestServlet.setDatastoreProp(ServletConsts.PUBLIC_KEY_PROP, eReaderKeyPublic.getEncoded(), datastoreKey);
        RequestServlet.setDatastoreProp(ServletConsts.PRIVATE_KEY_PROP, eReaderKeyPrivate.getEncoded(), datastoreKey);
    }

    public byte[] createMockMessageData(String name, byte[] data) {
        CborBuilder builder = new CborBuilder();
        MapBuilder<CborBuilder> map = builder.addMap();
        map.put(name, data);
        map.end();
        return Util.cborEncode(builder.build().get(0));
    }

    public void fillDatastoreForDeviceResponseParsing(Key datastoreKey) {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        RequestServlet.setDatastoreProp(ServletConsts.PUBLIC_KEY_PROP, eReaderKeyPublic.getEncoded(), datastoreKey);
        RequestServlet.setDatastoreProp(ServletConsts.PRIVATE_KEY_PROP, eReaderKeyPrivate.getEncoded(), datastoreKey);
        RequestServlet.setDatastoreProp(ServletConsts.DEVICE_KEY_PROP, eDeviceKeyPublic.getEncoded(), datastoreKey);
        RequestServlet.setDatastoreProp(ServletConsts.SESSION_TRANS_PROP, Util.cborEncode(sessionTranscript), datastoreKey);
        RequestServlet.setNumPostRequests(1, datastoreKey);
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