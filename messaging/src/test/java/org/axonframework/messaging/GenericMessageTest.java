/*
 * Copyright (c) 2010-2020. Axon Framework
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

import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.junit.jupiter.api.*;

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

    @BeforeEach
    void setUp() {
        UnitOfWork<?> unitOfWork = mock(UnitOfWork.class);
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
    void messageSerialization() {
        GenericMessage<String> message = new GenericMessage<>("payload", Collections.singletonMap("key", "value"));
        Serializer jacksonSerializer = JacksonSerializer.builder().build();

        SerializedObject<String> serializedPayload = message.serializePayload(jacksonSerializer, String.class);
        SerializedObject<String> serializedMetaData = message.serializeMetaData(jacksonSerializer, String.class);

        assertEquals("\"payload\"", serializedPayload.getData());
        assertEquals("{\"key\":\"value\",\"foo\":\"bar\"}", serializedMetaData.getData());
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
}
