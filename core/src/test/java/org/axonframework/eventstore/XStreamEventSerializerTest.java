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
import org.axonframework.domain.StringAggregateIdentifier;
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
    private static final String SPECIAL__CHAR__STRING = "Special chars: '\"&;\n\\<>/\r";

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
        assertFalse("Package name found in:" + new String(serialized), asString.contains("org.axonframework.domain"));
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
        assertTrue(asString.contains("<aggId"));
        StubDomainEvent deserialized = (StubDomainEvent) testSubject.deserialize(serialized);
        assertEquals(new Long(1), deserialized.getSequenceNumber());
    }

    @Test
    public void testBackwardsCompatibility() throws UnsupportedEncodingException {
        byte[] serializedEvent = (
                "<org.axonframework.eventstore.XStreamEventSerializerTest_-TestEvent eventRevision=\"0\">"
                        + "<metaData><values>"
                        + "<entry><string>_timestamp</string><dateTime>2011-08-12T13:09:40.849+02:00</dateTime></entry>"
                        + "<entry><string>_identifier</string><uuid>5e3cdec7-ad3d-4aeb-8826-34a1bddd4849</uuid></entry>"
                        + "<entry><string>someMetaData</string><string>someValue</string></entry>"
                        + "</values></metaData>"
                        + "<sequenceNumber>0</sequenceNumber>"
                        + "<aggregateIdentifier>6c14e5dc-d4bc-4fea-bcfc-ecb59855652e</aggregateIdentifier>"
                        + "<name>Henk</name><date>2011-08-12T00:00:00.000+02:00</date>"
                        + "<dateTime>2011-08-12T13:09:40.925+02:00</dateTime>"
                        + "<period>PT0.100S</period>"
                        + "</org.axonframework.eventstore.XStreamEventSerializerTest_-TestEvent>")
                .getBytes("UTF-8");
        TestEvent event = (TestEvent) testSubject.deserialize(serializedEvent);
        assertNotNull(event.getAggregateIdentifier());
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
        byte[] serialized = testSubject.serialize(new TestEvent(SPECIAL__CHAR__STRING));
        TestEvent deserialized = (TestEvent) testSubject.deserialize(serialized);
        assertEquals(SPECIAL__CHAR__STRING, deserialized.getName());
    }

    /**
     * Tests the scenario as described in <a href="http://code.google.com/p/axonframework/issues/detail?id=150">issue
     * #150</a>.
     */
    @Test
    public void testSerializeWithSpecialCharacters_WithoutUpcasters() {
        byte[] serialized = testSubject.serialize(new TestEvent(SPECIAL__CHAR__STRING));
        TestEvent deserialized = (TestEvent) testSubject.deserialize(serialized);
        assertEquals(SPECIAL__CHAR__STRING, deserialized.getName());
    }

    /**
     * Tests the scenario as described in <a href="http://code.google.com/p/axonframework/issues/detail?id=188">issue
     * #188</a>.
     */
    @Test
    public void testSerializeWithCustomAggregateIdentifier() {
        byte[] serialized = testSubject.serialize(new CustomIdentifierEvent("Some name", new SomeAggregateId("ok")));
        System.out.println(new String(serialized));
        CustomIdentifierEvent deserialized = (CustomIdentifierEvent) testSubject.deserialize(serialized);
        assertEquals("ok", deserialized.getSomeAggregateId().asString());
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

    public static class CustomIdentifierEvent extends DomainEvent {

        private static final long serialVersionUID = 1657550542124835062L;
        private String name;
        private DateMidnight date;
        private DateTime dateTime;
        private Period period;
        private SomeAggregateId someAggregateId;

        public CustomIdentifierEvent(String name, SomeAggregateId someAggregateId) {
            super(0, someAggregateId);
            this.name = name;
            this.someAggregateId = someAggregateId;
            this.date = new DateMidnight();
            this.dateTime = new DateTime();
            this.period = new Period(100);
            addMetaData("someMetaData", "someValue");
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
