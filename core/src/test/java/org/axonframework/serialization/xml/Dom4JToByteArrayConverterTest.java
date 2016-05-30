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

package org.axonframework.serialization.xml;

import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.SimpleSerializedObject;
import org.axonframework.serialization.SimpleSerializedType;
import org.dom4j.Document;
import org.dom4j.DocumentFactory;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class Dom4JToByteArrayConverterTest {

    private Dom4JToByteArrayConverter testSubject;
    private SimpleSerializedType serializedType;

    @Before
    public void setUp() throws Exception {
        testSubject = new Dom4JToByteArrayConverter();
        serializedType = new SimpleSerializedType("custom", "0");
    }

    @Test
    public void testCanConvert() {
        assertEquals(Document.class, testSubject.expectedSourceType());
        assertEquals(byte[].class, testSubject.targetType());
    }

    @Test
    public void testConvert() throws Exception {
        DocumentFactory df = DocumentFactory.getInstance();
        Document doc = df.createDocument("UTF-8");
        doc.setRootElement(df.createElement("rootElement"));

        SimpleSerializedObject<Document> original = new SimpleSerializedObject<>(doc,
                                                                                         Document.class,
                                                                                         serializedType);
        SerializedObject<byte[]> actual = testSubject.convert(original);

        assertNotNull(actual);
        assertNotNull(actual.getData());
        String actualString = new String(actual.getData());

        assertTrue("Wrong output: " + actualString, actualString.contains("rootElement"));
    }
}
