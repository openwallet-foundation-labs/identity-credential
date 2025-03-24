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
package com.android.javacard.mdl;

import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.Util;
import javacard.security.AESKey;
import javacard.security.CryptoException;
import javacard.security.ECPrivateKey;
import javacard.security.ECPublicKey;
import javacard.security.KeyBuilder;
import javacard.security.KeyPair;
import javacard.security.MessageDigest;
import javacard.security.RandomData;
import javacard.security.Signature;
import javacardx.crypto.AEADCipher;
import javacardx.crypto.Cipher;

/**
 * This class implements the cryptographic functions required to comply with ISO 18013-5
 * specifications. Mostly it uses standard JavaCard libraries and tt implements hkdf function which
 * is not supported by Javacard library. Note: The current implementation only support EC256 ECDSA
 * signature validation. //TODO support ES384 and ES512 ECDSA signature validation.
 */
public class SEProvider {

  public static final short ES256 = 1;
  public static final short ES384 = 2;
  public static final short ES512 = 3;
  public static final byte SIGNING_OPTION_FK= 0x01;
  public static final byte SIGNING_OPTION_CASD = 0x02;
  public static final short DEFAULT_MAX_SCRATCH_SIZE = 512;
  public static final short DEFAULT_MAX_BUFFER_SIZE = 2048;
  public static final boolean DEFAULT_AES_GCM_BUFFERING = true;
  public static final byte DEFAULT_SIGNING_OPTION = SIGNING_OPTION_FK;
  public static final byte AES_GCM_NONCE_LENGTH = (byte) 12;
  public static final byte AES_GCM_TAG_LENGTH = 16;
  public static final short SIGNING_CERT_MAX_SIZE = 512;
  // parameters offset
  public static final short PARAM_OFF_AES_GCM_OUT_LEN = 0;
  public static final short PARAM_OFF_PURPOSE = 1;
  public static final short PARAMS_COUNT = 2;
  final byte secp256r1_H = 1;
  final byte secp384r1_H = 1;
  final byte secp521r1_H = 1;

  // final variables
  // --------------------------------------------------------------
  // P-256 Curve Parameters
  public final short MAX_SCRATCH_SIZE;
  public final short MAX_BUFFER_SIZE;
  public final boolean AES_GCM_BUFFERING;
  public final byte SIGNING_OPTION;
  final byte[] secp256r1_P;
  final byte[] secp256r1_A;
  final byte[] secp256r1_B;
  final byte[] secp256r1_S;
  // Uncompressed form
  final byte[] secp256r1_UCG;
  final byte[] secp256r1_N;
  // secp384
  final byte[] secp384r1_P;
  final byte[] secp384r1_A;
  final byte[] secp384r1_B;
  final byte[] secp384r1_S;
  // Uncompressed form
  final byte[] secp384r1_UCG;
  final byte[] secp384r1_N;
  // secp521
  final byte[] secp521r1_P;
  final byte[] secp521r1_A;
  final byte[] secp521r1_B;
  final byte[] secp521r1_S;
  // Uncompressed form
  final byte[] secp521r1_UCG;
  final byte[] secp521r1_N;
  private final Signature signerWithSha256;
  private final Signature signerWithSha384;
  private final Signature signerWithSha512;
  private final KeyPair ec256KeyPair;
  private final KeyPair ec384KeyPair;
  private final KeyPair ec521KeyPair;
  private final RandomData mRng;
  // --------------------------------------------------------------
  private final short[] parameters;
  private final byte[] tag;
  private final X509CertHandler mX509CertHandler;
  private AEADCipher aesGcmCipher;

