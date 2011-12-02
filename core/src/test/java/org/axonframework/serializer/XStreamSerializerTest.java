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

package org.axonframework.serializer;

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.MetaData;
import org.axonframework.domain.StringAggregateIdentifier;
import org.axonframework.domain.StubDomainEvent;
import org.axonframework.domain.UUIDAggregateIdentifier;
import org.dom4j.Document;
import org.joda.time.DateMidnight;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.junit.*;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class XStreamSerializerTest {

    private XStreamSerializer testSubject;
    private static final String SPECIAL__CHAR__STRING = "Special chars: '\"&;\n\\<>/\n\t";

    @Before
    public void setUp() {
        this.testSubject = new XStreamSerializer();
    }

    @Test
    public void testSerializeAndDeserializeDomainEvent() {
        SerializedObject serializedEvent = testSubject.serialize(new TestEvent("Henk"));
        Object actualResult = testSubject.deserialize(serializedEvent);
        assertTrue(actualResult instanceof TestEvent);
        TestEvent actualEvent = (TestEvent) actualResult;
        assertEquals("Henk", actualEvent.getName());
    }

    @Test
    public void testPackageAlias() throws UnsupportedEncodingException {
        testSubject.addPackageAlias("axondomain", "org.axonframework.domain");
        testSubject.addPackageAlias("axon", "org.axonframework");

        SerializedObject serialized = testSubject.serialize(new GenericDomainEventMessage<StubDomainEvent>(
                new UUIDAggregateIdentifier(),
                (long) 1,
                new StubDomainEvent(), MetaData.emptyInstance()
        ));
        String asString = new String(serialized.getData(), "UTF-8");
        assertFalse("Package name found in:" + asString, asString.contains("org.axonframework.domain"));
        DomainEventMessage deserialized = (DomainEventMessage) testSubject.deserialize(serialized);
        assertEquals(1L, deserialized.getSequenceNumber());
        assertTrue(asString.contains("axondomain"));
    }

    @Test
    public void testAlias() throws UnsupportedEncodingException {
        testSubject.addAlias("stub", StubDomainEvent.class);

        SerializedObject serialized = testSubject.serialize(new GenericDomainEventMessage<StubDomainEvent>(
                new UUIDAggregateIdentifier(),
                (long) 1,
                new StubDomainEvent(), MetaData.emptyInstance()
        ));
        String asString = new String(serialized.getData(), "UTF-8");
        assertFalse(asString.contains("org.axonframework.domain"));
        assertTrue(asString.contains("<payload class=\"stub\""));
        DomainEventMessage deserialized = (DomainEventMessage) testSubject.deserialize(serialized);
        assertEquals(1L, deserialized.getSequenceNumber());
    }

    @Test
    public void testFieldAlias() throws UnsupportedEncodingException {
        testSubject.addFieldAlias("relevantPeriod", TestEvent.class, "period");

        SerializedObject serialized = testSubject.serialize(new TestEvent("hello"));
        String asString = new String(serialized.getData(), "UTF-8");
        assertFalse(asString.contains("period"));
        assertTrue(asString.contains("<relevantPeriod"));
        TestEvent deserialized = (TestEvent) testSubject.deserialize(serialized);
        assertNotNull(deserialized);
    }

    @Test
    public void testRevisionNumber() throws UnsupportedEncodingException {
        SerializedObject serialized = testSubject.serialize(new RevisionSpecifiedEvent());
        assertNotNull(serialized);
        assertEquals(2, serialized.getType().getRevision());
        assertEquals(RevisionSpecifiedEvent.class.getName(), serialized.getType().getName());
    }

    @Test
    public void testSerializedTypeUsesClassAlias() throws UnsupportedEncodingException {
        testSubject.addAlias("rse", RevisionSpecifiedEvent.class);
        SerializedObject serialized = testSubject.serialize(new RevisionSpecifiedEvent());
        assertNotNull(serialized);
        assertEquals(2, serialized.getType().getRevision());
        assertEquals("rse", serialized.getType().getName());
    }

    /**
     * Tests the scenario as described in <a href="http://code.google.com/p/axonframework/issues/detail?id=150">issue
     * #150</a>.
     */
    @Test
    public void testSerializeWithSpecialCharacters_WithDom4JUpcasters() {
        testSubject.setUpcasters(Arrays.<Upcaster>asList(new Upcaster() {

            @Override
            public boolean canUpcast(SerializedType serializedType) {
                return true;
            }

            @Override
            public Class<?> expectedRepresentationType() {
                return Document.class;
            }

            @Override
            public IntermediateRepresentation upcast(IntermediateRepresentation intermediateRepresentation) {
                return intermediateRepresentation;
            }

            @Override
            public SerializedType upcast(SerializedType serializedType) {
                return serializedType;
            }
        }));
        SerializedObject serialized = testSubject.serialize(new TestEvent(SPECIAL__CHAR__STRING));
        TestEvent deserialized = (TestEvent) testSubject.deserialize(serialized);
        assertArrayEquals(SPECIAL__CHAR__STRING.getBytes(), deserialized.getName().getBytes());
    }

    /**
     * Tests the scenario as described in <a href="http://code.google.com/p/axonframework/issues/detail?id=150">issue
     * #150</a>.
     */
    @Test
    public void testSerializeWithSpecialCharacters_WithoutUpcasters() {
        SerializedObject serialized = testSubject.serialize(new TestEvent(SPECIAL__CHAR__STRING));
        TestEvent deserialized = (TestEvent) testSubject.deserialize(serialized);
        assertEquals(SPECIAL__CHAR__STRING, deserialized.getName());
    }

    /**
     * Tests the scenario as described in <a href="http://code.google.com/p/axonframework/issues/detail?id=188">issue
     * #188</a>.
     */
    @Test
    public void testSerializeWithCustomAggregateIdentifier() {
        SerializedObject serialized = testSubject.serialize(new CustomIdentifierEvent("Some name",
                                                                                      new SomeAggregateId("ok")));
        CustomIdentifierEvent deserialized = (CustomIdentifierEvent) testSubject.deserialize(serialized);
        assertEquals("ok", deserialized.getSomeAggregateId().asString());
    }

    @Revision(2)
    public static class RevisionSpecifiedEvent {
    }

    public static class TestEvent {

        private static final long serialVersionUID = 1657550542124835062L;
        private String name;
        private DateMidnight date;
        private DateTime dateTime;
        private Period period;

        public TestEvent(String name) {
            this.name = name;
            this.date = new DateMidnight();
            this.dateTime = new DateTime();
            this.period = new Period(100);
        }

        public String getName() {
            return name;
        }
    }

    public static class CustomIdentifierEvent {

        private static final long serialVersionUID = 1657550542124835062L;
        private String name;
        private DateMidnight date;
        private DateTime dateTime;
        private Period period;
        private SomeAggregateId someAggregateId;

        public CustomIdentifierEvent(String name, SomeAggregateId someAggregateId) {
            this.name = name;
            this.someAggregateId = someAggregateId;
            this.date = new DateMidnight();
            this.dateTime = new DateTime();
            this.period = new Period(100);
        }

        public String getName() {
            return name;
        }

        public SomeAggregateId getSomeAggregateId() {
            return someAggregateId;
        }
    }

    public static class SomeAggregateId extends StringAggregateIdentifier {

        public SomeAggregateId(String id) {
            super(id);
        }
    }
}
