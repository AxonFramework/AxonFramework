/*
 * Copyright (c) 2010-2013. Axon Framework
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

package org.axonframework.upcasting;

import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.SerializedType;
import org.axonframework.serializer.SimpleSerializedObject;
import org.axonframework.serializer.SimpleSerializedType;
import org.junit.*;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class AbstractSingleEntryUpcasterTest {

    @Test
    public void testUpcastSerializedType() {
        final SimpleSerializedType upcastType = new SimpleSerializedType("newString", "2");
        AbstractSingleEntryUpcaster<String> testSubject = new StubUpcaster("did it", upcastType);

        List<SerializedType> actual = testSubject.upcast(new SimpleSerializedType("string", "1"));
        assertEquals(1, actual.size());
        assertEquals(upcastType, actual.get(0));
    }

    @Test
    public void testUpcastSerializedType_Null() {
        AbstractSingleEntryUpcaster<String> testSubject = new StubUpcaster("value", null);

        List<SerializedType> actual = testSubject.upcast(new SimpleSerializedType("string", "1"));
        assertEquals(0, actual.size());
    }

    @Test
    public void testUpcastSerializedObject() {
        final SerializedType upcastType = new SimpleSerializedType("newString", "2");
        AbstractSingleEntryUpcaster<String> testSubject = new StubUpcaster("did it", upcastType);

        List<SerializedObject<?>> actual = testSubject.upcast(
                new SimpleSerializedObject<String>("string", String.class, "string", "1"),
                Collections.singletonList(upcastType), null);
        assertEquals(1, actual.size());
        assertEquals(upcastType, actual.get(0).getType());
        assertEquals("did it", actual.get(0).getData());
    }

    @Test
    public void testUpcastSerializedObject_Null() {
        AbstractSingleEntryUpcaster<String> testSubject = new StubUpcaster(null, null);

        List<SerializedObject<?>> actual = testSubject.upcast(
                new SimpleSerializedObject<String>("string", String.class, "string", "1"), null, null);
        assertEquals(0, actual.size());
    }

    private static class StubUpcaster extends AbstractSingleEntryUpcaster<String> {

        private final String upcastValue;
        private final SerializedType upcastType;

        public StubUpcaster(String upcastValue, SerializedType upcastType) {
            this.upcastValue = upcastValue;
            this.upcastType = upcastType;
        }

        @Override
        protected String doUpcast(SerializedObject intermediateRepresentation, UpcastingContext context) {
            return upcastValue;
        }

        @Override
        protected SerializedType doUpcast(SerializedType serializedType) {
            return upcastType;
        }

        @Override
        public boolean canUpcast(SerializedType serializedType) {
            return true;
        }

        @Override
        public Class<String> expectedRepresentationType() {
            return String.class;
        }
    }
}
