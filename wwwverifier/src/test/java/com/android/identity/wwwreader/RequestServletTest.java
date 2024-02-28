/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.identity.wwwreader;

// imports for CBOR encoding/decoding
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

// unit testing imports
import org.bouncycastle.jce.provider.BouncyCastleProvider;
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

// identity credential logic imports
import com.android.identity.cbor.Cbor;
import com.android.identity.cbor.CborMap;
import com.android.identity.cbor.DataItem;
import com.android.identity.crypto.EcPrivateKey;
import com.android.identity.crypto.EcPrivateKeyDoubleCoordinate;
import com.android.identity.internal.Util;
import com.android.identity.mdoc.connectionmethod.ConnectionMethod;
import com.android.identity.mdoc.connectionmethod.ConnectionMethodHttp;
import com.android.identity.mdoc.engagement.EngagementGenerator;
import com.android.identity.mdoc.engagement.EngagementParser;
import com.android.identity.mdoc.origininfo.OriginInfoDomain;
import com.android.identity.mdoc.request.DeviceRequestParser;
import com.android.identity.mdoc.sessionencryption.SessionEncryption;

// imports from Datastore
import com.android.identity.crypto.EcCurve;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;

// other imports
import java.io.IOException;
import java.security.Security;
import java.util.Base64;
import java.util.Collections;
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

    private EcPrivateKey eReaderKey =
            new EcPrivateKeyDoubleCoordinate(
                    EcCurve.P256,
                    Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_D),
                    Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_X),
                    Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_Y)
            );
    private EcPrivateKey eDeviceKey =
            new EcPrivateKeyDoubleCoordinate(
                    EcCurve.P256,
                    Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_DEVICE_KEY_D),
                    Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_DEVICE_KEY_X),
                    Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_DEVICE_KEY_Y)
            );
    private byte[] encodedSessionTranscriptBytes = Util.fromHex(
        TestVectors.ISO_18013_5_ANNEX_D_SESSION_TRANSCRIPT_BYTES);
    private DataItem sessionTranscript =
            Cbor.decode(encodedSessionTranscriptBytes).getAsTaggedEncodedCbor();

    @Before
    public void setUp() {
        Security.insertProviderAt(new BouncyCastleProvider(), 1);
        MockitoAnnotations.initMocks(this);
        servlet = new RequestServlet();
        servlet.setStoreLogs(false);
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
        String generatedUriWebsite = connectionMethod.getUri();
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

        byte[] re = RequestServlet.generateReaderEngagement(eReaderKey.getPublicKey(), dKey);
        RequestServlet.setDatastoreProp(ServletConsts.RE_PROP, re, dKey);
        RequestServlet.setDatastoreProp(ServletConsts.PRIVKEY_PROP,
            Cbor.encode(eReaderKey.toCoseKey(Map.of()).getToDataItem()), dKey);

        // construct messageData (containing Device Engagement)
        EngagementGenerator eg = new EngagementGenerator(eDeviceKey.getPublicKey(),
            EngagementGenerator.ENGAGEMENT_VERSION_1_1);
        eg.addConnectionMethods(Collections.singletonList(new ConnectionMethodHttp(
                ServletConsts.ABSOLUTE_URL + "/" + dKeyStr)));
        String fakeBaseUrl = "https://fake-mdoc-reader.appspot.com/";
        eg.addOriginInfos(Collections.singletonList(
                new OriginInfoDomain(fakeBaseUrl)));
        byte[] encodedDeviceEngagement = eg.generate();
        byte[] messageDataBytes = createMessageData(encodedDeviceEngagement);

        sendPostRequest(messageDataBytes, dKeyStr);

        byte[] sessionData = byteWriter.toByteArray();

        // parse sessionData to extract DeviceRequest
        byte[] generatedTranscript =
            RequestServlet.getDatastoreProp(ServletConsts.TRANSCRIPT_PROP, dKey);
        SessionEncryption sed =
            new SessionEncryption(SessionEncryption.ROLE_MDOC,
                    eDeviceKey,
                    eReaderKey.getPublicKey(),
                    generatedTranscript);
        DeviceRequestParser.DeviceRequest dr = new DeviceRequestParser()
            .setDeviceRequest(sed.decryptMessage(sessionData).getData())
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

        byte[] re = RequestServlet.generateReaderEngagement(eReaderKey.getPublicKey(), dKey);
        RequestServlet.setDatastoreProp(ServletConsts.RE_PROP, re, dKey);
        RequestServlet.setDatastoreProp(ServletConsts.PRIVKEY_PROP,
            Cbor.encode(eReaderKey.toCoseKey(Map.of()).getToDataItem()), dKey);

        // construct messageData (containing Device Engagement)
        EngagementGenerator eg = new EngagementGenerator(eDeviceKey.getPublicKey(),
                EngagementGenerator.ENGAGEMENT_VERSION_1_1);
        eg.addConnectionMethods(Collections.singletonList(
                new ConnectionMethodHttp(ServletConsts.ABSOLUTE_URL + "/" + dKeyStr)));
        eg.addOriginInfos(Collections.singletonList(
                new OriginInfoDomain(ServletConsts.BASE_URL)));
        byte[] encodedDeviceEngagement = eg.generate();
        byte[] messageDataBytes = createMessageData(encodedDeviceEngagement);

        sendPostRequest(messageDataBytes, dKeyStr);

        byte[] sessionData = byteWriter.toByteArray();

        // parse sessionData to extract DeviceRequest
        byte[] generatedTranscript =
            RequestServlet.getDatastoreProp(ServletConsts.TRANSCRIPT_PROP, dKey);
        SessionEncryption sed =
                new SessionEncryption(SessionEncryption.ROLE_MDOC,
                        eDeviceKey,
                        eReaderKey.getPublicKey(),
                        generatedTranscript);
        DeviceRequestParser.DeviceRequest dr = new DeviceRequestParser()
                .setDeviceRequest(sed.decryptMessage(sessionData).getData())
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

        byte[] re = RequestServlet.generateReaderEngagement(eReaderKey.getPublicKey(), dKey);
        RequestServlet.setDatastoreProp(ServletConsts.RE_PROP, re, dKey);
        RequestServlet.setDatastoreProp(ServletConsts.PRIVKEY_PROP,
                Cbor.encode(eReaderKey.toCoseKey(Map.of()).getToDataItem()), dKey);

        // construct messageData (containing Device Engagement)
        DataItem deviceEngagementBytes = sessionTranscript.get(0);
        byte[] messageDataBytes = createMessageData(deviceEngagementBytes.getAsTagged().getAsBstr());

        sendPostRequest(messageDataBytes, dKeyStr);

        byte[] sessionData = byteWriter.toByteArray();

        // parse sessionData to extract DeviceRequest
        byte[] generatedTranscript =
            RequestServlet.getDatastoreProp(ServletConsts.TRANSCRIPT_PROP, dKey);
        SessionEncryption sed =
                new SessionEncryption(SessionEncryption.ROLE_MDOC,
                        eDeviceKey,
                        eReaderKey.getPublicKey(),
                        generatedTranscript);
        DeviceRequestParser.DeviceRequest dr = new DeviceRequestParser()
                .setDeviceRequest(sed.decryptMessage(sessionData).getData())
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
        RequestServlet.setDatastoreProp(ServletConsts.PRIVKEY_PROP,
                Cbor.encode(eReaderKey.toCoseKey(Map.of()).getToDataItem()), dKey);
        RequestServlet.setDatastoreProp(ServletConsts.DEVKEY_PROP,
                Cbor.encode(eDeviceKey.getPublicKey().toCoseKey(Map.of()).getToDataItem()), dKey);
        RequestServlet.setDatastoreProp(ServletConsts.TRANSCRIPT_PROP,
            Cbor.encode(sessionTranscript), dKey);
        RequestServlet.setOriginInfoStatus(ServletConsts.OI_FAILURE_START +
            ServletConsts.OI_FAILURE_END.trim(), dKey);
        RequestServlet.setNumPostRequests(1, dKey);
        
        byte[] sessionData = Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_SESSION_DATA);
        sendPostRequest(sessionData, dKeyStr);

        // process response
        byte[] responseMessage = byteWriter.toByteArray();
        SessionEncryption sed =
                new SessionEncryption(SessionEncryption.ROLE_MDOC,
                        eDeviceKey,
                        eReaderKey.getPublicKey(),
                        Cbor.encode(sessionTranscript));
        SessionEncryption.DecryptedMessage responseMessageDecrypted =
            sed.decryptMessage(responseMessage);
        Assert.assertEquals(responseMessageDecrypted.getData(), null);
        Assert.assertEquals(responseMessageDecrypted.getStatus(), OptionalLong.of(20));
        String devResponseJSON = RequestServlet.getDeviceResponse(dKey);
        Assert.assertTrue(devResponseJSON.length() > 0);
    }

    /**
     * @param deviceEngagementBytes CBOR encoded Device Engagement data
     * @return CBOR encoded MessageData message, containing Device Engagement
     */
    private byte[] createMessageData(byte[] deviceEngagementBytes) {
        return Cbor.encode(CborMap.Companion.builder()
                .put(ServletConsts.DE_KEY, deviceEngagementBytes)
                .end().build());
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
        return RequestServlet.createNewSession("all").split(",")[1];
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