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

package org.axonframework.queryhandling;

import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.GenericResultMessage;
import org.axonframework.messaging.MetaData;
import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link GenericSubscriptionQueryUpdateMessage}.
 *
 * @author Milan Savic
 */
class GenericSubscriptionQueryUpdateMessageTest {

    @Test
    void messageCreation() {
        String payload = "payload";

        SubscriptionQueryUpdateMessage<Object> result = GenericSubscriptionQueryUpdateMessage.asUpdateMessage(payload);

        assertEquals(payload, result.getPayload());
    }

    @Test
    void messageCreationWithNullPayload() {
        String payload = null;

        GenericSubscriptionQueryUpdateMessage<String> result =
                new GenericSubscriptionQueryUpdateMessage<>(String.class, payload);

        assertNull(result.getPayload());
    }

    @Test
    void andMetaData() {
        Map<String, String> metaData = Collections.singletonMap("k1", "v2");
        SubscriptionQueryUpdateMessage<Object> original = new GenericSubscriptionQueryUpdateMessage<>(
                new GenericMessage<>("payload", metaData));


        Map<String, String> newMetaData = Collections.singletonMap("k2", "v3");
        SubscriptionQueryUpdateMessage<Object> result = original.andMetaData(newMetaData);

        assertEquals(original.getPayload(), result.getPayload());
        MetaData expectedMetaData = MetaData.from(metaData)
                                            .mergedWith(newMetaData);
        assertEquals(expectedMetaData, result.getMetaData());
    }

    @Test
    void withMetaData() {
        Map<String, String> metaData = Collections.singletonMap("k1", "v2");
        SubscriptionQueryUpdateMessage<Object> original = new GenericSubscriptionQueryUpdateMessage<>(
                new GenericMessage<>("payload", metaData));


        Map<String, String> newMetaData = Collections.singletonMap("k2", "v3");
        SubscriptionQueryUpdateMessage<Object> result = original.withMetaData(newMetaData);

        assertEquals(original.getPayload(), result.getPayload());
        assertEquals(newMetaData, result.getMetaData());
    }

    @Test
    void messageCreationBasedOnExistingMessage() {
        GenericSubscriptionQueryUpdateMessage<String> original = new GenericSubscriptionQueryUpdateMessage<>("payload");

        SubscriptionQueryUpdateMessage<Object> result = GenericSubscriptionQueryUpdateMessage.asUpdateMessage(original);

        assertEquals(result, original);
    }

    @Test
    void messageCreationBasedOnResultMessage() {
        Map<String, String> metaData = Collections.singletonMap("k1", "v1");
        CommandResultMessage<String> resultMessage = GenericCommandResultMessage.asCommandResultMessage(
                new GenericResultMessage<>("result", metaData));

        SubscriptionQueryUpdateMessage<Object> result = GenericSubscriptionQueryUpdateMessage.asUpdateMessage(
                resultMessage);

        assertEquals(result.getPayload(), resultMessage.getPayload());
        assertEquals(result.getMetaData(), resultMessage.getMetaData());
    }

    @Test
    void messageCreationBasedOnExceptionalResultMessage() {
        Map<String, String> metaData = Collections.singletonMap("k1", "v1");
        RuntimeException exception = new RuntimeException();
        CommandResultMessage<String> resultMessage = GenericCommandResultMessage.asCommandResultMessage(
                new GenericResultMessage<>(exception, metaData));

        SubscriptionQueryUpdateMessage<Object> result = GenericSubscriptionQueryUpdateMessage.asUpdateMessage(
                resultMessage);

        assertEquals(result.getMetaData(), resultMessage.getMetaData());
        assertTrue(result.isExceptional());
        assertEquals(exception, result.exceptionResult());
    }

    @Test
    void messageCreationBasedOnAnyMessage() {
        Map<String, String> metaData = Collections.singletonMap("k1", "v1");
        GenericMessage<String> message = new GenericMessage<>("payload", metaData);

        SubscriptionQueryUpdateMessage<Object> result = GenericSubscriptionQueryUpdateMessage.asUpdateMessage(message);

        assertEquals(result.getPayload(), message.getPayload());
        assertEquals(result.getMetaData(), message.getMetaData());
    }
}
