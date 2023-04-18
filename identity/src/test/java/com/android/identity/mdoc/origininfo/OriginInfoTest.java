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

import com.android.identity.internal.Util;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class OriginInfoTest {
    @Test
    public void testOriginInfoQr() {
        OriginInfoQr info = new OriginInfoQr(OriginInfo.CAT_RECEIVE);
        OriginInfoQr decoded = OriginInfoQr.decode(info.encode());
        Assert.assertEquals(OriginInfo.CAT_RECEIVE, decoded.getCat());
        Assert.assertEquals("{\n" +
                "  'cat' : 1,\n" +
                "  'type' : 2,\n" +
                "  'Details' : null\n" +
                "}", Util.cborPrettyPrint(info.encode()));
    }

    @Test
    public void testOriginInfoQrDelivery() {
        OriginInfoQr info = new OriginInfoQr(OriginInfo.CAT_DELIVERY);
        OriginInfoQr decoded = OriginInfoQr.decode(info.encode());
        Assert.assertEquals(OriginInfo.CAT_DELIVERY, decoded.getCat());
        Assert.assertEquals("{\n" +
                "  'cat' : 0,\n" +
                "  'type' : 2,\n" +
                "  'Details' : null\n" +
                "}", Util.cborPrettyPrint(info.encode()));
    }

    @Test
    public void testOriginInfoNfc() {
        OriginInfoNfc info = new OriginInfoNfc(OriginInfo.CAT_RECEIVE);
        OriginInfoNfc decoded = OriginInfoNfc.decode(info.encode());
        Assert.assertEquals(OriginInfo.CAT_RECEIVE, decoded.getCat());
        Assert.assertEquals("{\n" +
                "  'cat' : 1,\n" +
                "  'type' : 3,\n" +
                "  'Details' : null\n" +
                "}", Util.cborPrettyPrint(info.encode()));
    }

    @Test
    public void testOriginInfoNfcDelivery() {
        OriginInfoNfc info = new OriginInfoNfc(OriginInfo.CAT_DELIVERY);
        OriginInfoNfc decoded = OriginInfoNfc.decode(info.encode());
        Assert.assertEquals(OriginInfo.CAT_DELIVERY, decoded.getCat());
        Assert.assertEquals("{\n" +
                "  'cat' : 0,\n" +
                "  'type' : 3,\n" +
                "  'Details' : null\n" +
                "}", Util.cborPrettyPrint(info.encode()));
    }

    @Test
    public void testOriginInfoWebsite() {
        OriginInfoWebsite info = new OriginInfoWebsite(OriginInfo.CAT_RECEIVE, "https://foo.com/bar");
        OriginInfoWebsite decoded = OriginInfoWebsite.decode(info.encode());
        Assert.assertEquals(OriginInfo.CAT_RECEIVE, decoded.getCat());
        Assert.assertEquals("https://foo.com/bar", decoded.getBaseUrl());
        Assert.assertEquals("{\n" +
                "  'cat' : 1,\n" +
                "  'type' : 1,\n" +
                "  'Details' : {\n" +
                "    'baseUrl' : 'https://foo.com/bar'\n" +
                "  }\n" +
                "}", Util.cborPrettyPrint(info.encode()));
    }

    @Test
    public void testOriginInfoWebsiteDelivery() {
        OriginInfoWebsite info = new OriginInfoWebsite(OriginInfo.CAT_DELIVERY, "https://foo.com/baz");
        OriginInfoWebsite decoded = OriginInfoWebsite.decode(info.encode());
        Assert.assertEquals(OriginInfo.CAT_DELIVERY, decoded.getCat());
        Assert.assertEquals("{\n" +
                "  'cat' : 0,\n" +
                "  'type' : 1,\n" +
                "  'Details' : {\n" +
                "    'baseUrl' : 'https://foo.com/baz'\n" +
                "  }\n" +
                "}", Util.cborPrettyPrint(info.encode()));
    }

}