  public SEProvider(byte[] buf, short start, short len) {
    if (len > 0) {
      if (len != 6) {
        ISOException.throwIt(ISO7816.SW_UNKNOWN);
      }
      AES_GCM_BUFFERING = buf[start] == 1;
      start++;
      SIGNING_OPTION = buf[start];
      if (!isSigningOptionValid(SIGNING_OPTION)) {
        ISOException.throwIt(ISO7816.SW_DATA_INVALID);
      }
      start++;
      MAX_BUFFER_SIZE = Util.getShort(buf, start);
      start += 2;
      MAX_SCRATCH_SIZE = Util.getShort(buf, start);
    } else {
      AES_GCM_BUFFERING = DEFAULT_AES_GCM_BUFFERING;
      SIGNING_OPTION = DEFAULT_SIGNING_OPTION;
      MAX_BUFFER_SIZE = DEFAULT_MAX_BUFFER_SIZE;
      MAX_SCRATCH_SIZE = DEFAULT_MAX_SCRATCH_SIZE;
    }

    secp521r1_P =
        new byte[] {
            (byte) 0x01, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff
        };
    secp521r1_A =
        new byte[] {
            (byte) 0x01, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xfc
        };
    secp521r1_B =
        new byte[] {
            (byte) 0x51, (byte) 0x95, (byte) 0x3e, (byte) 0xb9, (byte) 0x61,
            (byte) 0x8e, (byte) 0x1c, (byte) 0x9a, (byte) 0x1f, (byte) 0x92, (byte) 0x9a,
            (byte) 0x21, (byte) 0xa0, (byte) 0xb6, (byte) 0x85, (byte) 0x40, (byte) 0xee,
            (byte) 0xa2, (byte) 0xda, (byte) 0x72, (byte) 0x5b, (byte) 0x99, (byte) 0xb3,
            (byte) 0x15, (byte) 0xf3, (byte) 0xb8, (byte) 0xb4, (byte) 0x89, (byte) 0x91,
            (byte) 0x8e, (byte) 0xf1, (byte) 0x09, (byte) 0xe1, (byte) 0x56, (byte) 0x19,
            (byte) 0x39, (byte) 0x51, (byte) 0xec, (byte) 0x7e, (byte) 0x93, (byte) 0x7b,
            (byte) 0x16, (byte) 0x52, (byte) 0xc0, (byte) 0xbd, (byte) 0x3b, (byte) 0xb1,
            (byte) 0xbf, (byte) 0x07, (byte) 0x35, (byte) 0x73, (byte) 0xdf, (byte) 0x88,
            (byte) 0x3d, (byte) 0x2c, (byte) 0x34, (byte) 0xf1, (byte) 0xef, (byte) 0x45,
            (byte) 0x1f, (byte) 0xd4, (byte) 0x6b, (byte) 0x50, (byte) 0x3f, (byte) 0x00
        };
    secp521r1_S =
        new byte[] {
            (byte) 0xd0, (byte) 0x9e, (byte) 0x88, (byte) 0x00, (byte) 0x29, (byte) 0x1c,
            (byte) 0xb8, (byte) 0x53, (byte) 0x96, (byte) 0xcc, (byte) 0x67, (byte) 0x17,
            (byte) 0x39, (byte) 0x32, (byte) 0x84, (byte) 0xaa, (byte) 0xa0, (byte) 0xda,
            (byte) 0x64, (byte) 0xba
        };
    // Uncompressed form
    secp521r1_UCG =
        new byte[] {
            (byte) 0x04, (byte) 0x00, (byte) 0xc6, (byte) 0x85, (byte) 0x8e, (byte) 0x06,
            (byte) 0xb7, (byte) 0x04, (byte) 0x04, (byte) 0xe9, (byte) 0xcd, (byte) 0x9e,
            (byte) 0x3e, (byte) 0xcb, (byte) 0x66, (byte) 0x23, (byte) 0x95, (byte) 0xb4,
            (byte) 0x42, (byte) 0x9c, (byte) 0x64, (byte) 0x81, (byte) 0x39, (byte) 0x05,
            (byte) 0x3f, (byte) 0xb5, (byte) 0x21, (byte) 0xf8, (byte) 0x28, (byte) 0xaf,
            (byte) 0x60, (byte) 0x6b, (byte) 0x4d, (byte) 0x3d, (byte) 0xba, (byte) 0xa1,
            (byte) 0x4b, (byte) 0x5e, (byte) 0x77, (byte) 0xef, (byte) 0xe7, (byte) 0x59,
            (byte) 0x28, (byte) 0xfe, (byte) 0x1d, (byte) 0xc1, (byte) 0x27, (byte) 0xa2,
            (byte) 0xff, (byte) 0xa8, (byte) 0xde, (byte) 0x33, (byte) 0x48, (byte) 0xb3,
            (byte) 0xc1, (byte) 0x85, (byte) 0x6a, (byte) 0x42, (byte) 0x9b, (byte) 0xf9,
            (byte) 0x7e, (byte) 0x7e, (byte) 0x31, (byte) 0xc2, (byte) 0xe5, (byte) 0xbd,
            (byte) 0x66, (byte) 0x01, (byte) 0x18, (byte) 0x39, (byte) 0x29, (byte) 0x6a,
            (byte) 0x78, (byte) 0x9a, (byte) 0x3b, (byte) 0xc0, (byte) 0x04, (byte) 0x5c,
            (byte) 0x8a, (byte) 0x5f, (byte) 0xb4, (byte) 0x2c, (byte) 0x7d, (byte) 0x1b,
            (byte) 0xd9, (byte) 0x98, (byte) 0xf5, (byte) 0x44, (byte) 0x49, (byte) 0x57,
            (byte) 0x9b, (byte) 0x44, (byte) 0x68, (byte) 0x17, (byte) 0xaf, (byte) 0xbd,
            (byte) 0x17, (byte) 0x27, (byte) 0x3e, (byte) 0x66, (byte) 0x2c, (byte) 0x97,
            (byte) 0xee, (byte) 0x72, (byte) 0x99, (byte) 0x5e, (byte) 0xf4, (byte) 0x26,
            (byte) 0x40, (byte) 0xc5, (byte) 0x50, (byte) 0xb9, (byte) 0x01, (byte) 0x3f,
            (byte) 0xad, (byte) 0x07, (byte) 0x61, (byte) 0x35, (byte) 0x3c, (byte) 0x70,
            (byte) 0x86, (byte) 0xa2, (byte) 0x72, (byte) 0xc2, (byte) 0x40, (byte) 0x88,
            (byte) 0xbe, (byte) 0x94, (byte) 0x76, (byte) 0x9f, (byte) 0xd1, (byte) 0x66,
            (byte) 0x50
        };
    secp521r1_N =
        new byte[] {
            (byte) 0x01, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xfa, (byte) 0x51, (byte) 0x86,
            (byte) 0x87, (byte) 0x83, (byte) 0xbf, (byte) 0x2f, (byte) 0x96, (byte) 0x6b,
            (byte) 0x7f, (byte) 0xcc, (byte) 0x01, (byte) 0x48, (byte) 0xf7, (byte) 0x09,
            (byte) 0xa5, (byte) 0xd0, (byte) 0x3b, (byte) 0xb5, (byte) 0xc9, (byte) 0xb8,
            (byte) 0x89, (byte) 0x9c, (byte) 0x47, (byte) 0xae, (byte) 0xbb, (byte) 0x6f,
            (byte) 0xb7, (byte) 0x1e, (byte) 0x91, (byte) 0x38, (byte) 0x64, (byte) 0x09
        };
    //-----------------------------------------------------------------------------

    secp384r1_P =
        new byte[] {
          (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
          (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
          (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
          (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
          (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
          (byte) 0xff, (byte) 0xfe, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
          (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
          (byte) 0x00, (byte) 0x00, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff
        };

    secp384r1_A =
        new byte[] {
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xfe, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xfc
        };

    secp384r1_B =
        new byte[] {
            (byte) 0xb3, (byte) 0x31, (byte) 0x2f, (byte) 0xa7, (byte) 0xe2, (byte) 0x3e,
            (byte) 0xe7, (byte) 0xe4, (byte) 0x98, (byte) 0x8e, (byte) 0x05, (byte) 0x6b,
            (byte) 0xe3, (byte) 0xf8, (byte) 0x2d, (byte) 0x19, (byte) 0x18, (byte) 0x1d,
            (byte) 0x9c, (byte) 0x6e, (byte) 0xfe, (byte) 0x81, (byte) 0x41, (byte) 0x12,
            (byte) 0x03, (byte) 0x14, (byte) 0x08, (byte) 0x8f, (byte) 0x50, (byte) 0x13,
            (byte) 0x87, (byte) 0x5a, (byte) 0xc6, (byte) 0x56, (byte) 0x39, (byte) 0x8d,
            (byte) 0x8a, (byte) 0x2e, (byte) 0xd1, (byte) 0x9d, (byte) 0x2a, (byte) 0x85,
            (byte) 0xc8, (byte) 0xed, (byte) 0xd3, (byte) 0xec, (byte) 0x2a, (byte) 0xef
        };

    secp384r1_S =
        new byte[] {
            (byte) 0xa3, (byte) 0x35, (byte) 0x92, (byte) 0x6a, (byte) 0xa3, (byte) 0x19,
            (byte) 0xa2, (byte) 0x7a, (byte) 0x1d, (byte) 0x00, (byte) 0x89, (byte) 0x6a,
            (byte) 0x67, (byte) 0x73, (byte) 0xa4, (byte) 0x82, (byte) 0x7a, (byte) 0xcd,
            (byte) 0xac, (byte) 0x73
        };
    // Uncompressed form
    secp384r1_UCG =
        new byte[] {
            (byte) 0x04, (byte) 0xaa, (byte) 0x87, (byte) 0xca, (byte) 0x22, (byte) 0xbe,
            (byte) 0x8b, (byte) 0x05, (byte) 0x37, (byte) 0x8e, (byte) 0xb1, (byte) 0xc7,
            (byte) 0x1e, (byte) 0xf3, (byte) 0x20, (byte) 0xad, (byte) 0x74, (byte) 0x6e,
            (byte) 0x1d, (byte) 0x3b, (byte) 0x62, (byte) 0x8b, (byte) 0xa7, (byte) 0x9b,
            (byte) 0x98, (byte) 0x59, (byte) 0xf7, (byte) 0x41, (byte) 0xe0, (byte) 0x82,
            (byte) 0x54, (byte) 0x2a, (byte) 0x38, (byte) 0x55, (byte) 0x02, (byte) 0xf2,
            (byte) 0x5d, (byte) 0xbf, (byte) 0x55, (byte) 0x29, (byte) 0x6c, (byte) 0x3a,
            (byte) 0x54, (byte) 0x5e, (byte) 0x38, (byte) 0x72, (byte) 0x76, (byte) 0x0a,
            (byte) 0xb7, (byte) 0x36, (byte) 0x17, (byte) 0xde, (byte) 0x4a, (byte) 0x96,
            (byte) 0x26, (byte) 0x2c, (byte) 0x6f, (byte) 0x5d, (byte) 0x9e, (byte) 0x98,
            (byte) 0xbf, (byte) 0x92, (byte) 0x92, (byte) 0xdc, (byte) 0x29, (byte) 0xf8,
            (byte) 0xf4, (byte) 0x1d, (byte) 0xbd, (byte) 0x28, (byte) 0x9a, (byte) 0x14,
            (byte) 0x7c, (byte) 0xe9, (byte) 0xda, (byte) 0x31, (byte) 0x13, (byte) 0xb5,
            (byte) 0xf0, (byte) 0xb8, (byte) 0xc0, (byte) 0x0a, (byte) 0x60, (byte) 0xb1,
            (byte) 0xce, (byte) 0x1d, (byte) 0x7e, (byte) 0x81, (byte) 0x9d, (byte) 0x7a,
            (byte) 0x43, (byte) 0x1d, (byte) 0x7c, (byte) 0x90, (byte) 0xea, (byte) 0x0e,
            (byte) 0x5f
        };

    secp384r1_N =
        new byte[] {
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xc7, (byte) 0x63, (byte) 0x4d, (byte) 0x81, (byte) 0xf4, (byte) 0x37,
            (byte) 0x2d, (byte) 0xdf, (byte) 0x58, (byte) 0x1a, (byte) 0x0d, (byte) 0xb2,
            (byte) 0x48, (byte) 0xb0, (byte) 0xa7, (byte) 0x7a, (byte) 0xec, (byte) 0xec,
            (byte) 0x19, (byte) 0x6a, (byte) 0xcc, (byte) 0xc5, (byte) 0x29, (byte) 0x73
        };
    //-----------------------------------------------------------------------------------------

    secp256r1_P =
        new byte[] {
          (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0x00, (byte) 0x00,
          (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
          (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
          (byte) 0x00, (byte) 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
          (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
          (byte) 0xFF, (byte) 0xFF
        };

    secp256r1_A =
        new byte[] {
          (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0x00, (byte) 0x00,
          (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
          (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
          (byte) 0x00, (byte) 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
          (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
          (byte) 0xFF, (byte) 0xFC
        };

    secp256r1_B =
        new byte[] {
          (byte) 0x5A, (byte) 0xC6, (byte) 0x35, (byte) 0xD8, (byte) 0xAA, (byte) 0x3A,
          (byte) 0x93, (byte) 0xE7, (byte) 0xB3, (byte) 0xEB, (byte) 0xBD, (byte) 0x55,
          (byte) 0x76, (byte) 0x98, (byte) 0x86, (byte) 0xBC, (byte) 0x65, (byte) 0x1D,
          (byte) 0x06, (byte) 0xB0, (byte) 0xCC, (byte) 0x53, (byte) 0xB0, (byte) 0xF6,
          (byte) 0x3B, (byte) 0xCE, (byte) 0x3C, (byte) 0x3E, (byte) 0x27, (byte) 0xD2,
          (byte) 0x60, (byte) 0x4B
        };

    secp256r1_S =
        new byte[] {
          (byte) 0xC4, (byte) 0x9D, (byte) 0x36, (byte) 0x08, (byte) 0x86, (byte) 0xE7,
          (byte) 0x04, (byte) 0x93, (byte) 0x6A, (byte) 0x66, (byte) 0x78, (byte) 0xE1,
          (byte) 0x13, (byte) 0x9D, (byte) 0x26, (byte) 0xB7, (byte) 0x81, (byte) 0x9F,
          (byte) 0x7E, (byte) 0x90
        };

    // Uncompressed form
    secp256r1_UCG =
        new byte[] {
          (byte) 0x04, (byte) 0x6B, (byte) 0x17, (byte) 0xD1, (byte) 0xF2, (byte) 0xE1,
          (byte) 0x2C, (byte) 0x42, (byte) 0x47, (byte) 0xF8, (byte) 0xBC, (byte) 0xE6,
          (byte) 0xE5, (byte) 0x63, (byte) 0xA4, (byte) 0x40, (byte) 0xF2, (byte) 0x77,
          (byte) 0x03, (byte) 0x7D, (byte) 0x81, (byte) 0x2D, (byte) 0xEB, (byte) 0x33,
          (byte) 0xA0, (byte) 0xF4, (byte) 0xA1, (byte) 0x39, (byte) 0x45, (byte) 0xD8,
          (byte) 0x98, (byte) 0xC2, (byte) 0x96, (byte) 0x4F, (byte) 0xE3, (byte) 0x42,
          (byte) 0xE2, (byte) 0xFE, (byte) 0x1A, (byte) 0x7F, (byte) 0x9B, (byte) 0x8E,
          (byte) 0xE7, (byte) 0xEB, (byte) 0x4A, (byte) 0x7C, (byte) 0x0F, (byte) 0x9E,
          (byte) 0x16, (byte) 0x2B, (byte) 0xCE, (byte) 0x33, (byte) 0x57, (byte) 0x6B,
          (byte) 0x31, (byte) 0x5E, (byte) 0xCE, (byte) 0xCB, (byte) 0xB6, (byte) 0x40,
          (byte) 0x68, (byte) 0x37, (byte) 0xBF, (byte) 0x51, (byte) 0xF5
        };

    secp256r1_N =
        new byte[] {
          (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0x00, (byte) 0x00,
          (byte) 0x00, (byte) 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
          (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xBC, (byte) 0xE6,
          (byte) 0xFA, (byte) 0xAD, (byte) 0xA7, (byte) 0x17, (byte) 0x9E, (byte) 0x84,
          (byte) 0xF3, (byte) 0xB9, (byte) 0xCA, (byte) 0xC2, (byte) 0xFC, (byte) 0x63,
          (byte) 0x25, (byte) 0x51
        };
    parameters = JCSystem.makeTransientShortArray(PARAMS_COUNT, JCSystem.CLEAR_ON_RESET);
    // 2 bytes len and 16 bytes tag.
    tag = JCSystem.makeTransientByteArray((short) 18, JCSystem.CLEAR_ON_RESET);
    ec256KeyPair = new KeyPair(KeyPair.ALG_EC_FP, KeyBuilder.LENGTH_EC_FP_256);
    initECKey(ec256KeyPair, ES256);
    ec384KeyPair = new KeyPair(KeyPair.ALG_EC_FP, KeyBuilder.LENGTH_EC_FP_384);
    initECKey(ec384KeyPair, ES384);
    ec521KeyPair = new KeyPair(KeyPair.ALG_EC_FP, KeyBuilder.LENGTH_EC_FP_521);
    initECKey(ec521KeyPair, ES512);
    signerWithSha256 = Signature.getInstance(Signature.ALG_ECDSA_SHA_256, false);
    signerWithSha384 = Signature.getInstance(Signature.ALG_ECDSA_SHA_384, false);
    signerWithSha512 = Signature.getInstance(Signature.ALG_ECDSA_SHA_512, false);
    aesGcmCipher = (AEADCipher) Cipher.getInstance(AEADCipher.ALG_AES_GCM, false);
    mRng = RandomData.getInstance(RandomData.ALG_TRNG);
    mX509CertHandler = new X509CertHandler();
  }

  private boolean isSigningOptionValid(byte signingOption) {
    switch (signingOption) {
      case SIGNING_OPTION_FK:
      case SIGNING_OPTION_CASD:
        return true;
      default:
        return false;
    }
  }

  public void initECKey(KeyPair ecKeyPair) {
    ECPrivateKey privKey = (ECPrivateKey) ecKeyPair.getPrivate();
    ECPublicKey pubkey = (ECPublicKey) ecKeyPair.getPublic();
    // Default ES256
    initEcPublicKey(pubkey, ES256);
    initEcPrivateKey(privKey, ES256);
  }

  public void initECKey(KeyPair ecKeyPair, short curve) {
    ECPrivateKey privKey = (ECPrivateKey) ecKeyPair.getPrivate();
    ECPublicKey pubkey = (ECPublicKey) ecKeyPair.getPublic();
    initEcPublicKey(pubkey, curve);
    initEcPrivateKey(privKey, curve);
  }

  public void initEcPublicKey(ECPublicKey pubKey) {
    initEcPublicKey(pubKey, ES256);
  }

  public void initEcPublicKey(ECPublicKey pubKey, short curve) {
    switch (curve) {
      case ES256:
        pubKey.setFieldFP(secp256r1_P, (short) 0, (short) secp256r1_P.length);
        pubKey.setA(secp256r1_A, (short) 0, (short) secp256r1_A.length);
        pubKey.setB(secp256r1_B, (short) 0, (short) secp256r1_B.length);
        pubKey.setG(secp256r1_UCG, (short) 0, (short) secp256r1_UCG.length);
        pubKey.setK(secp256r1_H);
        pubKey.setR(secp256r1_N, (short) 0, (short) secp256r1_N.length);
        break;
      case ES384:
        pubKey.setFieldFP(secp384r1_P, (short) 0, (short) secp384r1_P.length);
        pubKey.setA(secp384r1_A, (short) 0, (short) secp384r1_A.length);
        pubKey.setB(secp384r1_B, (short) 0, (short) secp384r1_B.length);
        pubKey.setG(secp384r1_UCG, (short) 0, (short) secp384r1_UCG.length);
        pubKey.setK(secp384r1_H);
        pubKey.setR(secp384r1_N, (short) 0, (short) secp384r1_N.length);
        break;
      case ES512:
        pubKey.setFieldFP(secp521r1_P, (short) 0, (short) secp521r1_P.length);
        pubKey.setA(secp521r1_A, (short) 0, (short) secp521r1_A.length);
        pubKey.setB(secp521r1_B, (short) 0, (short) secp521r1_B.length);
        pubKey.setG(secp521r1_UCG, (short) 0, (short) secp521r1_UCG.length);
        pubKey.setK(secp521r1_H);
        pubKey.setR(secp521r1_N, (short) 0, (short) secp521r1_N.length);
        break;
      default:
        // Other curves not supported.
        break;
    }
  }

  public void initEcPrivateKey(ECPrivateKey privKey, short curve) {
    switch (curve) {
      case ES256:
        privKey.setFieldFP(secp256r1_P, (short) 0, (short) secp256r1_P.length);
        privKey.setA(secp256r1_A, (short) 0, (short) secp256r1_A.length);
        privKey.setB(secp256r1_B, (short) 0, (short) secp256r1_B.length);
        privKey.setG(secp256r1_UCG, (short) 0, (short) secp256r1_UCG.length);
        privKey.setK(secp256r1_H);
        privKey.setR(secp256r1_N, (short) 0, (short) secp256r1_N.length);
        break;
      case ES384:
        privKey.setFieldFP(secp384r1_P, (short) 0, (short) secp384r1_P.length);
        privKey.setA(secp384r1_A, (short) 0, (short) secp384r1_A.length);
        privKey.setB(secp384r1_B, (short) 0, (short) secp384r1_B.length);
        privKey.setG(secp384r1_UCG, (short) 0, (short) secp384r1_UCG.length);
        privKey.setK(secp384r1_H);
        privKey.setR(secp384r1_N, (short) 0, (short) secp384r1_N.length);
        break;
      case ES512:
        privKey.setFieldFP(secp521r1_P, (short) 0, (short) secp521r1_P.length);
        privKey.setA(secp521r1_A, (short) 0, (short) secp521r1_A.length);
        privKey.setB(secp521r1_B, (short) 0, (short) secp521r1_B.length);
        privKey.setG(secp521r1_UCG, (short) 0, (short) secp521r1_UCG.length);
        privKey.setK(secp521r1_H);
        privKey.setR(secp521r1_N, (short) 0, (short) secp521r1_N.length);
        break;
      default:
        // Other curves not supported.
        break;
    }

  }

  public short ecSign256(
      ECPrivateKey key,
      byte[] inputDataBuf,
      short inputDataStart,
      short inputDataLength,
      byte[] outputDataBuf,
      short outputDataStart) {
    signerWithSha256.init(key, Signature.MODE_SIGN);
    return signerWithSha256.sign(
        inputDataBuf, inputDataStart, inputDataLength, outputDataBuf, outputDataStart);
  }

  private MessageDigest getMessageDigest256Instance() {
    return MessageDigest.getInstance(MessageDigest.ALG_SHA3_256, false);
  }

  public short digest(byte[] buffer, short start, short len, byte[] outBuf, short index) {
    return getMessageDigest256Instance().doFinal(buffer, start, len, outBuf, index);
  }

  public void beginAesGcmOperation(
      AESKey key,
      boolean encrypt,
      byte[] nonce,
      short start,
      short len,
      byte[] authData,
      short authDataStart,
      short authDataLen) {
    parameters[PARAM_OFF_PURPOSE] = 0;
    parameters[PARAM_OFF_AES_GCM_OUT_LEN] = 0;
    Util.arrayFillNonAtomic(tag, (short) 0, (short) tag.length, (byte) 0);
    // Create the cipher
    byte mode = encrypt ? Cipher.MODE_ENCRYPT : Cipher.MODE_DECRYPT;
    initCipher(key, nonce, start, len, authData, authDataStart, authDataLen, mode);
    parameters[PARAM_OFF_PURPOSE] = mode;
  }

  public short bufferData(
      byte[] inData,
      short inDataStart,
      short inDatalen,
      byte mode,
      byte[] scratchpad,
      short scratchpadOff,
      short scratchpadLen) {
    if (!AES_GCM_BUFFERING || mode == Cipher.MODE_ENCRYPT || inDatalen == 0) {
      return inDatalen;
    }
    short totalLen = 0;
    short tagLen = Util.getShort(tag, (short) 0);
    short tagOffset = 2;
    // Copy tag and input to scratch pad and then copy last combined 16 bytes from scratch pad and
    // input to tag.
    Util.arrayCopyNonAtomic(tag, tagOffset, scratchpad, scratchpadOff, tagLen);
    totalLen += tagLen;
    short copyBytes = 0;
    short inputRemainLen = 0;
    if (scratchpadLen >= inDatalen) {
      copyBytes = inDatalen;
    } else {
      copyBytes = scratchpadLen;
      inputRemainLen = (short) (inDatalen - scratchpadLen);
    }
    Util.arrayCopyNonAtomic(
        inData, inDataStart, scratchpad, (short) (scratchpadOff + tagLen), copyBytes);
    totalLen += copyBytes;
    if (totalLen <= 16) {
      Util.arrayCopyNonAtomic(scratchpad, scratchpadOff, tag, tagOffset, totalLen);
      Util.setShort(tag, (short) 0, totalLen);
      totalLen = 0;
    } else {
      short scratchpadToTagCopyLen = (short) (AES_GCM_TAG_LENGTH - inputRemainLen);
      short scratchpadToTagCopyOff = (short) (scratchpadOff + totalLen - scratchpadToTagCopyLen);
      Util.arrayCopyNonAtomic(
          scratchpad, scratchpadToTagCopyOff, tag, tagOffset, scratchpadToTagCopyLen);
      if (inputRemainLen > 0) {
        tagOffset += scratchpadToTagCopyLen;
        Util.arrayCopyNonAtomic(
            inData,
            (short) (inDataStart + inDatalen - inputRemainLen),
            tag,
            tagOffset,
            inputRemainLen);
      }
      totalLen -= scratchpadToTagCopyLen;
      Util.arrayCopyNonAtomic(scratchpad, scratchpadOff, inData, inDataStart, totalLen);
      Util.setShort(tag, (short) 0, AES_GCM_TAG_LENGTH);
    }
    return totalLen;
  }

  public short doAesGcmOperation(
      byte[] inData,
      short inDataStart,
      short inDataLen,
      byte[] outData,
      short outDataStart,
      boolean justUpdate) {
    short len = 0;
    short mode = parameters[PARAM_OFF_PURPOSE];
    if (!justUpdate) {
      parameters[PARAM_OFF_PURPOSE] = 0;
      parameters[PARAM_OFF_AES_GCM_OUT_LEN] = 0;
      if (mode == Cipher.MODE_ENCRYPT) {
        len = aesGcmCipher.doFinal(inData, inDataStart, inDataLen, outData, outDataStart);
        len += aesGcmCipher.retrieveTag(outData, (short) (outDataStart + len), AES_GCM_TAG_LENGTH);
      } else {
        short tagLen = Util.getShort(tag, (short) 0);
        len = aesGcmCipher.doFinal(inData, inDataStart, inDataLen, outData, outDataStart);
        boolean verified = aesGcmCipher.verifyTag(tag, (short) 2, tagLen, AES_GCM_TAG_LENGTH);
        if (!verified) {
          ISOException.throwIt(ISO7816.SW_DATA_INVALID);
        }
        Util.setShort(tag, (short) 2, (short) 0);
      }
    } else {
      try {
        inDataLen =
            bufferData(
                inData,
                inDataStart,
                inDataLen,
                (byte) mode,
                outData,
                outDataStart,
                (short) outData.length);
        len = aesGcmCipher.update(inData, inDataStart, inDataLen, outData, outDataStart);
      } catch (CryptoException e) {
        ISOException.throwIt(e.getReason());
      }
      parameters[PARAM_OFF_AES_GCM_OUT_LEN] += (short) (inDataLen - len);
    }
    return len;
  }

  public void generateRandomData(byte[] tempBuffer, short offset, short length) {
    mRng.nextBytes(tempBuffer, offset, length);
  }

  public short generateCredKeyCert(
      ECPrivateKey attestKey,
      ECPublicKey credPubKey,
      byte[] osVersion,
      short osVersionStart,
      short osVersionLen,
      byte[] osPatchLevel,
      short osPatchLevelStart,
      short osPatchLevelLen,
      byte[] challenge,
      short challengeStart,
      short challengeLen,
      byte[] notBefore,
      short notBeforeStart,
      short notBeforeLen,
      byte[] notAfter,
      short notAfterStart,
      short notAfterLen,
      byte[] creationDateTime,
      short creationDateTimeStart,
      short creationDateTimeLen,
      byte[] attAppId,
      short attAppIdStart,
      short attAppIdLen,
      boolean testCredential,
      byte[] buf,
      short start,
      short len,
      byte[] scratch,
      short scratchStart,
      short scratchLen) {
    return mX509CertHandler.generateCredKeyCert(
        attestKey,
        this,
        credPubKey,
        osVersion,
        osVersionStart,
        osVersionLen,
        osPatchLevel,
        osPatchLevelStart,
        osPatchLevelLen,
        challenge,
        challengeStart,
        challengeLen,
        notBefore,
        notBeforeStart,
        notBeforeLen,
        notAfter,
        notAfterStart,
        notAfterLen,
        creationDateTime,
        creationDateTimeStart,
        creationDateTimeLen,
        attAppId,
        attAppIdStart,
        attAppIdLen,
        testCredential,
        buf,
        start,
        len,
        scratch,
        scratchStart,
        scratchLen);
  }

  public short generateSigningKeyCert(
      ECPublicKey signingPubKey,
      ECPrivateKey attestKey,
      byte[] notBefore,
      short notBeforeStart,
      short notBeforeLen,
      byte[] notAfter,
      short notAfterStart,
      short notAfterLen,
      byte[] buf,
      short start,
      short len,
      byte[] scratch,
      short scratchStart,
      short scratchLen) {
    return mX509CertHandler.generateSigningKeyCert(
        this,
        signingPubKey,
        attestKey,
        notBefore,
        notBeforeStart,
        notBeforeLen,
        notAfter,
        notAfterStart,
        notAfterLen,
        buf,
        start,
        len,
        scratch,
        scratchStart,
        scratchLen);
  }

  public short aesGCMEncryptOneShot(
      AESKey key,
      byte[] secret,
      short secretStart,
      short secretLen,
      byte[] encSecret,
      short encSecretStart,
      byte[] nonce,
      short nonceStart,
      short nonceLen,
      byte[] authData,
      short authDataStart,
      short authDataLen,
      boolean justUpdate) {
    // Create the cipher
    initCipher(
        key,
        nonce,
        nonceStart,
        nonceLen,
        authData,
        authDataStart,
        authDataLen,
        Cipher.MODE_ENCRYPT);
    if (authDataLen != 0) {
      aesGcmCipher.updateAAD(authData, authDataStart, authDataLen);
    }
    short len = aesGcmCipher.doFinal(secret, secretStart, secretLen, encSecret, encSecretStart);
    len += aesGcmCipher.retrieveTag(encSecret, (short) (encSecretStart + len), AES_GCM_TAG_LENGTH);
    return len;
  }

  public short encryptDecryptInPlace(
      byte[] buf, short start, short len, byte[] scratch, short scratchStart, short scratchLen) {
    short inOffset = start;
    short outOffset = start;
    while (scratchLen < len) {
      Util.arrayCopyNonAtomic(buf, inOffset, scratch, scratchStart, scratchLen);
      outOffset += doAesGcmOperation(scratch, scratchStart, scratchLen, buf, outOffset, true);
      inOffset += scratchLen;
      len -= scratchLen;
    }
    if (len > 0) {
      Util.arrayCopyNonAtomic(buf, inOffset, scratch, scratchStart, len);
      outOffset += doAesGcmOperation(scratch, scratchStart, len, buf, outOffset, true);
    }
    return outOffset;
  }

  public short aesGCMDecryptOneShot(
      AESKey key,
      byte[] encSecret,
      short encSecretStart,
      short encSecretLen,
      byte[] secret,
      short secretStart,
      byte[] nonce,
      short nonceStart,
      short nonceLen,
      byte[] authData,
      short authDataStart,
      short authDataLen,
      boolean justUpdate) {
    // Create the cipher
    initCipher(
        key,
        nonce,
        nonceStart,
        nonceLen,
        authData,
        authDataStart,
        authDataLen,
        Cipher.MODE_DECRYPT);
    if (AES_GCM_BUFFERING) {
      encSecretLen -= AES_GCM_TAG_LENGTH;
    }
    short len = aesGcmCipher.doFinal(encSecret, encSecretStart, encSecretLen, secret, secretStart);
    if (!aesGcmCipher.verifyTag(
        encSecret,
        (short) (encSecretStart + encSecretLen),
        AES_GCM_TAG_LENGTH,
        AES_GCM_TAG_LENGTH)) {
      ISOException.throwIt(ISO7816.SW_DATA_INVALID);
    }
    return len;
  }

  public void initCipher(
      AESKey aesKey,
      byte[] nonce,
      short nonceStart,
      short nonceLen,
      byte[] authData,
      short authDataStart,
      short authDataLen,
      byte mode) {

    if (nonceLen != AES_GCM_NONCE_LENGTH) {
      CryptoException.throwIt(CryptoException.ILLEGAL_VALUE);
    }

    if (aesGcmCipher == null) {
      aesGcmCipher = (AEADCipher) Cipher.getInstance(AEADCipher.ALG_AES_GCM, false);
    }
    aesGcmCipher.init(aesKey, mode, nonce, nonceStart, nonceLen);

    if (authDataLen != 0) {
      aesGcmCipher.updateAAD(authData, authDataStart, authDataLen);
    }
  }

  public boolean validateEcDsaSign(
      byte[] buf,
      short toBeSignedStart,
      short toBeSignedLen,
      short alg,
      byte[] sign,
      short signStart,
      short signLen,
      short pubKeyStart,
      short pubKeyLen) {
    ECPublicKey key = null;
    Signature signature = null;
    switch (alg) {
      case ES256:
        key = (ECPublicKey) ec256KeyPair.getPublic();
        signature = signerWithSha256;
        break;
      case ES384:
        key = (ECPublicKey) ec384KeyPair.getPublic();
        signature = signerWithSha384;
        break;
      case ES512:
        key = (ECPublicKey) ec521KeyPair.getPublic();
        signature = signerWithSha512;
        break;
      default:
        return false;
    }
    key.setW(buf, pubKeyStart, pubKeyLen);
    signature.init(key, Signature.MODE_VERIFY);
    return signature.verify(buf, toBeSignedStart, toBeSignedLen, sign, signStart, signLen);
  }

  public Signature getVerifier(byte[] key, short keyStart, short keyLen, short alg, byte mode) {
    if (alg != ES256) {
      return null;
    }
    ec256KeyPair.genKeyPair();
    ECPublicKey pubKey = (ECPublicKey) ec256KeyPair.getPublic();
    pubKey.setW(key, keyStart, keyLen);
    signerWithSha256.init(pubKey, mode);
    return signerWithSha256;
  }

  public short convertCoseSign1SignatureToAsn1(
      byte[] input,
      short offset,
      short len,
      byte[] scratchPad,
      short scratchPadOff,
      short scratchLen) {
    return mX509CertHandler.convertCoseSign1SignatureToAsn1(
        input, offset, len, scratchPad, scratchPadOff, scratchLen);
  }
}
