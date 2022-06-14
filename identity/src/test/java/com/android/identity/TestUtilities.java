/*
 * Copyright 2021 The Android Open Source Project
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

package com.android.identity;

import androidx.annotation.NonNull;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public class TestUtilities {

    private TestUtilities() {}

    static @NonNull X509Certificate generateSelfSignedCert(KeyPair keyPair) {
        try {
            final Date notBefore = Date.from(Instant.now());
            final Date notAfter = Date.from(Instant.now().plus(Duration.ofDays(30)));
            final X500Name subjectIssuerName = new X500Name("CN=test");
            final ContentSigner signer;
            signer = new JcaContentSignerBuilder("SHA256WithECDSA")
                .build(keyPair.getPrivate());
            final X509CertificateHolder certHolder =
                new JcaX509v3CertificateBuilder(
                    subjectIssuerName,
                    BigInteger.valueOf(10101),
                    notBefore,
                    notAfter,
                    subjectIssuerName,
                    keyPair.getPublic())
                    .addExtension(Extension.basicConstraints, true, new BasicConstraints(true))
                    .build(signer);
            return new JcaX509CertificateConverter()
                .setProvider(new BouncyCastleProvider())
                .getCertificate(certHolder);
        } catch (OperatorCreationException | CertIOException | CertificateException e) {
            throw new IllegalStateException("Error generating self-signed certificate", e);
        }
    }
}