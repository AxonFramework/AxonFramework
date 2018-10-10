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

package org.axonframework.messaging;

import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.junit.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Rene de Waele
 */
public class GenericMessageTest {

    private Map<String, ?> correlationData = MetaData.from(Collections.singletonMap("foo", "bar"));

    @Before
    public void setUp() {
        UnitOfWork<?> unitOfWork = mock(UnitOfWork.class);
        when(unitOfWork.getCorrelationData()).thenAnswer(invocation -> correlationData);
        CurrentUnitOfWork.set(unitOfWork);
    }

    @After
    public void tearDown() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.clear(CurrentUnitOfWork.get());
        }
    }

    @Test
    public void testCorrelationDataAddedToNewMessage() {
        assertEquals(correlationData, new HashMap<>(new GenericMessage<>(new Object()).getMetaData()));

        MetaData newMetaData = MetaData.from(Collections.singletonMap("whatever", new Object()));
        assertEquals(newMetaData.mergedWith(correlationData),
                     new GenericMessage<>(new Object(), newMetaData).getMetaData());
    }

    @Test
    public void testMessageSerialization() {
        GenericMessage<String> message = new GenericMessage<>("payload", Collections.singletonMap("key", "value"));
        Serializer jacksonSerializer = JacksonSerializer.builder().build();

        SerializedObject<String> serializedPayload = message.serializePayload(jacksonSerializer, String.class);
        SerializedObject<String> serializedMetaData = message.serializeMetaData(jacksonSerializer, String.class);

        Assert.assertEquals("\"payload\"", serializedPayload.getData());
        Assert.assertEquals("{\"key\":\"value\",\"foo\":\"bar\"}", serializedMetaData.getData());
    }
}
