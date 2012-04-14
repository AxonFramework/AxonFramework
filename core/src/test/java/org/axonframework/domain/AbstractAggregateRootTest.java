/*
 * Copyright (c) 2010-2011. Axon Framework
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
        assertFalse(deserialized(baos).getUncommittedEvents().hasNext());
        assertNotNull(deserialized(baos).getIdentifier());

        AggregateRoot deserialized = deserialized(baos);
        deserialized.doSomething();
        assertEquals(1, deserialized.getUncommittedEventCount());
        assertNotNull(deserialized.getUncommittedEvents().next());

        AggregateRoot deserialized2 = deserialized(baos);
        deserialized2.doSomething();
        assertNotNull(deserialized2.getUncommittedEvents().next());
        assertEquals(1, deserialized2.getUncommittedEventCount());
    }

    private AggregateRoot deserialized(ByteArrayOutputStream baos) {
        XStreamSerializer serializer = new XStreamSerializer();
        return (AggregateRoot) serializer.deserialize(new SimpleSerializedObject<byte[]>(baos.toByteArray(),
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

    @Test
    public void testReadEventStreamDuringEventCommit() {
        testSubject.doSomething();
        testSubject.doSomething();
        DomainEventStream uncommittedEvents = testSubject.getUncommittedEvents();
        uncommittedEvents.next();
        testSubject.commitEvents();
        assertTrue(uncommittedEvents.hasNext());
        assertNotNull(uncommittedEvents.next());
        assertFalse(uncommittedEvents.hasNext());
    }

    private static class AggregateRoot extends AbstractAggregateRoot {

        private final Object identifier;

        private AggregateRoot() {
            identifier = IdentifierFactory.getInstance().generateIdentifier();
        }

        @Override
        public Object getIdentifier() {
            return identifier;
        }

        public void doSomething() {
            registerEvent(new StubDomainEvent());
        }
    }
}
