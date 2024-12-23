/*
 * Copyright (c) 2010-2024. Axon Framework
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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.messaging.*;
import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link GenericSubscriptionQueryUpdateMessage}.
 *
 * @author Milan Savic
 */
// todo: should we remove support for that?
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

        SubscriptionQueryUpdateMessage<String> result = new GenericSubscriptionQueryUpdateMessage<>(
                new QualifiedName("test", "query", "0.0.1"), payload, String.class
        );

        assertNull(result.getPayload());
    }

    @Test
    void andMetaData() {
        Map<String, String> metaData = Collections.singletonMap("k1", "v2");
        SubscriptionQueryUpdateMessage<Object> original = new GenericSubscriptionQueryUpdateMessage<>(
                new GenericMessage<>(new QualifiedName("test", "query", "0.0.1"), "payload", metaData)
        );

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
                new GenericMessage<>(new QualifiedName("test", "query", "0.0.1"), "payload", metaData)
        );

        Map<String, String> newMetaData = Collections.singletonMap("k2", "v3");
        SubscriptionQueryUpdateMessage<Object> result = original.withMetaData(newMetaData);

        assertEquals(original.getPayload(), result.getPayload());
        assertEquals(newMetaData, result.getMetaData());
    }

    @Test
    void messageCreationBasedOnExistingMessage() {
        SubscriptionQueryUpdateMessage<String> original =
                new GenericSubscriptionQueryUpdateMessage<>(new QualifiedName("test", "query", "0.0.1"), "payload");

        SubscriptionQueryUpdateMessage<Object> result = GenericSubscriptionQueryUpdateMessage.asUpdateMessage(original);

        assertEquals(result, original);
    }

    @Test
    void messageCreationBasedOnResultMessage() {
        Map<String, String> metaData = Collections.singletonMap("k1", "v1");
        CommandResultMessage<String> resultMessage = asCommandResultMessage(
                new GenericResultMessage<>(new QualifiedName("test", "command", "0.0.1"), "result", metaData)
        );

        SubscriptionQueryUpdateMessage<Object> result =
                GenericSubscriptionQueryUpdateMessage.asUpdateMessage(resultMessage);

        assertEquals(result.getPayload(), resultMessage.getPayload());
        assertEquals(result.getMetaData(), resultMessage.getMetaData());
    }

    @Test
    void messageCreationBasedOnExceptionalResultMessage() {
        Map<String, String> metaData = Collections.singletonMap("k1", "v1");
        RuntimeException exception = new RuntimeException();
        CommandResultMessage<String> resultMessage = asCommandResultMessage(
                new GenericResultMessage<>(new QualifiedName("test", "query", "0.0.1"), exception, metaData)
        );

        SubscriptionQueryUpdateMessage<Object> result =
                GenericSubscriptionQueryUpdateMessage.asUpdateMessage(resultMessage);

        assertEquals(result.getMetaData(), resultMessage.getMetaData());
        assertTrue(result.isExceptional());
        assertEquals(exception, result.exceptionResult());
    }

    @Test
    void messageCreationBasedOnAnyMessage() {
        Map<String, String> metaData = Collections.singletonMap("k1", "v1");
        Message<String> message =
                new GenericMessage<>(new QualifiedName("test", "query", "0.0.1"), "payload", metaData);

        SubscriptionQueryUpdateMessage<Object> result = GenericSubscriptionQueryUpdateMessage.asUpdateMessage(message);

        assertEquals(result.getPayload(), message.getPayload());
        assertEquals(result.getMetaData(), message.getMetaData());
    }

    @SuppressWarnings("unchecked")
    private static <R> CommandResultMessage<R> asCommandResultMessage(@Nullable Object commandResult) {
        if (commandResult instanceof CommandResultMessage) {
            return (CommandResultMessage<R>) commandResult;
        } else if (commandResult instanceof Message) {
            Message<R> commandResultMessage = (Message<R>) commandResult;
            return new GenericCommandResultMessage<>(commandResultMessage);
        }
        return new GenericCommandResultMessage<>(QualifiedNameUtils.fromClassName(commandResult.getClass()), (R) commandResult);
    }
}
