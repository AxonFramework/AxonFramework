/*
 * Copyright (c) 2010-2018. Axon Framework
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
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.JavaSerializer;
import org.axonframework.serialization.SerializedType;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.*;
import org.junit.runner.*;
import org.junit.runners.*;
import org.quartz.JobDataMap;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.axonframework.messaging.Headers.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@RunWith(Parameterized.class)
public class DirectEventJobDataBinderTest {

    private static final String TEST_EVENT_PAYLOAD = "event-payload";

    private QuartzEventScheduler.DirectEventJobDataBinder testSubject;

    private final Serializer serializer;
    private final Function<Class, String> expectedSerializedClassType;
    private final Predicate<Object> revisionMatcher;

    private final EventMessage<String> testEventMessage;
    private final MetaData testMetaData;

    @SuppressWarnings("unused") // Test name used to give sensible name to parameterized test
    public DirectEventJobDataBinderTest(String testName,
                                        Serializer serializer,
                                        Function<Class, String> expectedSerializedClassType,
                                        Predicate<Object> revisionMatcher) {
        this.serializer = spy(serializer);
        this.expectedSerializedClassType = expectedSerializedClassType;
        this.revisionMatcher = revisionMatcher;

        EventMessage<String> testEventMessage = GenericEventMessage.asEventMessage(TEST_EVENT_PAYLOAD);
        testMetaData = MetaData.with("some-key", "some-value");
        this.testEventMessage = testEventMessage.withMetaData(testMetaData);

        testSubject = new QuartzEventScheduler.DirectEventJobDataBinder(this.serializer);
    }

    @Parameterized.Parameters(name = "Using {0}")
    public static Collection serializerImplementationAndAssertionSpecifics() {
        return Arrays.asList(new Object[][]{
                {
                        "JavaSerializer",
                        JavaSerializer.builder().build(),
                        (Function<Class, String>) Class::getName,
                        (Predicate<Object>) Objects::nonNull},
                {
                        "XStreamSerializer",
                        XStreamSerializer.builder().build(),
                        (Function<Class, String>) clazz -> clazz.getSimpleName().toLowerCase(),
                        (Predicate<Object>) Objects::isNull
                },
                {
                        "JacksonSerializer",
                        JacksonSerializer.builder().build(),
                        (Function<Class, String>) Class::getName,
                        (Predicate<Object>) Objects::isNull
                }
        });
    }

    @Test
    public void testEventMessageToJobData() {
        JobDataMap result = testSubject.toJobData(testEventMessage);

        assertEquals(testEventMessage.getIdentifier(), result.get(MESSAGE_ID));
        assertEquals(testEventMessage.getTimestamp().toEpochMilli(), result.get(MESSAGE_TIMESTAMP));
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
    @Test
    public void testEventMessageFromJobData() {
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
                (SimpleSerializedObject<?>) argThat(this::assertEventMessageSerializedObject)
        );
    }

    private boolean assertEventMessageSerializedObject(SimpleSerializedObject<?> serializedObject) {
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
