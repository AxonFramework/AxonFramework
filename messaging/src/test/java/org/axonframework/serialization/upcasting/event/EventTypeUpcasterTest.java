/*
 * Copyright (c) 2010-2021. Axon Framework
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

package org.axonframework.serialization.upcasting.event;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.AbstractEventEntry;
import org.axonframework.eventhandling.EventData;
import org.axonframework.serialization.SerializedType;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedType;
import org.axonframework.serialization.TestSerializer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.Serializable;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Test class to validate the {@link EventTypeUpcaster} which for testing purposes creates a stub implementation of the
 * {@code EventTypeUpcaster}.
 *
 * @author Steven van Beelen
 */
class EventTypeUpcasterTest {

    public static final String EXPECTED_PAYLOAD_TYPE = TestEvent.class.getName();
    public static final String EXPECTED_REVISION = "1";
    public static final String UPCASTED_PAYLOAD_TYPE = RenamedTestEvent.class.getName();
    public static final String UPCASTED_REVISION = "2";

    private final EventTypeUpcaster testSubject =
            new EventTypeUpcaster(EXPECTED_PAYLOAD_TYPE, EXPECTED_REVISION, UPCASTED_PAYLOAD_TYPE, UPCASTED_REVISION);

    private static final String SOURCE_METHOD_NAME = "provideSerializers";
    private static Stream<Arguments> provideSerializers() {
        return Stream.of(
                Arguments.of(TestSerializer.XSTREAM.getSerializer()),
                Arguments.of(TestSerializer.JACKSON.getSerializer()),
                Arguments.of(TestSerializer.JACKSON_ONLY_ACCEPT_CONSTRUCTOR_PARAMETERS.getSerializer())
        );
    }

    @Test
    void testUpcasterBuilderFailsForNullExpectedPayloadTypeClass() {
        assertThrows(
                AxonConfigurationException.class, () -> EventTypeUpcaster.from((Class<?>) null, EXPECTED_REVISION)
        );
    }

    @Test
    void testUpcasterBuilderFailsForNullExpectedPayloadType() {
        assertThrows(
                AxonConfigurationException.class, () -> EventTypeUpcaster.from((String) null, EXPECTED_REVISION)
        );
    }

    @Test
    void testUpcasterBuilderFailsForEmptyExpectedPayloadType() {
        assertThrows(
                AxonConfigurationException.class, () -> EventTypeUpcaster.from("", EXPECTED_REVISION)
        );
    }

    @Test
    void testUpcasterBuilderFailsForNullUpcastedPayloadTypeClass() {
        EventTypeUpcaster.Builder testSubject = EventTypeUpcaster.from(EXPECTED_PAYLOAD_TYPE, EXPECTED_REVISION);
        assertThrows(
                AxonConfigurationException.class, () -> testSubject.to((Class<?>) null, UPCASTED_REVISION)
        );
    }

    @Test
    void testUpcasterBuilderFailsForNullUpcastedPayloadType() {
        EventTypeUpcaster.Builder testSubject = EventTypeUpcaster.from(EXPECTED_PAYLOAD_TYPE, EXPECTED_REVISION);
        assertThrows(
                AxonConfigurationException.class, () -> testSubject.to((String) null, UPCASTED_REVISION)
        );
    }

    @Test
    void testUpcasterBuilderFailsForEmptyUpcastedPayloadType() {
        EventTypeUpcaster.Builder testSubject = EventTypeUpcaster.from(EXPECTED_PAYLOAD_TYPE, EXPECTED_REVISION);
        assertThrows(
                AxonConfigurationException.class, () -> testSubject.to("", UPCASTED_REVISION)
        );
    }

    @ParameterizedTest
    @MethodSource(SOURCE_METHOD_NAME)
    void testCanUpcastReturnsTrueForMatchingPayloadTypeAndRevision(Serializer serializer) {
        EventData<?> testEventData = new TestEventEntry(EXPECTED_PAYLOAD_TYPE, EXPECTED_REVISION, serializer);
        IntermediateEventRepresentation testRepresentation = new InitialEventRepresentation(testEventData, serializer);

        assertTrue(testSubject.canUpcast(testRepresentation));
    }

