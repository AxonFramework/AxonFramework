/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.eventstore.eventstore;

import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.StubDomainEvent;
import org.axonframework.eventstore.EventStoreException;
import org.axonframework.eventstore.XStreamEventSerializer;
import org.junit.*;

import java.io.UnsupportedEncodingException;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class XStreamEventSerializerTest {

    private XStreamEventSerializer testSubject;

    @Before
    public void setUp() {
        this.testSubject = new XStreamEventSerializer();
    }

    @Test
    public void testSerializeAndDeserializeDomainEvent() {
        byte[] bytes = testSubject.serialize(new TestEvent("Henk"));
        Object actualResult = testSubject.deserialize(bytes);
        assertTrue(actualResult instanceof TestEvent);
        TestEvent actualEvent = (TestEvent) actualResult;
        assertEquals("Henk", actualEvent.getName());
    }

    @Test
    public void testSerialize_WithStrangeCharset() {
        testSubject = new XStreamEventSerializer("Weird");
        try {
            testSubject.serialize(new TestEvent("Klaas"));
            fail("Expected EventStoreException");
        }
        catch (EventStoreException e) {
            assertTrue("Not the type of exception that was expected",
                       e.getCause() instanceof UnsupportedEncodingException);
        }
    }

    @Test
    public void testDeserialize_WithStrangeCharset() {
        testSubject = new XStreamEventSerializer("Weird");
        try {
            testSubject.deserialize(new byte[]{});
            fail("Expected EventStoreException");
        }
        catch (EventStoreException e) {
            assertTrue("Not the type of exception that was expected",
                       e.getCause() instanceof UnsupportedEncodingException);
        }
    }

    @Test
    public void testPackageAlias() throws UnsupportedEncodingException {
        testSubject.addPackageAlias("axondomain", "org.axonframework.domain");
        testSubject.addPackageAlias("axon", "org.axonframework");

        byte[] serialized = testSubject.serialize(new StubDomainEvent(UUID.randomUUID(), 1));
        String asString = new String(serialized, "UTF-8");
        assertFalse(asString.contains("org.axonframework.domain"));
        assertTrue(asString.contains("axondomain"));
        StubDomainEvent deserialized = (StubDomainEvent) testSubject.deserialize(serialized);
        assertEquals(new Long(1), deserialized.getSequenceNumber());
    }

    @Test
    public void testAlias() throws UnsupportedEncodingException {
        testSubject.addAlias("stub", StubDomainEvent.class);

        byte[] serialized = testSubject.serialize(new StubDomainEvent(UUID.randomUUID(), 1));
        String asString = new String(serialized, "UTF-8");
        assertFalse(asString.contains("org.axonframework.domain"));
        assertTrue(asString.contains("<stub>"));
        StubDomainEvent deserialized = (StubDomainEvent) testSubject.deserialize(serialized);
        assertEquals(new Long(1), deserialized.getSequenceNumber());
    }

    @Test
    public void testFieldAlias() throws UnsupportedEncodingException {
        testSubject.addFieldAlias("aggId", DomainEvent.class, "aggregateIdentifier");

        byte[] serialized = testSubject.serialize(new StubDomainEvent(UUID.randomUUID(), 1));
        String asString = new String(serialized, "UTF-8");
        assertFalse(asString.contains("aggregateIdentifier"));
        assertTrue(asString.contains("<aggId>"));
        StubDomainEvent deserialized = (StubDomainEvent) testSubject.deserialize(serialized);
        assertEquals(new Long(1), deserialized.getSequenceNumber());
    }

    public static class TestEvent extends DomainEvent {

        private String name;

        public TestEvent(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
