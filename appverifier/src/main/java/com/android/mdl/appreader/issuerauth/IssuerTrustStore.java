package com.android.mdl.appreader.issuerauth;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStoreException;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.security.cert.PKIXCertPathChecker;
import java.security.cert.PKIXCertPathValidatorResult;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Interface that defines a trust manager, used to check the validity of a
 * document signer and the associated certificate chain.
 * <p>
 * Note that each document type should have a different trust manager; this
 * trust manager is selected by OID in the DS certificate. These trust managers
 * should have a specific TrustStore for each certificate and may implement
 * specific checks required for the document type.
 */
public interface IssuerTrustStore {
    /**
     * This method creates a certification trust path by finding a certificate in the
     * trust store that is the issuer of a certificate in the certificate chain.
     * It returns <code>null</code> if no trusted certificate can be found.
     *
     * @param chain the chain, leaf certificate first, followed by any certificate that signed the previous certificate
     * @return the certification path in the same order, or null if no certification trust path could be created
     */
    List<X509Certificate> createCertificationTrustPath(List<X509Certificate> chain) throws CertPathBuilderException;

    /**
     * This method validates that the given certificate chain is a valid chain that
     * includes a document signer. Accepts a chain of certificates, starting with
     * the document signer certificate, followed by any intermediate certificates up
     * to the optional root certificate.
     * <p>
     * The trust manager should be initialized with a set of trusted certificates.
     * The chain is trusted if a trusted certificate can be found that has signed
     * any certificate in the chain. The trusted certificate itself will be
     * validated as well.
     *
     * @param chainToDocumentSigner the document signer, intermediate certificates
     *                              and optional root certificate
     * @return false if no trusted certificate could be found for the certificate chain or if the certificate chain is invalid for any reason
     */
    PKIXCertPathValidatorResult validateCertificationTrustPath(List<X509Certificate> chainToDocumentSigner, List<PKIXCertPathChecker> mdocAndCRLPathCheckers)
            throws KeyStoreException, CertPathValidatorException,
            InvalidAlgorithmParameterException, CertificateException;
}
