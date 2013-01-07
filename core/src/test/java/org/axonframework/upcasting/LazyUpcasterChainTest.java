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

package org.axonframework.upcasting;

import org.axonframework.serializer.ConverterFactory;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.SerializedType;
import org.axonframework.serializer.SimpleSerializedObject;
import org.axonframework.serializer.SimpleSerializedType;
import org.junit.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class LazyUpcasterChainTest extends UpcasterChainTest {

    @Override
    protected UpcasterChain createUpcasterChain(ConverterFactory converterFactory, Upcaster... upcasters) {
        return new LazyUpcasterChain(converterFactory, Arrays.asList(upcasters));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testToBeConstructed() {
        Upcaster<String> firstUpcaster = spy(new StubUpcaster("1", "2"));
        Upcaster<String> secondUpcaster = spy(new StubUpcaster("2", "3"));
        Upcaster<String> thirdUpcaster = spy(new StubUpcaster("never", "ever"));
        LazyUpcasterChain testSubject = new LazyUpcasterChain(Arrays.<Upcaster>asList(firstUpcaster,
                                                                                      thirdUpcaster,
                                                                                      secondUpcaster));

        List<SerializedObject> actualResult = testSubject.upcast(
                new SimpleSerializedObject<String>("object", String.class, "type", "1"), null);
        verify(firstUpcaster, never()).upcast(isA(SerializedObject.class), isA(List.class), any(UpcastingContext.class));
        verify(secondUpcaster, never()).upcast(isA(SerializedObject.class), isA(List.class), any(UpcastingContext.class));
        verify(thirdUpcaster, never()).upcast(isA(SerializedObject.class), isA(List.class), any(UpcastingContext.class));
        assertEquals(4, actualResult.size());
        assertEquals("3", actualResult.get(0).getType().getRevision());
        assertEquals("3", actualResult.get(1).getType().getRevision());
        assertEquals("3", actualResult.get(2).getType().getRevision());
        assertEquals("3", actualResult.get(3).getType().getRevision());
        verify(firstUpcaster, never()).upcast(isA(SerializedObject.class), isA(List.class), any(UpcastingContext.class));
        verify(secondUpcaster, never()).upcast(isA(SerializedObject.class), isA(List.class), any(UpcastingContext.class));
        verify(thirdUpcaster, never()).upcast(isA(SerializedObject.class), isA(List.class), any(UpcastingContext.class));
        assertEquals("upcast upcast object", actualResult.get(0).getData().toString());
        assertEquals("upcast upcast object", actualResult.get(1).getData().toString());
        assertEquals("upcast upcast object", actualResult.get(2).getData().toString());
        assertEquals("upcast upcast object", actualResult.get(3).getData().toString());
        verify(firstUpcaster).upcast(isA(SerializedObject.class), isA(List.class), any(UpcastingContext.class));
        verify(secondUpcaster, times(2)).upcast(isA(SerializedObject.class), isA(List.class), any(UpcastingContext.class));
        verify(thirdUpcaster, never()).upcast(isA(SerializedObject.class), isA(List.class), any(UpcastingContext.class));
    }

    private class StubUpcaster implements Upcaster<String> {

        private final String workingRevision;
        private final String newRevision;

        public StubUpcaster(String workingRevision, String newRevision) {
            this.workingRevision = workingRevision;
            this.newRevision = newRevision;
        }

        @Override
        public boolean canUpcast(SerializedType serializedType) {
            return workingRevision.equals(serializedType.getRevision());
        }

        @Override
        public Class<String> expectedRepresentationType() {
            return String.class;
        }

        @Override
        public List<SerializedObject<?>> upcast(SerializedObject<String> intermediateRepresentation,
                                                List<SerializedType> expectedTypes, UpcastingContext context) {
            List<SerializedObject<?>> upcastObjects = new ArrayList<SerializedObject<?>>(expectedTypes.size());
            for (SerializedType expectedType : expectedTypes) {
                SerializedObject<String> upcastObject = new SimpleSerializedObject<String>(
                        "upcast " + intermediateRepresentation.getData(), String.class, expectedType);
                upcastObjects.add(upcastObject);
            }
            return upcastObjects;
        }

        @Override
        public List<SerializedType> upcast(SerializedType serializedType) {
            SerializedType upcastType = new SimpleSerializedType(serializedType.getName(), newRevision);
            return Arrays.asList(upcastType, upcastType);
        }
    }
}
