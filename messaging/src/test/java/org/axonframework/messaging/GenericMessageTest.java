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

package org.axonframework.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.correlation.ThrowingCorrelationDataProvider;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.LegacyDefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.LegacyUnitOfWork;
import org.axonframework.serialization.ConversionException;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.json.JacksonSerializer;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test correct operations of the {@link GenericMessage} class.
 *
 * @author Rene de Waele
 */
class GenericMessageTest {

    private final Map<String, String> correlationData = MetaData.from(Collections.singletonMap("foo", "bar"));

    private LegacyUnitOfWork<?> unitOfWork;

    @BeforeEach
    void setUp() {
        unitOfWork = mock(LegacyUnitOfWork.class);
        when(unitOfWork.getCorrelationData()).thenAnswer(invocation -> correlationData);
        CurrentUnitOfWork.set(unitOfWork);
    }

    @AfterEach
    void tearDown() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.clear(CurrentUnitOfWork.get());
        }
    }

    @Test
    void containsDataAsExpected() {
        String testIdentifier = "testIdentifier";
        MessageType testType = new MessageType("message");
        String testPayload = "payload";
        MetaData testMetaData = MetaData.emptyInstance();

        Message<String> testSubject = new GenericMessage<>(testIdentifier, testType, testPayload, testMetaData);

        assertEquals(testIdentifier, testSubject.getIdentifier());
        assertEquals(testType, testSubject.type());
        assertEquals(testPayload, testSubject.getPayload());
        assertEquals(testMetaData, testSubject.getMetaData());
    }

    @Test
    void correlationDataAddedToNewMessage() {
        Message<Object> testMessage = new GenericMessage<>(new MessageType("message"), new Object());
        assertEquals(correlationData, new HashMap<>(testMessage.getMetaData()));

        MetaData newMetaData = MetaData.from(Collections.singletonMap("what", "ever"));
        Message<Object> testMessageWithMetaData =
                new GenericMessage<>(new MessageType("message"), new Object(), newMetaData);
        assertEquals(newMetaData.mergedWith(correlationData), testMessageWithMetaData.getMetaData());
    }

    @Test
    void messageSerialization() throws IOException {
        Map<String, String> metaDataMap = Collections.singletonMap("key", "value");

        Message<String> message =
                new GenericMessage<>(new MessageType("message"), "payload", metaDataMap);

        JacksonSerializer jacksonSerializer = JacksonSerializer.builder().build();


        SerializedObject<String> serializedPayload = message.serializePayload(jacksonSerializer, String.class);
        SerializedObject<String> serializedMetaData = message.serializeMetaData(jacksonSerializer, String.class);

        assertEquals("\"payload\"", serializedPayload.getData());


        ObjectMapper objectMapper = jacksonSerializer.getObjectMapper();
        Map<String, String> actualMetaData = objectMapper.readValue(serializedMetaData.getData(), Map.class);

        assertTrue(actualMetaData.entrySet().containsAll(metaDataMap.entrySet()));
    }

    @Test
    void whenCorrelationDataProviderThrowsException_thenCatchException() {
        unitOfWork = new LegacyDefaultUnitOfWork<>(
                new GenericEventMessage<>(new MessageType("event"), "Input 1")
        );
        CurrentUnitOfWork.set(unitOfWork);
        unitOfWork.registerCorrelationDataProvider(new ThrowingCorrelationDataProvider());
        ConversionException exception = new ConversionException("foo");

        Message<?> result = new GenericMessage<>(new MessageType("exception"), exception);

        assertNotNull(result);
    }
}
