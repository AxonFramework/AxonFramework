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

package org.axonframework.deadline.quartz;

import org.axonframework.commandhandling.model.AggregateScopeDescriptor;
import org.axonframework.deadline.DeadlineMessage;
import org.axonframework.deadline.GenericDeadlineMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.ScopeDescriptor;
import org.axonframework.serialization.JavaSerializer;
import org.axonframework.serialization.SerializedType;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.*;
import org.mockito.*;
import org.quartz.JobDataMap;

import java.util.function.Predicate;

import static org.axonframework.deadline.quartz.DeadlineJob.DeadlineJobDataBinder.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class DeadlineJobDataBinderTest {

    private static final String TEST_DEADLINE_NAME = "deadline-name";
    private static final String TEST_DEADLINE_PAYLOAD = "deadline-payload";

    private JavaSerializer javaSerializer;
    private XStreamSerializer xStreamSerializer;
    private JacksonSerializer jacksonSerializer;

    private DeadlineMessage<String> testDeadlineMessage;
    private MetaData testMetaData;
    private ScopeDescriptor testDeadlineScope;

    @Before
    public void setUp() {
        javaSerializer = spy(new JavaSerializer());
        xStreamSerializer = spy(new XStreamSerializer());
        jacksonSerializer = spy(new JacksonSerializer());

        testDeadlineMessage = GenericDeadlineMessage.asDeadlineMessage(TEST_DEADLINE_NAME, TEST_DEADLINE_PAYLOAD);
        testMetaData = MetaData.with("some-key", "some-value");
        testDeadlineMessage = testDeadlineMessage.withMetaData(testMetaData);
        testDeadlineScope = new AggregateScopeDescriptor("aggregate-type", "aggregate-identifier");
    }

    @Test
    public void testToJobDataUsingJavaSerializer() {
        JobDataMap result =
                DeadlineJob.DeadlineJobDataBinder.toJobData(javaSerializer, testDeadlineMessage, testDeadlineScope);

        assertJobDataMap(result, javaSerializer);
        assertEquals(testDeadlineMessage.getPayloadType().getName(), result.get(DEADLINE_PAYLOAD_CLASS_NAME));
        assertNotNull(result.get(DEADLINE_PAYLOAD_REVISION));
    }

    @Test
    public void testToJobDataUsingXStreamSerializer() {
        JobDataMap result =
                DeadlineJob.DeadlineJobDataBinder.toJobData(xStreamSerializer, testDeadlineMessage, testDeadlineScope);

        assertJobDataMap(result, xStreamSerializer);
        assertEquals(
                testDeadlineMessage.getPayloadType().getSimpleName().toLowerCase(),
                result.get(DEADLINE_PAYLOAD_CLASS_NAME)
        );
        assertNull(result.get(DEADLINE_PAYLOAD_REVISION));
    }

    @Test
    public void testToJobDataUsingJacksonSerializer() {
        JobDataMap result =
                DeadlineJob.DeadlineJobDataBinder.toJobData(jacksonSerializer, testDeadlineMessage, testDeadlineScope);

        assertJobDataMap(result, jacksonSerializer);
        assertEquals(testDeadlineMessage.getPayloadType().getName(), result.get(DEADLINE_PAYLOAD_CLASS_NAME));
        assertNull(result.get(DEADLINE_PAYLOAD_REVISION));
    }

    private void assertJobDataMap(JobDataMap result, Serializer serializer) {
        assertEquals(TEST_DEADLINE_NAME, result.get(DEADLINE_NAME));
        assertEquals(testDeadlineMessage.getIdentifier(), result.get(DEADLINE_IDENTIFIER));
        assertEquals(testDeadlineMessage.getTimestamp().toEpochMilli(), result.get(DEADLINE_TIMESTAMP_EPOCH_MILLIS));
        assertNotNull(result.get(SERIALIZED_DEADLINE_PAYLOAD));
        assertNotNull(result.get(SERIALIZED_DEADLINE_METADATA));
        assertNotNull(result.get(SERIALIZED_DEADLINE_SCOPE));
        assertEquals(testDeadlineScope.getClass().getName(), result.get(SERIALIZED_DEADLINE_SCOPE_CLASS_NAME));

        verify(serializer).serialize(TEST_DEADLINE_PAYLOAD, byte[].class);
        verify(serializer).serialize(testMetaData, byte[].class);
        verify(serializer).serialize(testDeadlineScope, byte[].class);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testRetrievingDeadlineMessageUsingJavaSerializer() {
        JobDataMap testJobDataMap =
                DeadlineJob.DeadlineJobDataBinder.toJobData(javaSerializer, testDeadlineMessage, testDeadlineScope);

        DeadlineMessage<String> result =
                DeadlineJob.DeadlineJobDataBinder.deadlineMessage(javaSerializer, testJobDataMap);

        assertDeserializedDeadlineMessage(
                testDeadlineMessage,
                result,
                javaSerializer,
                TEST_DEADLINE_PAYLOAD.getClass().getName(),
                type -> (type.getName().equals(MetaData.class.getName()) && type.getRevision() == null) ||
                        type.getRevision() != null
        );
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testRetrievingDeadlineMessageUsingXStreamSerializer() {
        JobDataMap testJobDataMap =
                DeadlineJob.DeadlineJobDataBinder.toJobData(xStreamSerializer, testDeadlineMessage, testDeadlineScope);

        DeadlineMessage<String> result =
                DeadlineJob.DeadlineJobDataBinder.deadlineMessage(xStreamSerializer, testJobDataMap);

        assertDeserializedDeadlineMessage(testDeadlineMessage,
                                          result,
                                          xStreamSerializer,
                                          TEST_DEADLINE_PAYLOAD.getClass().getSimpleName().toLowerCase(),
                                          type -> type.getRevision() == null);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testRetrievingDeadlineMessageUsingJacksonSerializer() {
        JobDataMap testJobDataMap =
                DeadlineJob.DeadlineJobDataBinder.toJobData(jacksonSerializer, testDeadlineMessage, testDeadlineScope);

        DeadlineMessage<String> result =
                DeadlineJob.DeadlineJobDataBinder.deadlineMessage(jacksonSerializer, testJobDataMap);

        assertDeserializedDeadlineMessage(testDeadlineMessage,
                                          result,
                                          jacksonSerializer,
                                          TEST_DEADLINE_PAYLOAD.getClass().getName(),
                                          type -> type.getRevision() == null);
    }

    private void assertDeserializedDeadlineMessage(DeadlineMessage<String> expected,
                                                   DeadlineMessage<String> result,
                                                   Serializer serializer,
                                                   String expectedSerializedPayloadType,
                                                   Predicate<SerializedType> revisionMatcher) {
        assertEquals(expected.getDeadlineName(), result.getDeadlineName());
        assertEquals(expected.getIdentifier(), result.getIdentifier());
        assertEquals(expected.getTimestamp(), result.getTimestamp());
        assertEquals(expected.getPayload(), result.getPayload());
        assertEquals(expected.getPayloadType(), result.getPayloadType());
        assertEquals(expected.getMetaData(), result.getMetaData());

        verify(serializer, times(2)).deserialize(argThat(
                (ArgumentMatcher<SimpleSerializedObject<?>>) serializedObject ->
                        assertDeadlineMessageSerializedObject(serializedObject,
                                                              expectedSerializedPayloadType,
                                                              revisionMatcher)
        ));
    }

    private boolean assertDeadlineMessageSerializedObject(SimpleSerializedObject<?> serializedObject,
                                                          String expectedSerializedPayloadType,
                                                          Predicate<SerializedType> revisionMatcher) {
        SerializedType type = serializedObject.getType();
        String serializedTypeName = type.getName();
        return serializedObject.getData() != null &&
                serializedObject.getContentType().equals(byte[].class) &&
                (serializedTypeName.equals(expectedSerializedPayloadType) ||
                        serializedTypeName.equals(MetaData.class.getName())) &&
                revisionMatcher.test(type);
    }

    @Test
    public void testRetrievingDeadlineScopeUsingJavaSerializer() {
        JobDataMap testJobDataMap =
                DeadlineJob.DeadlineJobDataBinder.toJobData(javaSerializer, testDeadlineMessage, testDeadlineScope);

        ScopeDescriptor result = DeadlineJob.DeadlineJobDataBinder.deadlineScope(javaSerializer, testJobDataMap);

        assertEquals(testDeadlineScope, result);
        verify(javaSerializer).deserialize(
                (SimpleSerializedObject<?>) argThat(this::assertDeadlineScopeSerializedObject)
        );
    }

    @Test
    public void testRetrievingDeadlineScopeUsingXStreamSerializer() {
        JobDataMap testJobDataMap =
                DeadlineJob.DeadlineJobDataBinder.toJobData(xStreamSerializer, testDeadlineMessage, testDeadlineScope);

        ScopeDescriptor result = DeadlineJob.DeadlineJobDataBinder.deadlineScope(xStreamSerializer, testJobDataMap);

        assertEquals(testDeadlineScope, result);
        verify(xStreamSerializer).deserialize(
                (SimpleSerializedObject<?>) argThat(this::assertDeadlineScopeSerializedObject)
        );
    }

    @Test
    public void testRetrievingDeadlineScopeUsingJacksonSerializer() {
        JobDataMap testJobDataMap =
                DeadlineJob.DeadlineJobDataBinder.toJobData(jacksonSerializer, testDeadlineMessage, testDeadlineScope);

        ScopeDescriptor result = DeadlineJob.DeadlineJobDataBinder.deadlineScope(jacksonSerializer, testJobDataMap);

        assertEquals(testDeadlineScope, result);
        verify(jacksonSerializer).deserialize(
                (SimpleSerializedObject<?>) argThat(this::assertDeadlineScopeSerializedObject)
        );
    }

    private boolean assertDeadlineScopeSerializedObject(SimpleSerializedObject<?> serializedObject) {
        SerializedType type = serializedObject.getType();
        return serializedObject.getData() != null &&
                serializedObject.getContentType().equals(byte[].class) &&
                type.getName().equals(testDeadlineScope.getClass().getName()) &&
                type.getRevision() == null;
    }
}
