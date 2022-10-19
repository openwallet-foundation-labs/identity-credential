package com.google.sps;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.DataItem;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.json.JSONArray;

public class ReaderEngagementParser {

    private String tstr;
    private String cipherSuite;
    private String eReaderKeyBytes;
    private String connectionMethodType;
    private String connectionMethodVersion;
    private String connectionMethodWebsite;
    
    public ReaderEngagementParser() {
    }

    public void parse(byte[] byteArr) {
        ByteArrayInputStream bais = new ByteArrayInputStream(byteArr);
        List<DataItem> dataItems;
        try {
            dataItems = new CborDecoder(bais).decode();

            // check tstr
            tstr = dataItems.get(0).toString();

            String generatedSecurity = dataItems.get(1).toString();
            JSONArray generatedSecurityJSON = new JSONArray(generatedSecurity);
            // check cipher suite identifier
            cipherSuite = generatedSecurityJSON.get(0).toString();
            // check that ephemeral key exists
            eReaderKeyBytes = generatedSecurityJSON.get(1).toString();

            // check ConnectionMethods
            String generatedConnectionMethods = dataItems.get(2).toString();
            String generatedMethod = generatedConnectionMethods.substring(2, generatedConnectionMethods.length() - 2);
            String[] generatedMethodArr = generatedMethod.split(",");
            connectionMethodType = generatedMethodArr[0].trim();
            connectionMethodVersion = generatedMethodArr[1].trim();
            connectionMethodWebsite = generatedMethodArr[2].trim().substring(1, generatedMethodArr[2].length() - 2);
        } catch (CborException e) {
            throw new IllegalStateException("Error with CBOR decoding", e);
        }
    }

    public String getTstr() {
        return tstr;
    }

    public String getCipherSuite() {
        return cipherSuite;
    }

    public String getEReaderKeyBytes() {
        return eReaderKeyBytes;
    }

    public String getConnectionMethodType() {
        return connectionMethodType;
    }

    public String getConnectionMethodVersion() {
        return connectionMethodVersion;
    }

    public String getConnectionMethodWebsite() {
        return connectionMethodWebsite;
    }
}
