/*
 * Copyright (c) 2010-2012. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.serializer.xml;

import nu.xom.Document;
import nu.xom.Element;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.SimpleSerializedObject;
import org.axonframework.serializer.SimpleSerializedType;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Jochen Munz
 */
public class XomToByteArrayConverterTest {

    private XomToByteArrayConverter testSubject;
    private SimpleSerializedType serializedType;

    @Before
    public void setUp() throws Exception {
        testSubject = new XomToByteArrayConverter();
        serializedType = new SimpleSerializedType("custom", "0");
    }

    @Test
    public void testCanConvert() {
        assertEquals(Document.class, testSubject.expectedSourceType());
        assertEquals(byte[].class, testSubject.targetType());
    }

    @Test
    public void testConvert() throws Exception {
        Document doc = new Document(new Element("rootElement"));

        SimpleSerializedObject<Document> original = new SimpleSerializedObject<Document>(doc,
                Document.class,
                serializedType);
        SerializedObject<byte[]> actual = testSubject.convert(original);

        assertNotNull(actual);
        assertNotNull(actual.getData());
        String actualString = new String(actual.getData());

        assertTrue("Wrong output: " + actualString, actualString.contains("rootElement"));
    }
}