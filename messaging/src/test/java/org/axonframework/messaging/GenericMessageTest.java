/*
 * Copyright (c) 2010-2022. Axon Framework
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

import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.correlation.ThrowingCorrelationDataProvider;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.serialization.CannotConvertBetweenTypesException;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.json.JacksonSerializer;
import org.junit.jupiter.api.*;

import com.fasterxml.jackson.databind.ObjectMapper;

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

    private final Map<String, ?> correlationData = MetaData.from(Collections.singletonMap("foo", "bar"));
    private UnitOfWork<?> unitOfWork;

    @BeforeEach
    void setUp() {
        unitOfWork = mock(UnitOfWork.class);
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
    void correlationDataAddedToNewMessage() {
        assertEquals(correlationData, new HashMap<>(new GenericMessage<>(new Object()).getMetaData()));

        MetaData newMetaData = MetaData.from(Collections.singletonMap("whatever", new Object()));
        assertEquals(newMetaData.mergedWith(correlationData),
                     new GenericMessage<>(new Object(), newMetaData).getMetaData());
    }

    @Test
    void messageSerialization() throws IOException{
        Map<String, String> metaDataMap = Collections.singletonMap("key", "value");

        GenericMessage<String> message = new GenericMessage<>("payload", metaDataMap);
     
        JacksonSerializer jacksonSerializer = JacksonSerializer.builder().build();


        SerializedObject<String> serializedPayload = message.serializePayload(jacksonSerializer, String.class);
        SerializedObject<String> serializedMetaData = message.serializeMetaData(jacksonSerializer, String.class);

        assertEquals("\"payload\"", serializedPayload.getData());

    
        ObjectMapper objectMapper = jacksonSerializer.getObjectMapper();
        Map<String, String> actualMetaData = objectMapper.readValue(serializedMetaData.getData(), Map.class);

         assertTrue(actualMetaData.entrySet().containsAll(metaDataMap.entrySet()));
    }

    @Test
    void asMessageReturnsProvidedMessageAsIs() {
        GenericMessage<String> testMessage = new GenericMessage<>("payload");

        Message<?> result = GenericMessage.asMessage(testMessage);

        assertEquals(testMessage, result);
    }

    @Test
    void asMessageWrapsProvidedObjectsInMessage() {
        String testPayload = "payload";

        Message<?> result = GenericMessage.asMessage(testPayload);

        assertNotEquals(testPayload, result);
        assertEquals(testPayload, result.getPayload());
    }

    @Test
    void whenCorrelationDataProviderThrowsException_thenCatchException(){
        unitOfWork = new DefaultUnitOfWork<>(new GenericEventMessage<>("Input 1"));
        CurrentUnitOfWork.set(unitOfWork);
        unitOfWork.registerCorrelationDataProvider(new ThrowingCorrelationDataProvider());
        CannotConvertBetweenTypesException exception = new CannotConvertBetweenTypesException("foo");

        Message<?> result = GenericMessage.asMessage(exception);

        assertNotNull(result);
    }
}
