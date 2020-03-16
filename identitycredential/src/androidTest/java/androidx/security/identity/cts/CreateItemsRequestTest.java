/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.security.identity.cts;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.LinkedList;

import co.nstant.in.cbor.CborException;

import androidx.security.identity.RequestNamespace;

public class CreateItemsRequestTest {
    @Test
    public void basicRequest() throws CborException {
        LinkedList<RequestNamespace> requestedEntryNamespaces = new LinkedList<>();
        requestedEntryNamespaces.add(new RequestNamespace.Builder("org.test.ns")
                .addEntryName("xyz")
                .addEntryName("abc")
                .build());
        String docType = "org.test.ns";
        assertEquals("{\n"
                        + "  'docType' : 'org.test.ns',\n"
                        + "  'nameSpaces' : {\n"
                        + "    'org.test.ns' : {\n"
                        + "      'abc' : false,\n"
                        + "      'xyz' : false\n"
                        + "    }\n"
                        + "  }\n"
                        + "}",
                Util.cborPrettyPrint(Util.createItemsRequest(requestedEntryNamespaces, docType)));
    }

    @Test
    public void multipleNamespaces() throws CborException {
        LinkedList<RequestNamespace> requestedEntryNamespaces = new LinkedList<>();
        requestedEntryNamespaces.add(new RequestNamespace.Builder("org.test.ns1")
                .addEntryName("foo")
                .addEntryName("bar")
                .build());
        requestedEntryNamespaces.add(new RequestNamespace.Builder("org.test.ns2")
                .addEntryName("xyz")
                .addEntryName("abc")
                .build());
        String docType = "org.test.ns";
        assertEquals("{\n"
                        + "  'docType' : 'org.test.ns',\n"
                        + "  'nameSpaces' : {\n"
                        + "    'org.test.ns1' : {\n"
                        + "      'bar' : false,\n"
                        + "      'foo' : false\n"
                        + "    },\n"
                        + "    'org.test.ns2' : {\n"
                        + "      'abc' : false,\n"
                        + "      'xyz' : false\n"
                        + "    }\n"
                        + "  }\n"
                        + "}",
                Util.cborPrettyPrint(Util.createItemsRequest(requestedEntryNamespaces, docType)));
    }

    @Test
    public void noDocType() throws CborException {
        LinkedList<RequestNamespace> requestedEntryNamespaces = new LinkedList<>();
        requestedEntryNamespaces.add(new RequestNamespace.Builder("org.test.ns1")
                .addEntryName("foo")
                .addEntryName("bar")
                .build());
        assertEquals("{\n"
                        + "  'nameSpaces' : {\n"
                        + "    'org.test.ns1' : {\n"
                        + "      'bar' : false,\n"
                        + "      'foo' : false\n"
                        + "    }\n"
                        + "  }\n"
                        + "}",
                Util.cborPrettyPrint(Util.createItemsRequest(requestedEntryNamespaces, null)));
    }

    @Test
    public void empty() throws CborException {
        LinkedList<RequestNamespace> requestedEntryNamespaces = new LinkedList<>();
        assertEquals("{\n"
                        + "  'nameSpaces' : {}\n"
                        + "}",
                Util.cborPrettyPrint(Util.createItemsRequest(requestedEntryNamespaces, null)));
    }
}
