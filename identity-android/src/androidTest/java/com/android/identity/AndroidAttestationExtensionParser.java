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

package com.android.identity;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.Optional;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1Boolean;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Enumerated;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Set;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.DEROctetString;

// This code is based on https://github.com/google/android-key-attestation

public class AndroidAttestationExtensionParser {
    private static final String KEY_DESCRIPTION_OID = "1.3.6.1.4.1.11129.2.1.17";

    public enum SecurityLevel {
        SOFTWARE,
        TRUSTED_ENVIRONMENT,
        STRONG_BOX
    }

    private static final int ATTESTATION_VERSION_INDEX = 0;
    private static final int ATTESTATION_SECURITY_LEVEL_INDEX = 1;
    private static final int KEYMASTER_VERSION_INDEX = 2;
    private static final int KEYMASTER_SECURITY_LEVEL_INDEX = 3;
    private static final int ATTESTATION_CHALLENGE_INDEX = 4;
    private static final int UNIQUE_ID_INDEX = 5;
    private static final int SW_ENFORCED_INDEX = 6;
    private static final int TEE_ENFORCED_INDEX = 7;

    // Some security values. The complete list is in this AOSP file:
    // hardware/libhardware/include/hardware/keymaster_defs.h
    private static final int KM_SECURITY_LEVEL_SOFTWARE = 0;
    private static final int KM_SECURITY_LEVEL_TRUSTED_ENVIRONMENT = 1;
    private static final int KM_SECURITY_LEVEL_STRONG_BOX = 2;

    private int attestationVersion;
    private SecurityLevel attestationSecurityLevel;
    private int keymasterVersion;
    private SecurityLevel keymasterSecurityLevel;
    private byte[] attestationChallenge;
    private byte[] uniqueId;

    private Map<Integer, ASN1Primitive> softwareEnforcedAuthorizations;
    private Map<Integer, ASN1Primitive> teeEnforcedAuthorizations;

    public int getAttestationVersion() {
        return attestationVersion;
    }

    public SecurityLevel getAttestationSecurityLevel() {
        return attestationSecurityLevel;
    }

    public int getKeymasterVersion() {
        return keymasterVersion;
    }

    public SecurityLevel getKeymasterSecurityLevel() {
        return attestationSecurityLevel;
    }

    public byte[] getAttestationChallenge() {
        return attestationChallenge;
    }

    public byte[] getUniqueId() {
        return uniqueId;
    }

    public Set<Integer> getSoftwareEnforcedAuthorizationTags() {
        return softwareEnforcedAuthorizations.keySet();
    }

    public Set<Integer> getTeeEnforcedAuthorizationTags() {
        return teeEnforcedAuthorizations.keySet();
    }

    private static ASN1Primitive findAuthorizationListEntry(
            Map<Integer, ASN1Primitive> authorizationMap, int tag) {
        return authorizationMap.getOrDefault(tag, null);
    }

    public Optional<Integer> getSoftwareAuthorizationInteger(int tag) {
        ASN1Primitive entry = findAuthorizationListEntry(softwareEnforcedAuthorizations, tag);
        return Optional.ofNullable(entry).map(AndroidAttestationExtensionParser::getIntegerFromAsn1);
    }

    public Optional<Long> getSoftwareAuthorizationLong(int tag) {
        ASN1Primitive entry = findAuthorizationListEntry(softwareEnforcedAuthorizations, tag);
        return Optional.ofNullable(entry).map(AndroidAttestationExtensionParser::getLongFromAsn1);
    }


    public Optional<Integer> getTeeAuthorizationInteger(int tag) {
        ASN1Primitive entry = findAuthorizationListEntry(teeEnforcedAuthorizations, tag);
        return Optional.ofNullable(entry).map(AndroidAttestationExtensionParser::getIntegerFromAsn1);
    }
    public boolean getSoftwareAuthorizationBoolean(int tag) {
        ASN1Primitive entry = findAuthorizationListEntry(softwareEnforcedAuthorizations, tag);
        return entry != null;
    }

    public boolean getTeeAuthorizationBoolean(int tag) {
        ASN1Primitive entry = findAuthorizationListEntry(teeEnforcedAuthorizations, tag);
        return entry != null;
    }

    public Optional<byte[]> getSoftwareAuthorizationByteString(int tag) {
        ASN1OctetString entry = (ASN1OctetString) findAuthorizationListEntry(softwareEnforcedAuthorizations, tag);
        return Optional.ofNullable(entry).map(ASN1OctetString::getOctets);
    }

    public Optional<byte[]> getTeeAuthorizationByteString(int tag) {
        ASN1OctetString entry = (ASN1OctetString) findAuthorizationListEntry(teeEnforcedAuthorizations, tag);
        return Optional.ofNullable(entry).map(ASN1OctetString::getOctets);
    }

