package com.google.sps;

import static org.mockito.Mockito.doReturn;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

// imports from Datastore
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import static com.google.appengine.api.datastore.FetchOptions.Builder.withLimit;

// imports from Identity Credential Library
import com.android.identity.TestVectors;
import com.android.identity.Util;
import com.android.identity.EngagementParser;
import com.android.identity.EngagementGenerator;
import com.android.identity.ConnectionMethod;
import com.android.identity.ConnectionMethodRestApi;
import com.android.identity.OriginInfoWebsite;
import com.android.identity.OriginInfo;

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
        stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        Mockito.when(response.getWriter()).thenReturn(writer);
        helper.setUp();
        servlet.init();
    }

    @After
    public void tearDown() {
        helper.tearDown();
    }
    
    @Test
    public void checkURIPrefix() throws IOException {
        servlet.doGet(request, response);
        String generatedURI = stringWriter.toString();
        Assert.assertEquals(generatedURI.substring(0,7), ServletConsts.MDOC_URI_PREFIX);
    }

    @Test
    public void checkReaderEngagementContents() throws IOException {
        servlet.doGet(request, response);
        String generatedURI = stringWriter.toString();
        String readerEngagement = generatedURI.substring(7);

        EngagementParser parser = new EngagementParser(Base64.getMimeDecoder().decode(readerEngagement));
        EngagementParser.Engagement engagement = parser.parse();

        Assert.assertEquals(engagement.getVersion(), "1.1");
        Assert.assertEquals(engagement.getConnectionMethods().size(), 1);
        Assert.assertEquals(engagement.getOriginInfos().size(), 1);
    }

    @Test
    public void checkDeviceRequestGenerationWithTestVector() throws IOException {
        fillDatastoreForDeviceRequestGeneration();
        byte[] sessionEstablishmentBytes = createMockSessionEstablishment();
        String sessionEstablishmentStr = Base64.getEncoder().encodeToString(sessionEstablishmentBytes);

        doReturn(sessionEstablishmentStr).when(request).getParameter(ServletConsts.SESSION_ESTABLISHMENT_PARAM);
        servlet.doPost(request, response);

        String sessionDataString = stringWriter.toString();
        byte[] sessionDataBytes = Base64.getMimeDecoder().decode(sessionDataString.getBytes());

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
        fillDatastoreForDeviceResponseParsing();
        byte[] deviceResponseBytes = Util.fromHex(
            TestVectors.ISO_18013_5_ANNEX_D_DEVICE_RESPONSE);
        String deviceResponseStr = Base64.getEncoder().encodeToString(deviceResponseBytes);

        doReturn(deviceResponseStr).when(request).getParameter(ServletConsts.DEVICE_RESPONSE_PARAM);
        servlet.doPost(request, response);

        // get back response (parsed DeviceResponse documents)
        String documentsJSON = stringWriter.toString();
        Assert.assertTrue(documentsJSON.length() > 0);
    }

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

    public byte[] createMockSessionEstablishment() {
        byte[] encodedSessionTranscriptBytes = Util.fromHex(
            TestVectors.ISO_18013_5_ANNEX_D_SESSION_TRANSCRIPT_BYTES);
        DataItem sessionTranscript = Util.cborExtractTaggedAndEncodedCbor(
                Util.cborDecode(encodedSessionTranscriptBytes));
        DataItem deviceEngagementBytes = ((Array) sessionTranscript).getDataItems().get(0);
        byte[] encodedDeviceEngagement = ((ByteString) deviceEngagementBytes).getBytes();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            new CborEncoder(baos).encode(new CborBuilder()
                .add("mock eReaderKey") // eReaderKeyBytes
                .add("mock data") // data
                .add(encodedDeviceEngagement) // device engagement
                .build());
        } catch (CborException e) {
            throw new IllegalStateException("Error with CBOR encoding", e);
        }
        return baos.toByteArray();
    }

    public void fillDatastoreForDeviceResponseParsing() {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        PrivateKey eReaderKeyPrivate = Util.getPrivateKeyFromInteger(new BigInteger(
            TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_D, 16));
        byte[] encodedSessionTranscriptBytes = Util.fromHex(
                TestVectors.ISO_18013_5_ANNEX_D_SESSION_TRANSCRIPT_BYTES);
        byte[] sessionTranscript = Util.cborEncode(
                Util.cborExtractTaggedAndEncodedCbor(
                        Util.cborDecode(encodedSessionTranscriptBytes)));

        RequestServlet.putByteArrInDatastore(ServletConsts.PRIVATE_KEY_PROP, eReaderKeyPrivate.getEncoded());
        RequestServlet.putByteArrInDatastore(ServletConsts.SESSION_TRANS_PROP, sessionTranscript);
        RequestServlet.setDeviceRequestBoolean(true);
    }
}