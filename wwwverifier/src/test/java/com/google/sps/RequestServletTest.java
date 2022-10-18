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

// imports from custom classes
import com.google.sps.servlets.RequestServlet;
import com.google.sps.servlets.ServletConsts;
import com.google.sps.servlets.ReaderEngagementGenerator;

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
        String generatedURISansPrefix = generatedURI.substring(7);

        ReaderEngagementParser parser = new ReaderEngagementParser();
        parser.parse(Base64.getMimeDecoder().decode(generatedURISansPrefix));

        Assert.assertEquals(parser.getTstr(), "1.1");
        Assert.assertEquals(parser.getCipherSuite(), "1");
        Assert.assertEquals(parser.getEReaderKeyBytes().length() > 0, true);
        Assert.assertEquals(parser.getConnectionMethodType(), "4");
        Assert.assertEquals(parser.getConnectionMethodVersion(), "1");
        Assert.assertEquals(parser.getConnectionMethodWebsite(), "https://mdoc-reader.googleplex.com/request-mdl");
    }

    @Test
    public void checkDeviceRequestGenerationWithTestVector() throws IOException {
        FillDatastoreForDeviceRequestGeneration();
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

    public void FillDatastoreForDeviceRequestGeneration() {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        ReaderEngagementGenerator generator = new ReaderEngagementGenerator();
        byte[] readerEngagement = generator.generate();
        byte[] publicKey = generator.getPublicKey().getEncoded();
        byte[] privateKey = generator.getPrivateKey().getEncoded();

        try {
            Entity entity = datastore.get(RequestServlet.datastoreKey);
            entity.setProperty(ServletConsts.READER_ENGAGEMENT_PROP, Base64.getEncoder().encodeToString(readerEngagement));
            entity.setProperty(ServletConsts.PUBLIC_KEY_PROP, Base64.getEncoder().encodeToString(publicKey));
            entity.setProperty(ServletConsts.PRIVATE_KEY_PROP, Base64.getEncoder().encodeToString(privateKey));
            entity.setProperty(ServletConsts.SESSION_TRANS_PROP, false);
            entity.setProperty(ServletConsts.BOOLEAN_PROP, false);
            datastore.put(entity);
        } catch (EntityNotFoundException e) {
            throw new IllegalStateException("Entity could not be found in database", e);
        }
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

        try {
            Entity entity = datastore.get(RequestServlet.datastoreKey);
            entity.setProperty(ServletConsts.PRIVATE_KEY_PROP, Base64.getEncoder().encodeToString(eReaderKeyPrivate.getEncoded()));
            entity.setProperty(ServletConsts.SESSION_TRANS_PROP, Base64.getEncoder().encodeToString(sessionTranscript));
            entity.setProperty(ServletConsts.BOOLEAN_PROP, true);
            datastore.put(entity);
        } catch (EntityNotFoundException e) {
            throw new IllegalStateException("Entity could not be found in database", e);
        }
    }
}