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

package org.axonframework.serialization.upcasting.event;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.AbstractEventEntry;
import org.axonframework.eventhandling.EventData;
import org.axonframework.serialization.SerializedType;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedType;
import org.axonframework.serialization.TestSerializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

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

    private static final String SOURCE_METHOD_NAME = "provideSerializers";

    @SuppressWarnings("unused") // Used by all parameterized tests
    private static Stream<Arguments> provideSerializers() {
        return Stream.of(
                Arguments.of(TestSerializer.XSTREAM.getSerializer()),
                Arguments.of(TestSerializer.JACKSON.getSerializer()),
                Arguments.of(TestSerializer.JACKSON_ONLY_ACCEPT_CONSTRUCTOR_PARAMETERS.getSerializer())
        );
    }

    private final EventTypeUpcaster testSubject =
            new EventTypeUpcaster(EXPECTED_PAYLOAD_TYPE, EXPECTED_REVISION, UPCASTED_PAYLOAD_TYPE, UPCASTED_REVISION);

    @Test
    void upcasterBuilderFailsForNullExpectedPayloadTypeClass() {
        assertThrows(
                AxonConfigurationException.class, () -> EventTypeUpcaster.from((Class<?>) null, EXPECTED_REVISION)
        );
    }

    @Test
    void upcasterBuilderFailsForNullExpectedPayloadType() {
        assertThrows(
                AxonConfigurationException.class, () -> EventTypeUpcaster.from((String) null, EXPECTED_REVISION)
        );
    }

    @Test
    void upcasterBuilderFailsForEmptyExpectedPayloadType() {
        assertThrows(
                AxonConfigurationException.class, () -> EventTypeUpcaster.from("", EXPECTED_REVISION)
        );
    }

    @Test
    void upcasterBuilderFailsForNullUpcastedPayloadTypeClass() {
        EventTypeUpcaster.Builder testSubject = EventTypeUpcaster.from(EXPECTED_PAYLOAD_TYPE, EXPECTED_REVISION);
        assertThrows(
                AxonConfigurationException.class, () -> testSubject.to((Class<?>) null, UPCASTED_REVISION)
        );
    }

    @Test
    void upcasterBuilderFailsForNullUpcastedPayloadType() {
        EventTypeUpcaster.Builder testSubject = EventTypeUpcaster.from(EXPECTED_PAYLOAD_TYPE, EXPECTED_REVISION);
        assertThrows(
                AxonConfigurationException.class, () -> testSubject.to((String) null, UPCASTED_REVISION)
        );
    }

    @Test
    void upcasterBuilderFailsForEmptyUpcastedPayloadType() {
        EventTypeUpcaster.Builder testSubject = EventTypeUpcaster.from(EXPECTED_PAYLOAD_TYPE, EXPECTED_REVISION);
        assertThrows(
                AxonConfigurationException.class, () -> testSubject.to("", UPCASTED_REVISION)
        );
    }

    @ParameterizedTest
    @MethodSource(SOURCE_METHOD_NAME)
    void canUpcastReturnsTrueForMatchingPayloadTypeAndRevision(Serializer serializer) {
        EventData<?> testEventData = new TestEventEntry(EXPECTED_PAYLOAD_TYPE, EXPECTED_REVISION, serializer);
        IntermediateEventRepresentation testRepresentation = new InitialEventRepresentation(testEventData, serializer);

        assertTrue(testSubject.canUpcast(testRepresentation));
    }

    @ParameterizedTest
    @MethodSource(SOURCE_METHOD_NAME)
    void canUpcastReturnsFalseForIncorrectPayloadType(Serializer serializer) {
        EventData<?> testEventData =
                new TestEventEntry("some-non-matching-payload-type", EXPECTED_REVISION, serializer);
        IntermediateEventRepresentation testRepresentation = new InitialEventRepresentation(testEventData, serializer);

        assertFalse(testSubject.canUpcast(testRepresentation));
    }

    @ParameterizedTest
    @MethodSource(SOURCE_METHOD_NAME)
    void canUpcastReturnsFalseForIncorrectRevision(Serializer serializer) {
        EventData<?> testEventData =
                new TestEventEntry(EXPECTED_PAYLOAD_TYPE, "some-non-matching-revision", serializer);
        IntermediateEventRepresentation testRepresentation = new InitialEventRepresentation(testEventData, serializer);

        assertFalse(testSubject.canUpcast(testRepresentation));
    }

    @Test
    void isExpectedPayloadType() {
        assertTrue(testSubject.isExpectedPayloadType(EXPECTED_PAYLOAD_TYPE));
        assertFalse(testSubject.isExpectedPayloadType(UPCASTED_PAYLOAD_TYPE));
    }

    @Test
    void isExpectedRevision() {
        assertTrue(testSubject.isExpectedRevision(EXPECTED_REVISION));
        assertFalse(testSubject.isExpectedRevision(UPCASTED_REVISION));
    }

    @ParameterizedTest
    @MethodSource(SOURCE_METHOD_NAME)
    void doUpcast(Serializer serializer) {
        EventData<?> testEventData = new TestEventEntry(EXPECTED_PAYLOAD_TYPE, EXPECTED_REVISION, serializer);
        InitialEventRepresentation testRepresentation = new InitialEventRepresentation(testEventData, serializer);

        IntermediateEventRepresentation result = testSubject.doUpcast(testRepresentation);
        SerializedType resultType = result.getType();
        assertEquals(UPCASTED_PAYLOAD_TYPE, resultType.getName());
        assertEquals(UPCASTED_REVISION, resultType.getRevision());
    }

    @ParameterizedTest
    @MethodSource(SOURCE_METHOD_NAME)
    void shouldDeserializeToNewType(Serializer serializer) {
        // If we're dealing with an XStreamSerializer the FQCN in the XML tags defines the type.
        // Due to this, it's more reasonable to use type aliases on the XStream instance i.o. using this upcaster.
        if (serializer instanceof XStreamSerializer) {
            return;
        }

        final EventData<?> testEventData = new TestEventEntry(EXPECTED_PAYLOAD_TYPE, EXPECTED_REVISION, serializer);
        final InitialEventRepresentation testRepresentation = new InitialEventRepresentation(testEventData, serializer);

        final IntermediateEventRepresentation result = testSubject.doUpcast(testRepresentation);

        Object deserialize = serializer.deserialize(result.getData());

        assertEquals(RenamedTestEvent.class, deserialize.getClass());
    }

    @Test
    void upcastedType() {
        SerializedType expectedType = new SimpleSerializedType(UPCASTED_PAYLOAD_TYPE, UPCASTED_REVISION);
        assertEquals(expectedType, testSubject.upcastedType());
    }

    /**
     * Test {@link AbstractEventEntry} implementation which only allows adjusting the {@code payloadType} and {@code
     * payloadRevision}. All other {@code AbstractEventEntry} parameters are defaulted.
     */
    private static class TestEventEntry extends AbstractEventEntry<byte[]> {

        private static final TestEvent PAYLOAD = new TestEvent("payload");
        private static final TestEvent META_DATA = new TestEvent("metadata");

        public TestEventEntry(String payloadType, String payloadRevision, Serializer serializer) {
            super("eventIdentifier", "timestamp", payloadType, payloadRevision,
                  serializer.serialize(PAYLOAD, byte[].class).getData(),
                  serializer.serialize(META_DATA, byte[].class).getData());
        }
    }

    /**
     * A simple event used for testing.
     */
    @SuppressWarnings("unused")
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
     * Latest revision of {@code TestEvent} (renamed event type).
     */
    @SuppressWarnings("unused")
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