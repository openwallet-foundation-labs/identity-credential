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
 
//import androidx.annotation.NonNull;
//import androidx.annotation.Nullable;
 
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Map;
 
import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.builder.ArrayBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnicodeString;
 
/**
* Helper class for building <code>DeviceRequest</code> <a href="http://cbor.io/">CBOR</a>
* as specified in <em>ISO/IEC 18013-5</em> section 8.3 <em>Device Retrieval</em>.
*
* <p>This class supports requesting data for multiple documents in a single presentation.
*/
public final class DeviceRequestGenerator {
   private static final String TAG = "DeviceRequestGenerator";
 
   private final ArrayBuilder<CborBuilder> mDocRequestsBuilder;
   private byte[] mEncodedSessionTranscript;
 
   /**
    * Constructs a new {@link DeviceRequestGenerator}.
    */
   public DeviceRequestGenerator() {
       mDocRequestsBuilder = new CborBuilder().addArray();
   }
 
   /**
    * Sets the bytes of the <code>SessionTranscript</code> CBOR.
    *
    * This must be called if any of the document requests use reader authentication.
    *
    * @param encodedSessionTranscript the bytes of <code>SessionTranscript</code>.
    * @return the <code>DeviceRequestGenerator</code>.
    */
   public DeviceRequestGenerator setSessionTranscript(
           byte[] encodedSessionTranscript) {
       mEncodedSessionTranscript = encodedSessionTranscript;
       return this;
   }
 
   /**
    * Builds the <code>DeviceRequest</code> CBOR.
    *
    * @return the bytes of <code>DeviceRequest</code> CBOR.
    */
   public byte[] generate() {
       return Util.cborEncode(new CborBuilder()
               .addMap()
               .put("version", "1.0")
               .put(new UnicodeString("docRequests"), mDocRequestsBuilder.end().build().get(0))
               .end()
               .build().get(0));
   }
 
}