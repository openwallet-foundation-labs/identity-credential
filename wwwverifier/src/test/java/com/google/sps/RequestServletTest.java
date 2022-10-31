package com.google.sps;

import static org.mockito.Mockito.doReturn;

// imports for CBOR encoding/decoding
import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborEncoder;
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
import java.io.InputStream;
import java.io.OutputStream;
import javax.servlet.ReadListener;

// key generation imports
import java.security.KeyPair;
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
import java.util.OptionalInt;

// imports from Datastore
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import static com.google.appengine.api.datastore.FetchOptions.Builder.withLimit;
import com.google.appengine.api.datastore.Text;

// imports from Identity Credential Library
import com.google.sps.servlets.TestVectors;
import com.google.sps.servlets.Util;
import com.google.sps.servlets.EngagementParser;
import com.google.sps.servlets.EngagementGenerator;
import com.google.sps.servlets.ConnectionMethod;
import com.google.sps.servlets.ConnectionMethodHttp;
import com.google.sps.servlets.OriginInfoWebsite;
import com.google.sps.servlets.OriginInfo;
import com.google.sps.servlets.SessionEncryptionReader;

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
    
    @Test
    public void checkURIPrefix() throws IOException {
        stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        Mockito.when(response.getWriter()).thenReturn(writer);

        doReturn(ServletConsts.GET_PARAM_URI).when(request).getParameter(ServletConsts.GET_PARAM);
        servlet.doGet(request, response);
        String generatedURI = stringWriter.toString();
        Assert.assertEquals(generatedURI.substring(0,ServletConsts.MDOC_URI_PREFIX.length()), ServletConsts.MDOC_URI_PREFIX);
    }
  
    @Test
    public void checkReaderEngagementContents() throws IOException {
        stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        Mockito.when(response.getWriter()).thenReturn(writer);

        doReturn(ServletConsts.GET_PARAM_URI).when(request).getParameter(ServletConsts.GET_PARAM);
        servlet.doGet(request, response);
        String generatedURI = stringWriter.toString();
        String readerEngagement = generatedURI.substring(ServletConsts.MDOC_URI_PREFIX.length());
  
        EngagementParser parser = new EngagementParser(Base64.getMimeDecoder().decode(readerEngagement));
        EngagementParser.Engagement engagement = parser.parse();
  
        Assert.assertEquals(engagement.getVersion(), "1.1");
        Assert.assertEquals(engagement.getConnectionMethods().size(), 1);
    } 

    @Test
    public void checkDefaultDeviceResponseMessage() throws IOException {
        stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        Mockito.when(response.getWriter()).thenReturn(writer);

        doReturn(ServletConsts.GET_PARAM_RESPONSE).when(request).getParameter(ServletConsts.GET_PARAM);
        servlet.doGet(request, response);
        String responseStr = stringWriter.toString().trim();
        Assert.assertEquals(responseStr, ServletConsts.DEFAULT_RESPONSE_MSSG);
    } 

    @Test
    public void checkNonEmptyDeviceResponseMessage() throws IOException {
        stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        Mockito.when(response.getWriter()).thenReturn(writer);

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Entity entity = RequestServlet.getEntity();
        String correctMessage = "Sample Device Response";
        entity.setProperty(ServletConsts.DEVICE_RESPONSE_PROP, new Text(correctMessage));
        datastore.put(entity);
  
        doReturn(ServletConsts.GET_PARAM_RESPONSE).when(request).getParameter(ServletConsts.GET_PARAM);
        servlet.doGet(request, response);
        String responseStr = stringWriter.toString().trim();
        Assert.assertEquals(responseStr, correctMessage);
    }

    /*
    @Test
    public void checkDeviceRequestGenerationWithTestVector() throws IOException {
        stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        Mockito.when(response.getWriter()).thenReturn(writer);

        fillDatastoreForDeviceRequestGeneration();
        byte[] encodedSessionTranscriptBytes = Util.fromHex(
            TestVectors.ISO_18013_5_ANNEX_D_SESSION_TRANSCRIPT_BYTES);
        DataItem sessionTranscript = Util.cborExtractTaggedAndEncodedCbor(
                Util.cborDecode(encodedSessionTranscriptBytes));
        DataItem deviceEngagementBytes = ((Array) sessionTranscript).getDataItems().get(0);
        byte[] encodedDeviceEngagement = ((ByteString) deviceEngagementBytes).getBytes();
        byte[] messageDataBytes = createMockMessageData(ServletConsts.DEVICE_ENGAGEMENT_KEY, encodedDeviceEngagement);

        ServletInputStream sis = createMockInputStream(messageDataBytes);

        doReturn(messageDataBytes.length).when(request).getContentLength();
        doReturn(sis).when(request).getInputStream();
        servlet.doPost(request, response);

        String sessionDataString = stringWriter.toString();
        byte[] sessionDataBytes = Base64.getMimeDecoder().decode(sessionDataString.getBytes());

        //ByteArrayOutputStream baos = new ByteArrayOutputStream();
        //baos.writeTo(os);
        //byte[] sessionDataBytes = baos.toByteArray();

        // decode sessionData
        ByteArrayInputStream bais = new ByteArrayInputStream(sessionDataBytes);
        try {
            List<DataItem> dataItems = new CborDecoder(bais).decode();
            // assert that the size of dataItems is either 1 or 2 (depending on whether status is included)
            Assert.assertTrue(dataItems.size() > 0 && dataItems.size() < 3);
            // assert that DeviceRequest is present
            Assert.assertNotNull(dataItems.get(0));
        } catch (CborException e) {
            throw new IllegalStateException("Error with CBOR decoding", e);
        }
    }

    @Test
    public void checkDeviceResponseParsingWithTestVector() throws IOException {
        stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        Mockito.when(response.getWriter()).thenReturn(writer);

        SessionEncryptionReader reader = fillDatastoreForDeviceResponseParsing();
        byte[] encodedDeviceResponse = Util.fromHex(
            TestVectors.ISO_18013_5_ANNEX_D_DEVICE_RESPONSE);
        
        byte[] message = reader.encryptMessageToDevice(encodedDeviceResponse, OptionalInt.empty());
        ServletInputStream sis = createMockInputStream(message);

        doReturn(message.length).when(request).getContentLength();
        doReturn(sis).when(request).getInputStream();
        servlet.doPost(request, response);

        // get back response (parsed DeviceResponse documents)
        String documentsJSON = stringWriter.toString();
        Assert.assertTrue(documentsJSON.length() > 0);
    }
    */

    public void fillDatastoreForDeviceRequestGeneration() {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        KeyPair keyPair = RequestServlet.generateKeyPair();
        PublicKey publicKey = keyPair.getPublic();
        PrivateKey privateKey = keyPair.getPrivate();

        byte[] readerEngagement = RequestServlet.generateReaderEngagement(publicKey);

        RequestServlet.putByteArrInDatastore(ServletConsts.READER_ENGAGEMENT_PROP, readerEngagement);
        RequestServlet.putByteArrInDatastore(ServletConsts.PUBLIC_KEY_PROP, publicKey.getEncoded());
        RequestServlet.putByteArrInDatastore(ServletConsts.PRIVATE_KEY_PROP, privateKey.getEncoded());
        RequestServlet.setDeviceRequestBoolean(false);
    }

    public byte[] createMockMessageData(String name, byte[] data) {
        CborBuilder builder = new CborBuilder();
        MapBuilder<CborBuilder> map = builder.addMap();
        map.put(name,data);
        map.end();

        return Util.cborEncode(builder.build().get(0));
    }

    public SessionEncryptionReader fillDatastoreForDeviceResponseParsing() {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        PublicKey eReaderKeyPublic = Util.getPublicKeyFromIntegers(
            new BigInteger(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_X, 16),
            new BigInteger(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_Y, 16));
        PrivateKey eReaderKeyPrivate = Util.getPrivateKeyFromInteger(new BigInteger(
            TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_D, 16));
        PublicKey eDeviceKeyPublic = Util.getPublicKeyFromIntegers(
            new BigInteger(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_DEVICE_KEY_X, 16),
            new BigInteger(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_DEVICE_KEY_Y, 16));
        byte[] encodedSessionTranscriptBytes = Util.fromHex(
                TestVectors.ISO_18013_5_ANNEX_D_SESSION_TRANSCRIPT_BYTES);
        byte[] sessionTranscript = Util.cborEncode(
                Util.cborExtractTaggedAndEncodedCbor(
                        Util.cborDecode(encodedSessionTranscriptBytes)));

        RequestServlet.putByteArrInDatastore(ServletConsts.PUBLIC_KEY_PROP, eReaderKeyPublic.getEncoded());
        RequestServlet.putByteArrInDatastore(ServletConsts.PRIVATE_KEY_PROP, eReaderKeyPrivate.getEncoded());
        RequestServlet.putByteArrInDatastore("Device Key", eDeviceKeyPublic.getEncoded());
        RequestServlet.putByteArrInDatastore(ServletConsts.SESSION_TRANS_PROP, sessionTranscript);
        RequestServlet.setDeviceRequestBoolean(true);

        return new SessionEncryptionReader(eReaderKeyPrivate, eReaderKeyPublic, eDeviceKeyPublic, sessionTranscript);
    }

    public ServletInputStream createMockInputStream(byte[] data) {
        return new ServletInputStream() {
            private int lastIndexRetrieved = -1;
            private ReadListener readListener = null;
    
            @Override
            public boolean isFinished() {
                return (lastIndexRetrieved == data.length-1);
            }
    
            @Override
            public boolean isReady() {
                return isFinished();
            }
    
            @Override
            public void setReadListener(ReadListener readListener) {
                this.readListener = readListener;
                if (!isFinished()) {
                    try {
                        readListener.onDataAvailable();
                    } catch (IOException e) {
                        readListener.onError(e);
                    }
                } else {
                    try {
                        readListener.onAllDataRead();
                    } catch (IOException e) {
                        readListener.onError(e);
                    }
                }
            }
    
            @Override
            public int read() throws IOException {
                int i;
                if (!isFinished()) {
                    i = data[lastIndexRetrieved+1];
                    lastIndexRetrieved++;
                    if (isFinished() && (readListener != null)) {
                        try {
                            readListener.onAllDataRead();
                        } catch (IOException ex) {
                            readListener.onError(ex);
                            throw ex;
                        }
                    }
                    return i;
                } else {
                    return -1;
                }
            }
        };
    }
}