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

import androidx.test.filters.SmallTest;

import org.junit.Assert;
import org.junit.Test;

public class OriginInfoTest {
    @Test
    @SmallTest
    public void testOriginInfoQr() {
        OriginInfoQr info = new OriginInfoQr(OriginInfo.CAT_RECEIVE);
        OriginInfoQr decoded = info.decode(info.encode());
        Assert.assertEquals(OriginInfo.CAT_RECEIVE, decoded.getCat());
        Assert.assertEquals("[1, 2, null]", Util.cborPrettyPrint(info.encode()));
    }

    @Test
    @SmallTest
    public void testOriginInfoQrDelivery() {
        OriginInfoQr info = new OriginInfoQr(OriginInfo.CAT_DELIVERY);
        OriginInfoQr decoded = info.decode(info.encode());
        Assert.assertEquals(OriginInfo.CAT_DELIVERY, decoded.getCat());
        Assert.assertEquals("[0, 2, null]", Util.cborPrettyPrint(info.encode()));
    }

    @Test
    @SmallTest
    public void testOriginInfoNfc() {
        OriginInfoNfc info = new OriginInfoNfc(OriginInfo.CAT_RECEIVE);
        OriginInfoNfc decoded = info.decode(info.encode());
        Assert.assertEquals(OriginInfo.CAT_RECEIVE, decoded.getCat());
        Assert.assertEquals("[1, 3, null]", Util.cborPrettyPrint(info.encode()));
    }

    @Test
    @SmallTest
    public void testOriginInfoNfcDelivery() {
        OriginInfoNfc info = new OriginInfoNfc(OriginInfo.CAT_DELIVERY);
        OriginInfoNfc decoded = info.decode(info.encode());
        Assert.assertEquals(OriginInfo.CAT_DELIVERY, decoded.getCat());
        Assert.assertEquals("[0, 3, null]", Util.cborPrettyPrint(info.encode()));
    }

    @Test
    @SmallTest
    public void testOriginInfoWebsite() {
        OriginInfoWebsite info = new OriginInfoWebsite(OriginInfo.CAT_RECEIVE, "foo.com/bar");
        OriginInfoWebsite decoded = info.decode(info.encode());
        Assert.assertEquals(OriginInfo.CAT_RECEIVE, decoded.getCat());
        Assert.assertEquals("foo.com/bar", decoded.getBaseUrl());
        Assert.assertEquals("[\n" +
                "  1,\n" +
                "  1,\n" +
                "  {\n" +
                "    'baseUrl' : 'foo.com/bar'\n" +
                "  }\n" +
                "]", Util.cborPrettyPrint(info.encode()));
    }

    @Test
    @SmallTest
    public void testOriginInfoWebsiteDelivery() {
        OriginInfoWebsite info = new OriginInfoWebsite(OriginInfo.CAT_DELIVERY, "foo.com/baz");
        OriginInfoWebsite decoded = info.decode(info.encode());
        Assert.assertEquals(OriginInfo.CAT_DELIVERY, decoded.getCat());
        Assert.assertEquals("foo.com/baz", decoded.getBaseUrl());
        Assert.assertEquals("[\n" +
                "  0,\n" +
                "  1,\n" +
                "  {\n" +
                "    'baseUrl' : 'foo.com/baz'\n" +
                "  }\n" +
                "]", Util.cborPrettyPrint(info.encode()));
    }

}
