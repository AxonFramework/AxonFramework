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

package org.axonframework.eventstore;

import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.StubDomainEvent;
import org.axonframework.domain.UUIDAggregateIdentifier;
import org.dom4j.Document;
import org.joda.time.DateMidnight;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.junit.*;

import java.io.UnsupportedEncodingException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Arrays;

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
        assertEquals("someValue", actualEvent.getMetaDataValue("someMetaData"));
    }

    @Test(expected = UnsupportedCharsetException.class)
    public void testInitialize_WithStrangeCharset() {
        testSubject = new XStreamEventSerializer("Weird");
    }

    @Test
    public void testPackageAlias() throws UnsupportedEncodingException {
        testSubject.addPackageAlias("axondomain", "org.axonframework.domain");
        testSubject.addPackageAlias("axon", "org.axonframework");

        byte[] serialized = testSubject.serialize(new StubDomainEvent(new UUIDAggregateIdentifier(),
                                                                      1));
        String asString = new String(serialized, "UTF-8");
        assertFalse(asString.contains("org.axonframework.domain"));
        assertTrue(asString.contains("axondomain"));
        StubDomainEvent deserialized = (StubDomainEvent) testSubject.deserialize(serialized);
        assertEquals(new Long(1), deserialized.getSequenceNumber());
    }

    @Test
    public void testAlias() throws UnsupportedEncodingException {
        testSubject.addAlias("stub", StubDomainEvent.class);

        byte[] serialized = testSubject.serialize(new StubDomainEvent(new UUIDAggregateIdentifier(),
                                                                      1));
        String asString = new String(serialized, "UTF-8");
        assertFalse(asString.contains("org.axonframework.domain"));
        assertTrue(asString.contains("<stub eventRevision=\"0\">"));
        StubDomainEvent deserialized = (StubDomainEvent) testSubject.deserialize(serialized);
        assertEquals(new Long(1), deserialized.getSequenceNumber());
    }

    @Test
    public void testFieldAlias() throws UnsupportedEncodingException {
        testSubject.addFieldAlias("aggId", DomainEvent.class, "aggregateIdentifier");

        byte[] serialized = testSubject.serialize(new StubDomainEvent(new UUIDAggregateIdentifier(),
                                                                      1));
        String asString = new String(serialized, "UTF-8");
        assertFalse(asString.contains("aggregateIdentifier"));
        assertTrue(asString.contains("<aggId>"));
        StubDomainEvent deserialized = (StubDomainEvent) testSubject.deserialize(serialized);
        assertEquals(new Long(1), deserialized.getSequenceNumber());
    }

    /**
     * Tests the scenario as described in <a href="http://code.google.com/p/axonframework/issues/detail?id=150">issue
     * #150</a>.
     */
    @Test
    public void testSerializeWithSpecialCharacters_WithUpcasters() {
        testSubject.setEventUpcasters(Arrays.<EventUpcaster<Document>>asList(new EventUpcaster<Document>() {
            @Override
            public Class<Document> getSupportedRepresentation() {
                return Document.class;
            }

            @Override
            public Document upcast(Document event) {
                return event;
            }
        }));
        String special_char_string = "Special '\"&; chars";
        byte[] serialized = testSubject.serialize(new TestEvent(special_char_string));
        TestEvent deserialized = (TestEvent) testSubject.deserialize(serialized);
        assertEquals(special_char_string, deserialized.getName());
    }

    /**
     * Tests the scenario as described in <a href="http://code.google.com/p/axonframework/issues/detail?id=150">issue
     * #150</a>.
     */
    @Test
    public void testSerializeWithSpecialCharacters_WithoutUpcasters() {
        String special_char_string = "Special '\"&; chars";
        byte[] serialized = testSubject.serialize(new TestEvent(special_char_string));
        TestEvent deserialized = (TestEvent) testSubject.deserialize(serialized);
        assertEquals(special_char_string, deserialized.getName());
    }

    public static class TestEvent extends DomainEvent {

        private static final long serialVersionUID = 1657550542124835062L;
        private String name;
        private DateMidnight date;
        private DateTime dateTime;
        private Period period;

        public TestEvent(String name) {
            super(0, new UUIDAggregateIdentifier());
            this.name = name;
            this.date = new DateMidnight();
            this.dateTime = new DateTime();
            this.period = new Period(100);
            addMetaData("someMetaData", "someValue");
        }

        public String getName() {
            return name;
        }
    }
}
