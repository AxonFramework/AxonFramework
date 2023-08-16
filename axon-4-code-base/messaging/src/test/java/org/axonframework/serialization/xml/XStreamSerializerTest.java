/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.serialization.xml;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.reflection.AbstractReflectionConverter;
import org.axonframework.serialization.Revision;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.SerializedType;
import org.axonframework.serialization.SimpleSerializedObject;
import org.axonframework.utils.StubDomainEvent;
import org.dom4j.Document;
import org.junit.jupiter.api.*;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Objects;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link XStreamSerializer}.
 *
 * @author Allard Buijze
 */
class XStreamSerializerTest {

    private static final String SPECIAL__CHAR__STRING = "Special chars: '\"&;\n\\<>/\n\t";
    private static final String REGULAR_STRING = "Henk";

    private TestEvent testEvent;

    private XStreamSerializer testSubject;

    @BeforeEach
    void setUp() {
        this.testSubject = XStreamSerializer.builder()
                                            .xStream(new XStream())
                                            .build();
        this.testEvent = new TestEvent(REGULAR_STRING);
    }

    @Test
    void serializeAndDeserializeDomainEvent() {
        SerializedObject<byte[]> serializedEvent = testSubject.serialize(testEvent, byte[].class);
        Object actualResult = testSubject.deserialize(serializedEvent);
        assertTrue(actualResult instanceof TestEvent);
        TestEvent actualEvent = (TestEvent) actualResult;
        assertEquals(testEvent, actualEvent);
    }

    @Test
    void serializeAndDeserializeDomainEvent_WithXomUpcasters() {
        SerializedObject<nu.xom.Document> serializedEvent = testSubject.serialize(testEvent, nu.xom.Document.class);
        Object actualResult = testSubject.deserialize(serializedEvent);
        assertEquals(testEvent, actualResult);
    }

    @Test
    void serializeAndDeserializeDomainEvent_WithDom4JUpcasters() {
        SerializedObject<org.dom4j.Document> serializedEvent =
                testSubject.serialize(testEvent, org.dom4j.Document.class);
        Object actualResult = testSubject.deserialize(serializedEvent);
        assertEquals(testEvent, actualResult);
    }

    @Test
    void serializeAndDeserializeArray() {
        TestEvent toSerialize = new TestEvent("first");

        SerializedObject<String> serialized = testSubject.serialize(new TestEvent[]{toSerialize}, String.class);

        Class<?> actualType = testSubject.classForType(serialized.getType());
        assertTrue(actualType.isArray());
        assertEquals(TestEvent.class, actualType.getComponentType());
        TestEvent[] actual = testSubject.deserialize(serialized);
        assertEquals(1, actual.length);
        assertEquals(toSerialize.getName(), actual[0].getName());
    }

    @Test
    void serializeAndDeserializeList() {
        TestEvent toSerialize = new TestEvent("first");

        SerializedObject<String> serialized = testSubject.serialize(singletonList(toSerialize), String.class);

        List<TestEvent> actual = testSubject.deserialize(serialized);
        assertEquals(1, actual.size());
        assertEquals(toSerialize.getName(), actual.get(0).getName());
    }

    @Test
    void deserializeEmptyBytes() {
        assertEquals(Void.class, testSubject.classForType(SerializedType.emptyType()));
        assertNull(testSubject.deserialize(new SimpleSerializedObject<>(
                new byte[0], byte[].class, SerializedType.emptyType())
        ));
    }

    @Test
    void packageAlias() {
        testSubject.addPackageAlias("axon-eh", "org.axonframework.utils");
        testSubject.addPackageAlias("axon", "org.axonframework");

        SerializedObject<byte[]> serialized = testSubject.serialize(new StubDomainEvent(), byte[].class);
        String asString = new String(serialized.getData(), StandardCharsets.UTF_8);
        assertFalse(asString.contains("org.axonframework.domain"), "Package name found in:" + asString);
        StubDomainEvent deserialized = testSubject.deserialize(serialized);
        assertEquals(StubDomainEvent.class, deserialized.getClass());
        assertTrue(asString.contains("axon-eh"));
    }

    @Test
    void deserializeNullValue() {
        SerializedObject<byte[]> serializedNull = testSubject.serialize(null, byte[].class);
        assertEquals("empty", serializedNull.getType().getName());
        SimpleSerializedObject<byte[]> serializedNullString = new SimpleSerializedObject<>(
                serializedNull.getData(), byte[].class, testSubject.typeForClass(String.class)
        );
        assertNull(testSubject.deserialize(serializedNull));
        assertNull(testSubject.deserialize(serializedNullString));
    }

