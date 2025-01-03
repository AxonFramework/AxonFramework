/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.eventhandling.scheduling.quartz;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventTestUtils;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.SerializedType;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;
import org.axonframework.serialization.TestSerializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.quartz.JobDataMap;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.axonframework.messaging.Headers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DirectEventJobDataBinderTest {

    private static final String TEST_EVENT_PAYLOAD = "event-payload";

    private final EventMessage<String> testEventMessage;
    private final MetaData testMetaData;

    DirectEventJobDataBinderTest() {
        this.testMetaData = MetaData.with("some-key", "some-value");
        this.testEventMessage = EventTestUtils.<String>asEventMessage(TEST_EVENT_PAYLOAD)
                                              .withMetaData(testMetaData);
    }

    static Stream<Arguments> serializerImplementationAndAssertionSpecifics() {
        return Stream.of(
                Arguments.arguments(
                        spy(TestSerializer.XSTREAM.getSerializer()),
                        (Function<Class<?>, String>) clazz -> clazz.getSimpleName().toLowerCase(),
                        (Predicate<Object>) Objects::isNull
                ),
                Arguments.arguments(
                        spy(JacksonSerializer.builder().build()),
                        (Function<Class<?>, String>) Class::getName,
                        (Predicate<Object>) Objects::isNull
                )
        );
    }

    @MethodSource("serializerImplementationAndAssertionSpecifics")
    @ParameterizedTest
    void eventMessageToJobData(
            Serializer serializer,
            Function<Class<?>, String> expectedSerializedClassType,
            Predicate<Object> revisionMatcher
    ) {
        QuartzEventScheduler.DirectEventJobDataBinder testSubject = new QuartzEventScheduler.DirectEventJobDataBinder(serializer);

        JobDataMap result = testSubject.toJobData(testEventMessage);

        assertEquals(testEventMessage.getIdentifier(), result.get(MESSAGE_ID));
        assertEquals(testEventMessage.getTimestamp().toString(), result.get(MESSAGE_TIMESTAMP));
        String expectedPayloadType = expectedSerializedClassType.apply(testEventMessage.getPayloadType());
        assertEquals(expectedPayloadType, result.get(MESSAGE_TYPE));
        Object resultRevision = result.get(MESSAGE_REVISION);
        assertTrue(revisionMatcher.test(resultRevision));

        assertNotNull(result.get(SERIALIZED_MESSAGE_PAYLOAD));
        assertNotNull(result.get(MESSAGE_METADATA));

        verify(serializer).serialize(TEST_EVENT_PAYLOAD, byte[].class);
        verify(serializer).serialize(testMetaData, byte[].class);
    }

    @SuppressWarnings("unchecked")
    @MethodSource("serializerImplementationAndAssertionSpecifics")
    @ParameterizedTest
    void eventMessageFromJobData(
            Serializer serializer,
            Function<Class<?>, String> expectedSerializedClassType,
            Predicate<Object> revisionMatcher
    ) {
        QuartzEventScheduler.DirectEventJobDataBinder testSubject = new QuartzEventScheduler.DirectEventJobDataBinder(serializer);
        JobDataMap testJobDataMap = testSubject.toJobData(testEventMessage);

        Object result = testSubject.fromJobData(testJobDataMap);

        assertTrue(result instanceof EventMessage);

        EventMessage<String> resultEventMessage = (EventMessage<String>) result;

        assertEquals(testEventMessage.getIdentifier(), resultEventMessage.getIdentifier());
        assertEquals(testEventMessage.getTimestamp(), resultEventMessage.getTimestamp());
        assertEquals(testEventMessage.getPayload(), resultEventMessage.getPayload());
        assertEquals(testEventMessage.getPayloadType(), resultEventMessage.getPayloadType());
        assertEquals(testEventMessage.getMetaData(), resultEventMessage.getMetaData());

        verify(serializer, times(2)).deserialize(
                argThat(new MatchEventMessageSerializedObject(expectedSerializedClassType, revisionMatcher))
        );
    }

    private static class MatchEventMessageSerializedObject implements ArgumentMatcher<SimpleSerializedObject<?>> {
        private final Function<Class<?>, String> expectedSerializedClassType;
        private final Predicate<Object> revisionMatcher;

        MatchEventMessageSerializedObject(Function<Class<?>, String> expectedSerializedClassType,
                                          Predicate<Object> revisionMatcher) {
            this.expectedSerializedClassType = expectedSerializedClassType;
            this.revisionMatcher = revisionMatcher;
        }

        @Override
        public boolean matches(SimpleSerializedObject<?> serializedObject) {
            String expectedSerializedPayloadType = expectedSerializedClassType.apply(TEST_EVENT_PAYLOAD.getClass());

            SerializedType type = serializedObject.getType();
            String serializedTypeName = type.getName();
            boolean isSerializedMetaData = serializedTypeName.equals(MetaData.class.getName());

            return serializedObject.getData() != null &&
                    serializedObject.getContentType().equals(byte[].class) &&
                    (serializedTypeName.equals(expectedSerializedPayloadType) || isSerializedMetaData) &&
                    (isSerializedMetaData || revisionMatcher.test(type.getRevision()));
        }
    }

}
