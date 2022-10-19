package com.google.sps.servlets;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import java.io.ByteArrayOutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.PrivateKey;

public class ReaderEngagementGenerator {
    private static final String TAG = "ReaderEngagementGenerator";

    private PublicKey eReaderKeyPublic;
    private PrivateKey eReaderKeyPrivate;

    public ReaderEngagementGenerator() {
        // generate ephemeral key pair (eReaderKeyPublic, eReaderKeyPrivate)
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(ServletConsts.KEY_GENERATION_INSTANCE);
            ECGenParameterSpec ecSpec = new ECGenParameterSpec(ServletConsts.KEY_GENERATION_CURVE);
            generator.initialize(ecSpec);
            KeyPair keyPair = generator.generateKeyPair();
            eReaderKeyPublic = keyPair.getPublic();
            eReaderKeyPrivate = keyPair.getPrivate();
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e1) {
            throw new IllegalStateException("Error generating ephemeral key-pair", e1);
        }
    }

    public PublicKey getPublicKey() {
        return eReaderKeyPublic;
    }

    public PrivateKey getPrivateKey() {
        return eReaderKeyPrivate;
    }

    public byte[] generate() {
        // create ReaderEngagement CBOR message
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            new CborEncoder(baos).encode(new CborBuilder()
                .add("1.1") // tstr
                .addArray() // Security
                    .add(1) // cipher suite identifier
                    .add(eReaderKeyPublic.getEncoded()) // EReaderKeyBytes
                    .end()
                .addArray() // ConnectionMethods
                    .addArray() // ConnectionMethod
                        .add(4) // type
                        .add(1) // version
                        .addArray() // RestApiOptions
                            .add("https://mdoc-reader.googleplex.com/request-mdl") // website URI
                            .end()
                        .end()
                    .end()
                .build());
        } catch (CborException e) {
            throw new IllegalStateException("Error with CBOR encoding", e);
        }
        return baos.toByteArray();
    }
}
