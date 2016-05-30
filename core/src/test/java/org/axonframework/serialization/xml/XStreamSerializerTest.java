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

import org.axonframework.eventsourcing.StubDomainEvent;
import org.axonframework.serialization.Revision;
import org.axonframework.serialization.SerializedObject;
import org.junit.Before;
import org.junit.Test;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class XStreamSerializerTest {

    private XStreamSerializer testSubject;
    private static final String SPECIAL__CHAR__STRING = "Special chars: '\"&;\n\\<>/\n\t";
    private static final String REGULAR_STRING = "Henk";
    private TestEvent testEvent;

    @Before
    public void setUp() {
        this.testSubject = new XStreamSerializer();
        this.testEvent = new TestEvent(REGULAR_STRING);
    }

    @Test
    public void testSerializeAndDeserializeDomainEvent() {
        SerializedObject<byte[]> serializedEvent = testSubject.serialize(testEvent, byte[].class);
        Object actualResult = testSubject.deserialize(serializedEvent);
        assertTrue(actualResult instanceof TestEvent);
        TestEvent actualEvent = (TestEvent) actualResult;
        assertEquals(testEvent, actualEvent);
    }

    
    @Test
    public void testSerializeAndDeserializeDomainEvent_WithXomUpcasters(){
        SerializedObject<nu.xom.Document> serializedEvent = testSubject.serialize(testEvent, nu.xom.Document.class);
        Object actualResult = testSubject.deserialize(serializedEvent);
        assertEquals(testEvent, actualResult);
    }

    @Test
    public void testSerializeAndDeserializeDomainEvent_WithDom4JUpcasters() {
        SerializedObject<org.dom4j.Document> serializedEvent = testSubject.serialize(testEvent, org.dom4j.Document.class);
        Object actualResult = testSubject.deserialize(serializedEvent);
        assertEquals(testEvent, actualResult);
    }

    @Test
    public void testPackageAlias() throws UnsupportedEncodingException {
        testSubject.addPackageAlias("axones", "org.axonframework.eventsourcing");
        testSubject.addPackageAlias("axon", "org.axonframework");

        SerializedObject<byte[]> serialized = testSubject.serialize(new StubDomainEvent(), byte[].class);
        String asString = new String(serialized.getData(), "UTF-8");
        assertFalse("Package name found in:" +  asString, asString.contains("org.axonframework.domain"));
        StubDomainEvent deserialized = testSubject.deserialize(serialized);
        assertEquals(StubDomainEvent.class, deserialized.getClass());
        assertTrue(asString.contains("axones"));
    }

    @Test
    public void testAlias() throws UnsupportedEncodingException {
        testSubject.addAlias("stub", StubDomainEvent.class);

        SerializedObject<byte[]> serialized = testSubject.serialize(new StubDomainEvent(), byte[].class);
        String asString = new String(serialized.getData(), "UTF-8");
        assertFalse(asString.contains("org.axonframework.domain"));
        assertTrue(asString.contains("<stub"));
        StubDomainEvent deserialized = testSubject.deserialize(serialized);
        assertEquals(StubDomainEvent.class, deserialized.getClass());
    }

    @Test
    public void testFieldAlias() throws UnsupportedEncodingException {
        testSubject.addFieldAlias("relevantPeriod", TestEvent.class, "period");

        SerializedObject<byte[]> serialized = testSubject.serialize(testEvent, byte[].class);
        String asString = new String(serialized.getData(), "UTF-8");
        assertFalse(asString.contains("period"));
        assertTrue(asString.contains("<relevantPeriod"));
        TestEvent deserialized = testSubject.deserialize(serialized);
        assertNotNull(deserialized);
    }

    @Test
    public void testRevisionNumber() throws UnsupportedEncodingException {
        SerializedObject<byte[]> serialized = testSubject.serialize(new RevisionSpecifiedEvent(), byte[].class);
        assertNotNull(serialized);
        assertEquals("2", serialized.getType().getRevision());
        assertEquals(RevisionSpecifiedEvent.class.getName(), serialized.getType().getName());
    }

    @Test
    public void testSerializedTypeUsesClassAlias() throws UnsupportedEncodingException {
        testSubject.addAlias("rse", RevisionSpecifiedEvent.class);
        SerializedObject<byte[]> serialized = testSubject.serialize(new RevisionSpecifiedEvent(), byte[].class);
        assertNotNull(serialized);
        assertEquals("2", serialized.getType().getRevision());
        assertEquals("rse", serialized.getType().getName());
    }

    /**
     * Tests the scenario as described in <a href="http://code.google.com/p/axonframework/issues/detail?id=150">issue
     * #150</a>.
     */
    @Test
    public void testSerializeWithSpecialCharacters_WithDom4JUpcasters() {
        SerializedObject<byte[]> serialized = testSubject.serialize(new TestEvent(SPECIAL__CHAR__STRING), byte[].class);
        TestEvent deserialized = testSubject.deserialize(serialized);
        assertArrayEquals(SPECIAL__CHAR__STRING.getBytes(), deserialized.getName().getBytes());
    }

    /**
     * Tests the scenario as described in <a href="http://code.google.com/p/axonframework/issues/detail?id=150">issue
     * #150</a>.
     */
    @Test
    public void testSerializeWithSpecialCharacters_WithoutUpcasters() {
        SerializedObject<byte[]> serialized = testSubject.serialize(new TestEvent(SPECIAL__CHAR__STRING), byte[].class);
        TestEvent deserialized = testSubject.deserialize(serialized);
        assertEquals(SPECIAL__CHAR__STRING, deserialized.getName());
    }

    @Revision("2")
    public static class RevisionSpecifiedEvent {
    }

    public static class TestEvent implements Serializable {

        private static final long serialVersionUID = 1L;
        private String name;
        private LocalDate date;
        private Instant dateTime;
        private Period period;

        public TestEvent(String name) {
            this.name = name;
            this.date = LocalDate.now();
            this.dateTime = Instant.now();
            this.period = Period.ofDays(100);
        }

        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            TestEvent testEvent = (TestEvent) o;

            if (date != null ? !date.equals(testEvent.date) : testEvent.date != null) {
                return false;
            }
            if (dateTime != null ? !dateTime.equals(testEvent.dateTime) : testEvent.dateTime != null) {
                return false;
            }
            if (name != null ? !name.equals(testEvent.name) : testEvent.name != null) {
                return false;
            }
            if (period != null ? !period.equals(testEvent.period) : testEvent.period != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = name != null ? name.hashCode() : 0;
            result = 31 * result + (date != null ? date.hashCode() : 0);
            result = 31 * result + (dateTime != null ? dateTime.hashCode() : 0);
            result = 31 * result + (period != null ? period.hashCode() : 0);
            return result;
        }

    }
}
