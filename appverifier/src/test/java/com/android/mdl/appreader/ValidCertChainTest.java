package com.android.mdl.appreader;

import com.android.mdl.appreader.issuerauth.SimpleIssuerTrustStore;

import junit.framework.AssertionFailedError;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Before;
import org.junit.Test;

import com.android.mdl.appreader.certutil.*;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.security.cert.PKIXCertPathValidatorResult;
import java.security.cert.PolicyNode;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;

public class ValidCertChainTest {

    @Before
    public void registerProviders() {
        BouncyCastleProvider bc = new BouncyCastleProvider();
        Security.addProvider(bc);
    }

    @Test
    public void testGoodCertificatePath() throws Exception {
        List<X509Certificate> certificatePath = DynamicallyGenGoogleReaderValidationCertificates.createGoodCertificatePath();
        SimpleIssuerTrustStore sits = createTrustStore(certificatePath.get(1));
        sits.validateCertificationTrustPath(certificatePath, Collections.emptyList());
    }

    @Test
    public void testCertificatePathWithoutKeyId() throws Exception {
        List<X509Certificate> certificatePath = DynamicallyGenGoogleReaderValidationCertificates.createCertificatePathWithoutKeyId();
        SimpleIssuerTrustStore sits = createTrustStore(certificatePath.get(1));
        sits.validateCertificationTrustPath(certificatePath, Collections.emptyList());
    }

    @Test
    public void testCertificatePathOutsideValidityPeriod() throws Exception {
        List<X509Certificate> certificatePath = DynamicallyGenGoogleReaderValidationCertificates.createCertificatePathOutsideValidityPeriod();
        SimpleIssuerTrustStore sits = createTrustStore(certificatePath.get(1));
        assertThrows(CertPathValidatorException.class, () -> sits.validateCertificationTrustPath(certificatePath, Collections.emptyList()));
    }

    @Test
    public void testCertificatePathNoTrustAnchor() throws Exception {
        List<X509Certificate> certificatePath = DynamicallyGenGoogleReaderValidationCertificates.createCertificatePathNoTrustAnchor();
        SimpleIssuerTrustStore sits = createTrustStore(certificatePath.get(1));
        assertThrows(CertPathValidatorException.class, () -> sits.validateCertificationTrustPath(certificatePath, Collections.emptyList()));
    }

    @Test
    public void testCertificatePathBadSignature() throws Exception {
        List<X509Certificate> certificatePath = DynamicallyGenGoogleReaderValidationCertificates.createCertificatePathBadSignature();
        SimpleIssuerTrustStore sits = createTrustStore(certificatePath.get(1));
        assertThrows(CertPathValidatorException.class,
                () -> sits.validateCertificationTrustPath(certificatePath, Collections.emptyList()));
    }

    @Test
    public void testCertificatePathUnknownCriticalExtension() throws Exception {
        List<X509Certificate> certificatePath = DynamicallyGenGoogleReaderValidationCertificates.createCertificatePathCriticalPolicyOid();
        SimpleIssuerTrustStore sits = createTrustStore(certificatePath.get(1));
//        Assertions.assertThrows(CertPathValidatorException.class,
//                () -> sits.validateCertificationTrustPath(certificatePath, List.of()));
        PKIXCertPathValidatorResult result = sits.validateCertificationTrustPath(certificatePath, Collections.emptyList());
        PolicyNode policyNode = result.getPolicyTree();
        System.out.println(policyNode);
        // Assertions.;
    }

    private SimpleIssuerTrustStore createTrustStore(X509Certificate rootCert)
            throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("root", rootCert);
        return new SimpleIssuerTrustStore(trustStore);
    }

    public interface Executable {
        void execute() throws Exception;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> T assertThrows(Class<T> expectedType, Executable executable) {

        try {
            executable.execute();
        }
        catch (Throwable actualException) {
            if (expectedType.isInstance(actualException)) {
                return (T) actualException;
            }
            else {
                throw new AssertionFailedError(String.format("Unexpected exception: %s", actualException));
            }
        }
        throw new AssertionFailedError(String.format("Expected %s to be thrown, but nothing was thrown.", expectedType));
    }
}