    @Test
    void alias() {
        testSubject.addAlias("stub", StubDomainEvent.class);

        SerializedObject<byte[]> serialized = testSubject.serialize(new StubDomainEvent(), byte[].class);
        String asString = new String(serialized.getData(), StandardCharsets.UTF_8);
        assertFalse(asString.contains("org.axonframework.domain"));
        assertTrue(asString.contains("<stub"));
        StubDomainEvent deserialized = testSubject.deserialize(serialized);
        assertEquals(StubDomainEvent.class, deserialized.getClass());
    }

    @Test
    void fieldAlias() {
        testSubject.addFieldAlias("relevantPeriod", TestEvent.class, "period");

        SerializedObject<byte[]> serialized = testSubject.serialize(testEvent, byte[].class);
        String asString = new String(serialized.getData(), StandardCharsets.UTF_8);
        assertFalse(asString.contains("period"));
        assertTrue(asString.contains("<relevantPeriod"));
        TestEvent deserialized = testSubject.deserialize(serialized);
        assertNotNull(deserialized);
    }

    @Test
    void revisionNumber() {
        SerializedObject<byte[]> serialized = testSubject.serialize(new RevisionSpecifiedEvent(), byte[].class);
        assertNotNull(serialized);
        assertEquals("2", serialized.getType().getRevision());
        assertEquals(RevisionSpecifiedEvent.class.getName(), serialized.getType().getName());
    }

    @SuppressWarnings("Duplicates")
    @Test
    void serializedTypeUsesClassAlias() {
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
    void serializeWithSpecialCharacters_WithDom4JUpcasters() {
        SerializedObject<byte[]> serialized = testSubject.serialize(new TestEvent(SPECIAL__CHAR__STRING), byte[].class);
        TestEvent deserialized = testSubject.deserialize(serialized);
        assertArrayEquals(SPECIAL__CHAR__STRING.getBytes(), deserialized.getName().getBytes());
    }

    @Test
    void serializeNullValue() {
        SerializedObject<byte[]> serialized = testSubject.serialize(null, byte[].class);
        String deserialized = testSubject.deserialize(serialized);
        assertNull(deserialized);
    }

    /**
     * Tests the scenario as described in <a href="http://code.google.com/p/axonframework/issues/detail?id=150">issue
     * #150</a>.
     */
    @Test
    void serializeWithSpecialCharacters_WithoutUpcasters() {
        SerializedObject<byte[]> serialized = testSubject.serialize(new TestEvent(SPECIAL__CHAR__STRING), byte[].class);
        TestEvent deserialized = testSubject.deserialize(serialized);
        assertEquals(SPECIAL__CHAR__STRING, deserialized.getName());
    }

    @Test
    void unknownPropertiesAreIgnoredWhenConfiguringLenientDeserialization() {
        testSubject = XStreamSerializer.builder()
                                       .xStream(new XStream())
                                       .lenientDeserialization()
                                       .build();

        SerializedObject<Document> serialized = testSubject.serialize(testEvent, Document.class);
        Document data = serialized.getData();
        data.getRootElement().addElement("unknown").setText("Ignored");

        TestEvent actual = testSubject.deserialize(new SimpleSerializedObject<>(
                data, Document.class, serialized.getType())
        );

        assertEquals(testEvent, actual);
    }

    @Test
    void unknownPropertiesFailDeserializationByDefault() {
        testSubject = XStreamSerializer.builder()
                                       .xStream(new XStream())
                                       .build();

        SerializedObject<Document> serialized = testSubject.serialize(testEvent, Document.class);
        Document data = serialized.getData();
        data.getRootElement().addElement("unknown").setText("Ignored");

        assertThrows(
                AbstractReflectionConverter.UnknownFieldException.class,
                () -> testSubject.deserialize(new SimpleSerializedObject<>(data, Document.class, serialized.getType()))
        );
    }

    @Test
    void disableAxonTypeSecurity() {
        XStream xStream = mock(XStream.class);

        XStreamSerializer.builder()
                         .xStream(xStream)
                         .disableAxonTypeSecurity()
                         .build();

        verify(xStream, times(0)).allowTypesByWildcard(new String[]{"org.axonframework.**"});

        // Axon types are added as wildcards by default.
        XStreamSerializer.builder()
                         .xStream(xStream)
                         .build();

        verify(xStream).allowTypesByWildcard(new String[]{"org.axonframework.**"});
    }

    @Revision("2")
    public static class RevisionSpecifiedEvent {

    }

    public static class TestEvent implements Serializable {

        private static final long serialVersionUID = 1L;
        private final String name;
        private final LocalDate date;
        private final Instant dateTime;
        private final Period period;

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

            if (!Objects.equals(date, testEvent.date)) {
                return false;
            }
            if (!Objects.equals(dateTime, testEvent.dateTime)) {
                return false;
            }
            if (!Objects.equals(name, testEvent.name)) {
                return false;
            }
            return Objects.equals(period, testEvent.period);
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
