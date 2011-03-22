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

package org.axonframework.eventstore.legacy;

import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.UUIDAggregateIdentifier;
import org.axonframework.eventstore.EventUpcaster;
import org.axonframework.eventstore.XStreamEventSerializer;
import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.dom4j.util.NodeComparator;
import org.joda.time.DateMidnight;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.junit.*;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class LegacyAxonEventUpcasterTest {

    private XStreamEventSerializer serializer;
    private static final String NEW_SKOOL_MESSAGE =
            "<org.axonframework.eventstore.legacy.LegacyAxonEventUpcasterTest_-TestEvent eventRevision=\"0\">"
                    + "<metaData><values>"
                    + "<entry><string>_timestamp</string><localDateTime>2010-09-15T21:43:01.000</localDateTime></entry>"
                    + "<entry><string>_identifier</string><uuid>36f20a77-cdba-4e63-8c02-825486aad301</uuid></entry>"
                    + "</values></metaData>"
                    + "<sequenceNumber>0</sequenceNumber>"
                    + "<aggregateIdentifier>62daf7f6-c3ab-4179-a212-6b1da2a6ec72</aggregateIdentifier>"
                    + "<name>oldskool</name>"
                    + "<date>2010-09-15T00:00:00.000+02:00</date>"
                    + "<dateTime>2010-09-15T21:43:01.078+02:00</dateTime>"
                    + "<period>PT0.100S</period>"
                    + "</org.axonframework.eventstore.legacy.LegacyAxonEventUpcasterTest_-TestEvent>";
    private static final String NEW_SKOOL_MESSAGE_WITH_ADDITIONAL_METADATA =
            "<org.axonframework.eventstore.legacy.LegacyAxonEventUpcasterTest_-TestEvent eventRevision=\"0\">"
                    + "<metaData><values>"
                    + "<entry><string>_timestamp</string><localDateTime>2010-09-15T21:43:01.000</localDateTime></entry>"
                    + "<entry><string>_identifier</string><uuid>36f20a77-cdba-4e63-8c02-825486aad301</uuid></entry>"
                    + "<entry><string>someKey</string><string>someValue</string></entry>"
                    + "</values></metaData>"
                    + "<sequenceNumber>0</sequenceNumber>"
                    + "<aggregateIdentifier>62daf7f6-c3ab-4179-a212-6b1da2a6ec72</aggregateIdentifier>"
                    + "<name>oldskool</name>"
                    + "<date>2010-09-15T00:00:00.000+02:00</date>"
                    + "<dateTime>2010-09-15T21:43:01.078+02:00</dateTime>"
                    + "<period>PT0.100S</period>"
                    + "</org.axonframework.eventstore.legacy.LegacyAxonEventUpcasterTest_-TestEvent>";
    private static final String OLD_SKOOL_MESSAGE =
            "<org.axonframework.eventstore.legacy.LegacyAxonEventUpcasterTest_-TestEvent>"
                    + "<timestamp>2010-09-15T21:43:01.000</timestamp>"
                    + "<eventIdentifier>36f20a77-cdba-4e63-8c02-825486aad301</eventIdentifier>"
                    + "<sequenceNumber>0</sequenceNumber>"
                    + "<aggregateIdentifier>62daf7f6-c3ab-4179-a212-6b1da2a6ec72</aggregateIdentifier>"
                    + "<name>oldskool</name>"
                    + "<date>2010-09-15T00:00:00.000+02:00</date>"
                    + "<dateTime>2010-09-15T21:43:01.078+02:00</dateTime>"
                    + "<period>PT0.100S</period>"
                    + "</org.axonframework.eventstore.legacy.LegacyAxonEventUpcasterTest_-TestEvent>";

    @Before
    public void setUp() {
        this.serializer = new XStreamEventSerializer();
        LegacyAxonEventUpcaster testSubject = new LegacyAxonEventUpcaster();
        serializer.setEventUpcasters(Arrays.<EventUpcaster<Document>>asList(testSubject));
    }

    /**
     * Test to make sure that events created during the time events did not have an explicit MetaData object can still
     * be read.
     *
     * @throws java.io.UnsupportedEncodingException
     *
     */
    @Test
    public void testDeserializeOldStyleEvent() throws Exception {
        byte[] oldskoolEvent = (OLD_SKOOL_MESSAGE).getBytes("utf-8");
        TestEvent testEvent = (TestEvent) serializer.deserialize(oldskoolEvent);
        assertEquals(0l, testEvent.getEventRevision());
        assertEquals("62daf7f6-c3ab-4179-a212-6b1da2a6ec72", testEvent.getAggregateIdentifier().asString());
        assertEquals(new DateTime("2010-09-15T21:43:01.000"), testEvent.getTimestamp());
        assertNull(testEvent.getMetaDataValue("someValueThatDoesNotExist"));
        assertNotNull(testEvent.hashCode());
    }

    /**
     * Test to make sure that new events created can be read.
     *
     * @throws java.io.UnsupportedEncodingException
     *
     */
    @Test
    public void testDeserializeNewStyleEvent() throws Exception {
        byte[] newskoolEvent = (NEW_SKOOL_MESSAGE_WITH_ADDITIONAL_METADATA).getBytes("utf-8");
        TestEvent testEvent = (TestEvent) serializer.deserialize(newskoolEvent);
        assertEquals(0l, testEvent.getEventRevision());
        assertEquals("62daf7f6-c3ab-4179-a212-6b1da2a6ec72", testEvent.getAggregateIdentifier().asString());
        assertEquals(new DateTime("2010-09-15T21:43:01.000"), testEvent.getTimestamp());
        assertEquals("someValue", testEvent.getMetaDataValue("someKey"));
        assertNotNull(testEvent.hashCode());
    }

    @Test
    public void testSerializeAndDeserialize() {

        byte[] serializedEvent = serializer.serialize(new TestEvent("Testing123"));
        TestEvent testEvent = (TestEvent) serializer.deserialize(serializedEvent);
        Assert.assertEquals("Testing123", testEvent.getName());
    }

    @Test
    public void testLegacyUpcasterAddsRevisionAttributeToDocument() throws Exception {
        SAXReader reader = new SAXReader();
        Document oldDoc = reader.read(new ByteArrayInputStream(OLD_SKOOL_MESSAGE.getBytes("utf-8")));
        Document newDoc = reader.read(new ByteArrayInputStream(NEW_SKOOL_MESSAGE.getBytes("utf-8")));

        Document upcastedDoc = new LegacyAxonEventUpcaster().upcast(oldDoc);

        assertEquals(newDoc.getRootElement().attribute("eventRevision").getText(),
                     upcastedDoc.getRootElement().attribute(
                             "eventRevision").getText());
    }

    @SuppressWarnings({"unchecked"})
    @Test
    public void testUpcastedOldskoolDocumentEqualsNewskoolDocument() throws Exception {

        SAXReader reader = new SAXReader();
        Document oldDoc = reader.read(new ByteArrayInputStream(OLD_SKOOL_MESSAGE.getBytes("utf-8")));
        Document newDoc = reader.read(new ByteArrayInputStream(NEW_SKOOL_MESSAGE.getBytes("utf-8")));
        Document upcastedDoc = new LegacyAxonEventUpcaster().upcast(oldDoc);

        // Check if root element is the same
        assertEquals(newDoc.getRootElement().getName(), upcastedDoc.getRootElement().getName());
        // and has the same attributes
        Set<Attribute> attributes = new HashSet<Attribute>(newDoc.getRootElement().attributes());
        attributes.addAll(upcastedDoc.getRootElement().attributes());
        for (Attribute attribute : attributes) {
            assertEquals(newDoc.getRootElement().attribute(attribute.getName()).getText(),
                         upcastedDoc.getRootElement().attribute(attribute.getName()).getText());
        }

        // check if all root's children are identical
        Set<String> childNames = new HashSet<String>();
        for (Object element : newDoc.getRootElement().elements()) {
            childNames.add(((Element) element).getName());
        }

        for (Object element : upcastedDoc.getRootElement().elements()) {
            childNames.add(((Element) element).getName());
        }

        NodeComparator nodeComparator = new NodeComparator();
        for (String childName : childNames) {
            assertEquals(0, nodeComparator.compare(newDoc.getRootElement().element(childName),
                                                   upcastedDoc.getRootElement().element(childName)));
        }
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
            this.period = new Period(1000);
            addMetaData("some", "value");
            addMetaData("other", 2);
        }

        public String getName() {
            return name;
        }
    }
}