    @ParameterizedTest
    @MethodSource(SOURCE_METHOD_NAME)
    void testCanUpcastReturnsFalseForIncorrectPayloadType(Serializer serializer) {
        EventData<?> testEventData = new TestEventEntry("some-non-matching-payload-type", EXPECTED_REVISION, serializer);
        IntermediateEventRepresentation testRepresentation = new InitialEventRepresentation(testEventData, serializer);

        assertFalse(testSubject.canUpcast(testRepresentation));
    }

    @ParameterizedTest
    @MethodSource(SOURCE_METHOD_NAME)
    void testCanUpcastReturnsFalseForIncorrectRevision(Serializer serializer) {
        EventData<?> testEventData = new TestEventEntry(EXPECTED_PAYLOAD_TYPE, "some-non-matching-revision", serializer);
        IntermediateEventRepresentation testRepresentation = new InitialEventRepresentation(testEventData, serializer);

        assertFalse(testSubject.canUpcast(testRepresentation));
    }

    @Test
    void testIsExpectedPayloadType() {
        assertTrue(testSubject.isExpectedPayloadType(EXPECTED_PAYLOAD_TYPE));
        assertFalse(testSubject.isExpectedPayloadType(UPCASTED_PAYLOAD_TYPE));
    }

    @Test
    void testIsExpectedRevision() {
        assertTrue(testSubject.isExpectedRevision(EXPECTED_REVISION));
        assertFalse(testSubject.isExpectedRevision(UPCASTED_REVISION));
    }

    @ParameterizedTest
    @MethodSource(SOURCE_METHOD_NAME)
    void testDoUpcast(Serializer serializer) {
        EventData<?> testEventData = new TestEventEntry(EXPECTED_PAYLOAD_TYPE, EXPECTED_REVISION, serializer);
        InitialEventRepresentation testRepresentation = new InitialEventRepresentation(testEventData, serializer);

        IntermediateEventRepresentation result = testSubject.doUpcast(testRepresentation);
        SerializedType resultType = result.getType();
        assertEquals(UPCASTED_PAYLOAD_TYPE, resultType.getName());
        assertEquals(UPCASTED_REVISION, resultType.getRevision());
    }

    @ParameterizedTest
    @MethodSource(SOURCE_METHOD_NAME)
    void testShouldDeserializeToNewType(Serializer serializer) {
        final EventData<?> testEventData = new TestEventEntry(EXPECTED_PAYLOAD_TYPE, EXPECTED_REVISION, serializer);
        final InitialEventRepresentation testRepresentation = new InitialEventRepresentation(testEventData, serializer);

        final IntermediateEventRepresentation result = testSubject.doUpcast(testRepresentation);

        assertInstanceOf(RenamedTestEvent.class, serializer.deserialize(result.getData()));
    }

    @Test
    void testUpcastedType() {
        SerializedType expectedType = new SimpleSerializedType(UPCASTED_PAYLOAD_TYPE, UPCASTED_REVISION);
        assertEquals(expectedType, testSubject.upcastedType());
    }

    /**
     * Test {@link AbstractEventEntry} implementation which only allows adjusting the {@code payloadType} and {@code
     * payloadRevision}. All other {@code AbstractEventEntry} parameters are defaulted.
     */
    private static class TestEventEntry extends AbstractEventEntry<byte[]> {
        private static final TestEvent payload = new TestEvent("payload");
        private static final TestEvent metaData = new TestEvent("metadata");

        public TestEventEntry(String payloadType, String payloadRevision, Serializer serializer) {
            super("eventIdentifier", "timestamp", payloadType, payloadRevision, serializer.serialize(payload, byte[].class).getData(), serializer.serialize(metaData, byte[].class).getData());
        }
    }

    /**
     * A simple event used for testing
     */
    private static class TestEvent implements Serializable {
        private String testField;

        public TestEvent() {
        }

        public TestEvent(String testField) {
            this.testField = testField;
        }

        public String getTestField() {
            return testField;
        }

        public void setTestField(String testField) {
            this.testField = testField;
        }
    }

    /**
     * Latest revision of {@code TestEvent} (renamed event type)
     */
    private static class RenamedTestEvent implements Serializable {
        private String testField;

        public RenamedTestEvent() {
        }

        public RenamedTestEvent(String testField) {
            this.testField = testField;
        }

        public String getTestField() {
            return testField;
        }

        public void setTestField(String testField) {
            this.testField = testField;
        }
    }
}