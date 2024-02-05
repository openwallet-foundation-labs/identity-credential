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

package com.android.identity.mdoc.origininfo;

import com.android.identity.cbor.Cbor;
import com.android.identity.cbor.DiagnosticOption;
import com.android.identity.internal.Util;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Set;

@RunWith(JUnit4.class)
public class OriginInfoTest {
    @Test
    public void testOriginInfoDomainOrigin() {
        OriginInfoDomain info = new OriginInfoDomain("https://foo.com/bar");
        OriginInfoDomain decoded = OriginInfoDomain.decode(info.encode());
        Assert.assertEquals("https://foo.com/bar", decoded.getUrl());
        Assert.assertEquals("{\n" +
                        "  \"cat\": 1,\n" +
                        "  \"type\": 1,\n" +
                        "  \"details\": {\n" +
                        "    \"domain\": \"https://foo.com/bar\"\n" +
                        "  }\n" +
                        "}",
                Cbor.toDiagnostics(info.encode(), Set.of(DiagnosticOption.PRETTY_PRINT)));
    }
}
