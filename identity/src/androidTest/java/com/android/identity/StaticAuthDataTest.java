/*
 * Copyright 2022 The Android Open Source Project
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

import androidx.core.util.Pair;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.UnicodeString;

@SuppressWarnings("deprecation")
@RunWith(AndroidJUnit4.class)
public class StaticAuthDataTest {

    @Test
    @SmallTest
    public void testDecodeStaticAuthData() throws Exception {
        /* Manually builds up CBOR that conforms to EXAMPLE_STATIC_AUTH_DATA_HEX
         * defined in the bottom of this class. Then checks that decodeStaticAuthData()
         * decodes it correctly.
         */

        CborBuilder builder = new CborBuilder();
        MapBuilder<CborBuilder> outerBuilder = builder.addMap();
        outerBuilder.addKey("org.namespace");
        outerBuilder.end();

        DataItem issuerSignedItem = new CborBuilder()
                .addMap()
                .put("digestID", 42)
                .put("random", new byte[] {0x50, 0x51, 0x52})
                .put("elementIdentifier", "dataElementName")
                .put(new UnicodeString("elementValue"), SimpleValue.NULL)
                .end()
                .build().get(0);
        byte[] encodedIssuerSignedItem = Util.cborEncode(issuerSignedItem);

        DataItem issuerSignedItem2 = new CborBuilder()
                .addMap()
                .put("digestID", 43)
                .put("random", new byte[] {0x53, 0x54, 0x55})
                .put("elementIdentifier", "dataElementName2")
                .put(new UnicodeString("elementValue"), SimpleValue.NULL)
                .end()
                .build().get(0);
        byte[] encodedIssuerSignedItem2 = Util.cborEncodeWithoutCanonicalizing(issuerSignedItem2);

        DataItem digestIdMappingItem = new CborBuilder()
                .addMap()
                .putArray("org.namespace")
                .add(Util.cborBuildTaggedByteString(encodedIssuerSignedItem))
                .add(Util.cborBuildTaggedByteString(encodedIssuerSignedItem2))
                .end()
                .end()
                .build().get(0);

        byte[] staticAuthData = Util.cborEncode(new CborBuilder()
                .addMap()
                .put(new UnicodeString("digestIdMapping"), digestIdMappingItem)
                // Make issuerAuth look like a valid COSE_Sign1
                .putArray("issuerAuth") // IssuerAuth
                    .addArray().end()
                    .add(SimpleValue.NULL)
                    .add(new byte[] {0x01, 0x02})
                .end()
                .end()
                .build().get(0));

        Assert.assertEquals(EXAMPLE_STATIC_AUTH_DATA_HEX, Util.toHex(staticAuthData));

        // Now check that decodeStaticAuthData() correctly decodes CBOR conforming to this CDDL
        Pair<Map<String, List<byte[]>>, byte[]> val = Utility.decodeStaticAuthData(staticAuthData);
        Map<String, List<byte[]>> digestIdMapping = val.first;
        byte[] encodedStaticAuthData = val.second;

        // Check that the IssuerSignedItem instances are correctly decoded and the order
        // of the map matches what was put in above
        Assert.assertEquals(1, digestIdMapping.size());
        List<byte[]> list = digestIdMapping.get("org.namespace");
        Assert.assertNotNull(list);
        Assert.assertEquals(2, list.size());
        Assert.assertEquals("{\n" +
                "  'random' : [0x50, 0x51, 0x52],\n" +
                "  'digestID' : 42,\n" +
                "  'elementValue' : null,\n" +
                "  'elementIdentifier' : 'dataElementName'\n" +
                "}", Util.cborPrettyPrint(list.get(0)));
        Assert.assertEquals("{\n" +
                "  'digestID' : 43,\n" +
                "  'random' : [0x53, 0x54, 0x55],\n" +
                "  'elementIdentifier' : 'dataElementName2',\n" +
                "  'elementValue' : null\n" +
                "}", Util.cborPrettyPrint(list.get(1)));
    }

    @Test
    @SmallTest
    public void testEncodeStaticAuthData() throws Exception {
        /* Uses encodeStaticAuthData() to build up CBOR that conforms to
         * EXAMPLE_STATIC_AUTH_DATA_HEX defined in the bottom of this class.
         * Then checks that the same bytes are produced.
         */

        DataItem issuerSignedItem = new CborBuilder()
                .addMap()
                .put("digestID", 42)
                .put("random", new byte[] {0x50, 0x51, 0x52})
                .put("elementIdentifier", "dataElementName")
                .put(new UnicodeString("elementValue"), SimpleValue.NULL)
                .end()
                .build().get(0);
        byte[] encodedIssuerSignedItem = Util.cborEncode(issuerSignedItem);

        DataItem issuerSignedItem2 = new CborBuilder()
                .addMap()
                .put("digestID", 43)
                .put("random", new byte[] {0x53, 0x54, 0x55})
                .put("elementIdentifier", "dataElementName2")
                .put(new UnicodeString("elementValue"), SimpleValue.NULL)
                .end()
                .build().get(0);
        byte[] encodedIssuerSignedItem2 = Util.cborEncodeWithoutCanonicalizing(issuerSignedItem2);

        Map<String, List<byte[]>> issuerSignedMapping = new HashMap<>();
        issuerSignedMapping.put("org.namespace",
                Arrays.asList(encodedIssuerSignedItem, encodedIssuerSignedItem2));

        byte[] encodedIssuerAuth = Util.cborEncode(new CborBuilder()
                .addArray()
                    .addArray()
                    .end()
                .add(SimpleValue.NULL)
                .add(new byte[] {0x01, 0x02})
                .end()
                .build().get(0));

        byte[] staticAuthData = Utility.encodeStaticAuthData(issuerSignedMapping, encodedIssuerAuth);

        Assert.assertEquals(EXAMPLE_STATIC_AUTH_DATA_HEX, Util.toHex(staticAuthData));
    }

    // If you go to cbor.me with the hex value below you'll get
    //
    //  {
    //    "issuerAuth": [[], null, << 1, 2 >>],
    //    "digestIdMapping": {
    //      "org.namespace": [
    //        24(<< {
    //                "random": h'505152',
    //                "digestID": 42,
    //                "elementValue": null,
    //                "elementIdentifier": "dataElementName"
    //                } >>),
    //        24(<< {
    //                "digestID": 43,
    //                "random": h'535455',
    //                "elementIdentifier": "dataElementName2",
    //                "elementValue": null
    //                } >>)
    //      ]
    //    }
    //  }
    //
    // which by inspection matches this CDDL:
    //
    //     StaticAuthData = {
    //         "digestIdMapping": DigestIdMapping,
    //         "issuerAuth" : IssuerAuth
    //     }
    //
    //     DigestIdMapping = {
    //         NameSpace =&gt; [ + IssuerSignedItemBytes ]
    //     }
    //
    //     ; Defined in ISO 18013-5
    //     ;
    //     NameSpace = String
    //     DataElementIdentifier = String
    //     DigestID = uint
    //     IssuerAuth = COSE_Sign1 ; The payload is MobileSecurityObjectBytes
    //
    //     IssuerSignedItemBytes = #6.24(bstr .cbor IssuerSignedItem)
    //
    //     IssuerSignedItem = {
    //       "digestID" : uint,                           ; Digest ID for issuer data auth
    //       "random" : bstr,                             ; Random value for issuer data auth
    //       "elementIdentifier" : DataElementIdentifier, ; Data element identifier
    //       "elementValue" : NULL                        ; Placeholder for Data element value
    //     }
    //
    // TODO: We should add support for 'emb cbor' in Util.cborPrettyPrint() so we can just
    //   compare against a printed value instead of a hexdump.
    //
    private final String EXAMPLE_STATIC_AUTH_DATA_HEX =
            "a26a697373756572417574688380f64201026f64696765737449644d61707069"
          + "6e67a16d6f72672e6e616d65737061636582d8185847a46672616e646f6d4350"
          + "5152686469676573744944182a6c656c656d656e7456616c7565f671656c656d"
          + "656e744964656e7469666965726f64617461456c656d656e744e616d65d81858"
          + "48a4686469676573744944182b6672616e646f6d4353545571656c656d656e74"
          + "4964656e7469666965727064617461456c656d656e744e616d65326c656c656d"
          + "656e7456616c7565f6";
}
