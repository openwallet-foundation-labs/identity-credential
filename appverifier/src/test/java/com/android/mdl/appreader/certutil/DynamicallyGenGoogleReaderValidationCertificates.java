package com.android.mdl.appreader.certutil;

import org.bouncycastle.asn1.x509.KeyUsage;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.Period;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public class DynamicallyGenGoogleReaderValidationCertificates {

    private static final String TIME_STAMP_OID = "1.3.6.1.5.5.7.3.8";

    private static class RootKeyMaterial implements KeyMaterial {
        private final KeyPair iacaKeyPair;

        private RootKeyMaterial(KeyPair iacaKeyPair) {
            this.iacaKeyPair = iacaKeyPair;
        }

        @Override
        public PublicKey publicKey() {
            return iacaKeyPair.getPublic();
        }

        @Override
        public String signingAlgorithm() {
            return "SHA384WithECDSA";
        }

        @Override
        public PrivateKey signingKey() {
            return iacaKeyPair.getPrivate();
        }

        @Override
        public Optional<X509Certificate> issuerCertificate() {
            return Optional.empty();
        }
    }

    private static class RootCertificateMaterial implements CertificateMaterial {
        @Override
        public BigInteger serialNumber() {
            return new BigInteger("476f6f676c655f546573745f43415f31", 16);
        }

        @Override
        public Date startDate() {
            return EncodingUtil.parseShortISODate("2021-09-07");
        }

        @Override
        public Date endDate() {
            return EncodingUtil.parseShortISODate("2030-09-07");
        }

        @Override
        public Optional<Integer> pathLengthConstraint() {
            return Optional.of(0);
        }

        @Override
        public List<String> extendedKeyUsage() {
            List<String> strings = new java.util.ArrayList<>();
            strings.add("1.0.18013.5.1.7");
            return strings;
        }

        @Override
        public int keyUsage() {
            return KeyUsage.keyCertSign | KeyUsage.cRLSign;
        }

        @Override
        public boolean isCa() {
            return true;
        }
    }

    private static class RootDataMaterial implements DataMaterial {
        private final String dn;

        private RootDataMaterial(String dn) {
            this.dn = dn;
        }

        @Override
        public String subjectDN() {
            return dn;
        }

        @Override
        public String issuerDN() {
            return dn;
        }

        @Override
        public Optional<String> issuerAlternativeName() {
            // NOTE always interpreted as URL for now
            return Optional.of("https://www.google.com/");
        }
    }

    private static class RaKeyMaterial implements KeyMaterial {
        private final X509Certificate issuerCert;
        private final KeyPair keyPair;
        private final PrivateKey issuerPrivateKey;

        private RaKeyMaterial(X509Certificate issuerCert, KeyPair dsKeyPair, PrivateKey issuerPrivateKey) {
            this.issuerCert = issuerCert;
            this.keyPair = dsKeyPair;
            this.issuerPrivateKey = issuerPrivateKey;
        }

        @Override
        public PublicKey publicKey() {
            return keyPair.getPublic();
        }

        @Override
        public String signingAlgorithm() {
            return "SHA256WithECDSA";
        }

        @Override
        public PrivateKey signingKey() {
            return issuerPrivateKey;
        }

        @Override
        public Optional<X509Certificate> issuerCertificate() {
            return Optional.of(issuerCert);
        }
    }

    private static class RaCertificateMaterial implements CertificateMaterial {
        @Override
        public BigInteger serialNumber() {
            // TODO change
            return new BigInteger("476f6f676c655f546573745f44535f31", 16);
        }

        @Override
        public Date startDate() {
            return EncodingUtil.parseShortISODate("2021-09-07");
        }

        @Override
        public Date endDate() {
            return EncodingUtil.parseShortISODate("2022-12-07");
        }

        @Override
        public int keyUsage() {
            return KeyUsage.digitalSignature;
        }

        @Override
        public List<String> extendedKeyUsage() {
            // TODO change for mDL
            return Collections.emptyList();
        }
        
        @Override
        public boolean includeKeyIds() {
            return true;
        }

        @Override
        public boolean isCa() {
            return false;
        }

        @Override
        public Optional<Integer> pathLengthConstraint() {
            return Optional.empty();
        }
    }

    private static class RaDataMaterial implements DataMaterial {
        @Override
        public String subjectDN() {
            return "CN=Google TEST RA mDL, C=UT";
        }

        @Override
        public String issuerDN() {
            // must match DN of issuer character-by-character
            // TODO change for other generators
            return "CN=Google TEST Root mDL, C=UT";

            // reorders string
            // return issuerCert.getSubjectX500Principal().getName();
        }

        @Override
        public Optional<String> issuerAlternativeName() {
            // NOTE always interpreted as URL for now
            return Optional.of("https://www.google.com/");
        }
    }

    public static List<X509Certificate> createGoodCertificatePath() throws Exception {
        String curve = "secp256r1";
        KeyPair rootKeyPair = ECKeyPairGenerator.generateKeyPair(curve);

        X509Certificate rootCertificate = createRootCertificate(rootKeyPair);

        KeyPair raKeyPair = ECKeyPairGenerator.generateKeyPair(curve);
        X509Certificate raCertificate = createRaCertificate(raKeyPair, rootCertificate, rootKeyPair.getPrivate());

        List<X509Certificate> certificates = new java.util.ArrayList<>();
        certificates.add(raCertificate);
        certificates.add(rootCertificate);
        return certificates;
    }

    public static List<X509Certificate> createCertificatePathWithoutKeyId() throws Exception {
        String curve = "secp256r1";
        KeyPair rootKeyPair = ECKeyPairGenerator.generateKeyPair(curve);

        X509Certificate rootCertificate = createRootCertificate(rootKeyPair);

        KeyPair raKeyPair = ECKeyPairGenerator.generateKeyPair(curve);
        X509Certificate raCertificate = createRaCertificateNoKeyId(raKeyPair, rootCertificate, rootKeyPair.getPrivate());

        List<X509Certificate> certificates = new java.util.ArrayList<>();
        certificates.add(raCertificate);
        certificates.add(rootCertificate);
        return certificates;
        
    }
    
    
    public static List<X509Certificate> createCertificatePathOutsideValidityPeriod() throws Exception {
        String curve = "secp256r1";
        KeyPair rootKeyPair = ECKeyPairGenerator.generateKeyPair(curve);
        X509Certificate rootCertificate = createRootCertificate(rootKeyPair);
        KeyPair raKeyPair = ECKeyPairGenerator.generateKeyPair(curve);
        X509Certificate raCertificate = createRaCertificateOutsideValidityPeriod(raKeyPair, rootCertificate, rootKeyPair.getPrivate());

        List<X509Certificate> certificates = new java.util.ArrayList<>();
        certificates.add(raCertificate);
        certificates.add(rootCertificate);
        return certificates;
    }


    public static List<X509Certificate> createCertificatePathNoTrustAnchor() throws Exception {
        String curve = "secp256r1";
        KeyPair rootKeyPair = ECKeyPairGenerator.generateKeyPair(curve);
        X509Certificate rootCertificate = createRootCertificate(rootKeyPair);
        KeyPair wrongRootKeyPair = ECKeyPairGenerator.generateKeyPair(curve);
        X509Certificate wrongRootCertificate = createWrongRootCertificate(rootKeyPair);
        KeyPair raKeyPair = ECKeyPairGenerator.generateKeyPair(curve);
        X509Certificate raCertificate = createRaCertificateUnknownIssuer(raKeyPair, wrongRootCertificate, wrongRootKeyPair.getPrivate());

        List<X509Certificate> certificates = new java.util.ArrayList<>();
        certificates.add(raCertificate);
        certificates.add(rootCertificate);
        return certificates;
    }

    public static List<X509Certificate> createCertificatePathBadSignature() throws Exception {

        String curve = "secp256r1";
        KeyPair rootKeyPair = ECKeyPairGenerator.generateKeyPair(curve);
        X509Certificate rootCertificate = createRootCertificate(rootKeyPair);
        KeyPair raKeyPair = ECKeyPairGenerator.generateKeyPair(curve);
        X509Certificate raCertificate = createRaCertificateOutsideValidityPeriod(raKeyPair, rootCertificate, rootKeyPair.getPrivate());

        List<X509Certificate> certificates = new java.util.ArrayList<>();
        certificates.add(raCertificate);
        certificates.add(rootCertificate);
        return certificates;
    }

    public static List<X509Certificate> createCertificatePathCriticalPolicyOid() throws Exception {
        String curve = "secp256r1";
        KeyPair rootKeyPair = ECKeyPairGenerator.generateKeyPair(curve);
        X509Certificate rootCertificate = createRootCertificate(rootKeyPair);
        KeyPair raKeyPair = ECKeyPairGenerator.generateKeyPair(curve);
        X509Certificate raCertificate = createRaCertificateCriticalPolicyOid(raKeyPair, rootCertificate, rootKeyPair.getPrivate());

        List<X509Certificate> certificates = new java.util.ArrayList<>();
        certificates.add(raCertificate);
        certificates.add(rootCertificate);
        return certificates;
    }

    
    public static List<X509Certificate> createCertificatePathOutsideParentValidityPeriod() throws Exception {
        String curve = "secp256r1";
        KeyPair rootKeyPair = ECKeyPairGenerator.generateKeyPair(curve);
        X509Certificate rootCertificate = createRootCertificate(rootKeyPair);
        KeyPair raKeyPair = ECKeyPairGenerator.generateKeyPair(curve);
        X509Certificate raCertificate = createRaCertificateOutsideParentValidityPeriod(raKeyPair, rootCertificate, rootKeyPair.getPrivate());

        List<X509Certificate> certificates = new java.util.ArrayList<>();
        certificates.add(raCertificate);
        certificates.add(rootCertificate);
        return certificates;
    }

    public static X509Certificate createRaCertificate(KeyPair dsKeyPair, X509Certificate issuerCert,
            PrivateKey issuerPrivateKey) throws Exception {
        DataMaterial data = new RaDataMaterial();
        CertificateMaterial certData = new RaCertificateMaterial();
        KeyMaterial keyData = new RaKeyMaterial(issuerCert, dsKeyPair, issuerPrivateKey);
        return CertificateGenerator.generateCertificate(data, certData, keyData);
    }

    public static X509Certificate createRaCertificateOutsideValidityPeriod(KeyPair dsKeyPair, X509Certificate issuerCert,
            PrivateKey issuerPrivateKey) throws Exception {
        DataMaterial data = new RaDataMaterial();

        // just a test if a quick override works
        CertificateMaterial certData = new RaCertificateMaterial() {
            @Override
            public Date startDate() {
                return new Date(Instant.now().plus(Period.of(0, 0, 365)).toEpochMilli());
            }
        };
        KeyMaterial keyData = new RaKeyMaterial(issuerCert, dsKeyPair, issuerPrivateKey);
        return CertificateGenerator.generateCertificate(data, certData, keyData);
    }

    public static X509Certificate createRaCertificateOutsideParentValidityPeriod(KeyPair dsKeyPair, X509Certificate issuerCert,
            PrivateKey issuerPrivateKey) throws Exception {
        DataMaterial data = new RaDataMaterial();

        // just a test if a quick override works
        CertificateMaterial certData = new RaCertificateMaterial() {
            @Override
            public Date startDate() {
                // NOTE assumes that the root starts today
                return new Date(Instant.now().minus(Period.of(0, 0, 365)).toEpochMilli());
            }
        };
        KeyMaterial keyData = new RaKeyMaterial(issuerCert, dsKeyPair, issuerPrivateKey);
        return CertificateGenerator.generateCertificate(data, certData, keyData);
    }
    
    public static X509Certificate createRaCertificateBadSignature(KeyPair dsKeyPair, X509Certificate issuerCert,
            PrivateKey issuerPrivateKey) throws Exception {
        DataMaterial data = new RaDataMaterial();
        CertificateMaterial certData = new RaCertificateMaterial();
        KeyMaterial keyData = new RaKeyMaterial(issuerCert, dsKeyPair, issuerPrivateKey);
        X509Certificate raCertificate = CertificateGenerator.generateCertificate(data, certData, keyData);
        // alter(raCertificate);
        byte[] raCertificateData = raCertificate.getEncoded();
        // adding 1 to last byte of calculated signature
        raCertificateData[raCertificateData.length - 1]++;
        CertificateFactory certFactory = CertificateFactory.getInstance("X509");
        return (X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(raCertificateData));
    }
    
    public static X509Certificate createRaCertificateNoKeyId(KeyPair dsKeyPair, X509Certificate issuerCert,
            PrivateKey issuerPrivateKey) throws Exception {
        DataMaterial data = new RaDataMaterial();

        // just a test if a quick override works
        CertificateMaterial certData = new RaCertificateMaterial() {
            @Override
            public boolean includeKeyIds() {
                return false;
            }
        };
        KeyMaterial keyData = new RaKeyMaterial(issuerCert, dsKeyPair, issuerPrivateKey);
        return CertificateGenerator.generateCertificate(data, certData, keyData);
    }
    
    public static X509Certificate createRaCertificateUnknownIssuer(KeyPair dsKeyPair, X509Certificate wrongIssuerCert,
            PrivateKey issuerPrivateKey) throws Exception {
        DataMaterial data = new RaDataMaterial();

        CertificateMaterial certData = new RaCertificateMaterial();
        KeyMaterial keyData = new RaKeyMaterial(wrongIssuerCert, dsKeyPair, issuerPrivateKey);
        return CertificateGenerator.generateCertificate(data, certData, keyData);
    }

    public static X509Certificate createRaCertificateCriticalPolicyOid(KeyPair dsKeyPair, X509Certificate wrongIssuerCert,
            PrivateKey issuerPrivateKey) throws Exception {
        DataMaterial data = new RaDataMaterial();
        CertificateMaterial certData = new RaCertificateMaterial() {
            @Override
            public List<String> criticalPolicyOids() {
                List<String> policyOids = new java.util.ArrayList<>();
                policyOids.add("1.2.3");
                return policyOids;
            }
            
            @Override
            public List<String> extendedKeyUsage() {
                // 
                List<String> keyUsages = new java.util.ArrayList<>();
                keyUsages.add(TIME_STAMP_OID);
                return keyUsages;
            }
        };
        KeyMaterial keyData = new RaKeyMaterial(wrongIssuerCert, dsKeyPair, issuerPrivateKey);
        return CertificateGenerator.generateCertificate(data, certData, keyData);
    }
    
    public static X509Certificate createRootCertificate(KeyPair iacaKeyPair) throws Exception {
        final String dn = "CN=Google TEST Root mDL, C=UT";
    
        DataMaterial data = new RootDataMaterial(dn);
    
        CertificateMaterial certData = new RootCertificateMaterial();
    
        KeyMaterial keyData = new RootKeyMaterial(iacaKeyPair);
    
        return CertificateGenerator.generateCertificate(data, certData, keyData);
    }

    public static X509Certificate createWrongRootCertificate(KeyPair iacaKeyPair) throws Exception {
        final String dn = "CN=Wrong Google TEST Root mDL, C=UT";
    
        DataMaterial data = new RootDataMaterial(dn) {
            @Override
            public String issuerDN() {
                return dn;
            }
        };
    
        CertificateMaterial certData = new RootCertificateMaterial();
        KeyMaterial keyData = new RootKeyMaterial(iacaKeyPair);
        return CertificateGenerator.generateCertificate(data, certData, keyData);
    }
}