    private static boolean getBooleanFromAsn1(ASN1Encodable asn1Value) {
        if (asn1Value instanceof ASN1Boolean) {
            return ((ASN1Boolean) asn1Value).isTrue();
        } else {
            throw new RuntimeException(
                    "Boolean value expected; found " + asn1Value.getClass().getName() + " instead.");
        }
    }

    private static int getIntegerFromAsn1(ASN1Encodable asn1Value) {
        if (asn1Value instanceof ASN1Integer) {
            return ((ASN1Integer) asn1Value).getValue().intValue();
        } else if (asn1Value instanceof ASN1Enumerated) {
            return ((ASN1Enumerated) asn1Value).getValue().intValue();
        } else {
            throw new IllegalArgumentException(
                    "Integer value expected; found " + asn1Value.getClass().getName() + " instead.");
        }
    }

    private static long getLongFromAsn1(ASN1Encodable asn1Value) {
        if (asn1Value instanceof ASN1Integer) {
            return ((ASN1Integer) asn1Value).getValue().longValue();
        } else if (asn1Value instanceof ASN1Enumerated) {
            return ((ASN1Enumerated) asn1Value).getValue().longValue();
        } else {
            throw new IllegalArgumentException(
                    "Integer value expected; found " + asn1Value.getClass().getName() + " instead.");
        }
    }

    private static Map<Integer, ASN1Primitive> getAuthorizationMap(
            ASN1Encodable[] authorizationList) {
        Map<Integer, ASN1Primitive> authorizationMap = new HashMap<>();
        for (ASN1Encodable entry : authorizationList) {
            ASN1TaggedObject taggedEntry = (ASN1TaggedObject) entry;
            authorizationMap.put(taggedEntry.getTagNo(), taggedEntry.getObject());
        }
        return authorizationMap;
    }

    public AndroidAttestationExtensionParser(X509Certificate cert) throws IOException {
        byte[] attestationExtensionBytes = cert.getExtensionValue(KEY_DESCRIPTION_OID);
        if (attestationExtensionBytes == null || attestationExtensionBytes.length == 0) {
            throw new IllegalArgumentException("Couldn't find keystore attestation extension.");
        }

        ASN1Sequence seq;
        try (ASN1InputStream asn1InputStream = new ASN1InputStream(attestationExtensionBytes)) {
            // The extension contains one object, a sequence, in the
            // Distinguished Encoding Rules (DER)-encoded form. Get the DER
            // bytes.
            byte[] derSequenceBytes = ((ASN1OctetString) asn1InputStream.readObject()).getOctets();
            // Decode the bytes as an ASN1 sequence object.
            try (ASN1InputStream seqInputStream = new ASN1InputStream(derSequenceBytes)) {
                seq = (ASN1Sequence) seqInputStream.readObject();
            }
        }

        this.attestationVersion = getIntegerFromAsn1(seq.getObjectAt(ATTESTATION_VERSION_INDEX));
        this.attestationSecurityLevel =
                securityLevelToEnum(getIntegerFromAsn1(
                        seq.getObjectAt(ATTESTATION_SECURITY_LEVEL_INDEX)));
        this.keymasterVersion = getIntegerFromAsn1(seq.getObjectAt(KEYMASTER_VERSION_INDEX));
        this.keymasterSecurityLevel = securityLevelToEnum(
                getIntegerFromAsn1(seq.getObjectAt(KEYMASTER_SECURITY_LEVEL_INDEX)));
        this.attestationChallenge =
                ((ASN1OctetString) seq.getObjectAt(ATTESTATION_CHALLENGE_INDEX)).getOctets();
        this.uniqueId = ((ASN1OctetString) seq.getObjectAt(UNIQUE_ID_INDEX)).getOctets();

        this.softwareEnforcedAuthorizations = getAuthorizationMap(
                ((ASN1Sequence) seq.getObjectAt(SW_ENFORCED_INDEX)).toArray());

        this.teeEnforcedAuthorizations = getAuthorizationMap(
                ((ASN1Sequence) seq.getObjectAt(TEE_ENFORCED_INDEX)).toArray());
    }

    private static SecurityLevel securityLevelToEnum(int securityLevel) {
        switch (securityLevel) {
            case KM_SECURITY_LEVEL_SOFTWARE:
                return SecurityLevel.SOFTWARE;
            case KM_SECURITY_LEVEL_TRUSTED_ENVIRONMENT:
                return SecurityLevel.TRUSTED_ENVIRONMENT;
            case KM_SECURITY_LEVEL_STRONG_BOX:
                return SecurityLevel.STRONG_BOX;
            default:
                throw new IllegalArgumentException("Invalid security level.");
        }
    }
}
