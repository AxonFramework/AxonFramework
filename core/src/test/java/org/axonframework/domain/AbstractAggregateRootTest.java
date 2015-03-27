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

package org.axonframework.domain;

import org.axonframework.serializer.SimpleSerializedObject;
import org.axonframework.serializer.xml.XStreamSerializer;
import org.junit.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class AbstractAggregateRootTest {

    private AggregateRoot testSubject;

    @Before
    public void setUp() {
        testSubject = new AggregateRoot();
    }

    @Test
    public void testSerializability_GenericXStreamSerializer() throws IOException {
        XStreamSerializer serializer = new XStreamSerializer();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(serializer.serialize(testSubject, byte[].class).getData());

        assertEquals(0, deserialized(baos).getUncommittedEventCount());
        assertEquals(0, deserialized(baos).getUncommittedEvents().size());
        assertNotNull(deserialized(baos).getIdentifier());

        AggregateRoot deserialized = deserialized(baos);
        deserialized.doSomething();
        assertEquals(1, deserialized.getUncommittedEventCount());
        assertNotNull(deserialized.getUncommittedEvents().get(0));

        AggregateRoot deserialized2 = deserialized(baos);
        deserialized2.doSomething();
        assertNotNull(deserialized2.getUncommittedEvents().get(0));
        assertEquals(1, deserialized2.getUncommittedEventCount());
    }

    private AggregateRoot deserialized(ByteArrayOutputStream baos) {
        XStreamSerializer serializer = new XStreamSerializer();
        return (AggregateRoot) serializer.deserialize(new SimpleSerializedObject<>(baos.toByteArray(),
                                                                                         byte[].class,
                                                                                         "ignored",
                                                                                         "0"));
    }

    @Test
    public void testRegisterEvent() {
        assertEquals(0, testSubject.getUncommittedEventCount());
        testSubject.doSomething();
        assertEquals(1, testSubject.getUncommittedEventCount());
    }

    private static class AggregateRoot extends AbstractAggregateRoot {

        private final String identifier;

        private AggregateRoot() {
            identifier = IdentifierFactory.getInstance().generateIdentifier();
        }

        @Override
        public String getIdentifier() {
            return identifier;
        }

        public void doSomething() {
            registerEvent(new StubDomainEvent());
        }
    }
}
