/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.serialization.xml;

import nu.xom.Document;
import nu.xom.Element;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Jochen Munz
 */
public class XomToStringConverterTest {

    private XomToStringConverter testSubject;

    @Before
    public void setUp() {
        testSubject = new XomToStringConverter();
    }

    @Test
    public void testCanConvert() {
        assertEquals(Document.class, testSubject.expectedSourceType());
        assertEquals(String.class, testSubject.targetType());
    }

    @Test
    public void testConvert() {
        Document doc = new Document(new Element("rootElement"));

        String actual = testSubject.convert(doc);

        assertNotNull(actual);

        assertTrue("Wrong output: " + actual, actual.contains("rootElement"));
    }
}
