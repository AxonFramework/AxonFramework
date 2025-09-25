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

package org.axonframework.queryhandling;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageType;
import org.junit.jupiter.api.*;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link GenericSubscriptionQueryResponse} and partially the
 * {@link GenericSubscriptionQueryResponseMessages}.
 *
 * @author Steven van Beelen
 */
class GenericSubscriptionQueryResponseTest {

    private static final MessageType INITIAL_TYPE = new MessageType("query");
    private static final MessageType UPDATE_TYPE = new MessageType("update");
    private static final String INITIAL_PAYLOAD = "some-initial-result";
    private static final String UPDATE_PAYLOAD = "some-update";

    @Test
    void handleInvokesErrorConsumerOnExceptionInTheInitialResult() {
        AtomicBoolean canceled = new AtomicBoolean(false);
        AtomicBoolean initialResultConsumed = new AtomicBoolean(false);
        AtomicBoolean updateConsumed = new AtomicBoolean(false);
        AtomicBoolean errorConsumed = new AtomicBoolean(false);

        SubscriptionQueryResponseMessages testResponseMessage = new GenericSubscriptionQueryResponseMessages(
                Flux.error(new RuntimeException("oops")),
                Flux.empty(),
                () -> canceled.set(true)
        );
        SubscriptionQueryResponse<Object, Object> testSubject =
                new GenericSubscriptionQueryResponse<>(testResponseMessage, Message::payload, Message::payload);

        testSubject.handle(initialResult -> initialResultConsumed.set(true),
                           update -> updateConsumed.set(true),
                           error -> errorConsumed.set(true));

        assertTrue(canceled.get());
        assertFalse(initialResultConsumed.get());
        assertFalse(updateConsumed.get());
        assertTrue(errorConsumed.get());
    }

    @Test
    void handleInvokesErrorConsumerOnExceptionsInTheUpdates() {
        AtomicBoolean canceled = new AtomicBoolean(false);
        AtomicBoolean initialResultConsumed = new AtomicBoolean(false);
        AtomicBoolean updateConsumed = new AtomicBoolean(false);
        AtomicBoolean errorConsumed = new AtomicBoolean(false);

        SubscriptionQueryResponseMessages testResponseMessage = new GenericSubscriptionQueryResponseMessages(
                Flux.just(new GenericQueryResponseMessage(INITIAL_TYPE, INITIAL_PAYLOAD)),
                Flux.error(new RuntimeException("oops")),
                () -> canceled.set(true)
        );
        SubscriptionQueryResponse<Object, Object> testSubject =
                new GenericSubscriptionQueryResponse<>(testResponseMessage, Message::payload, Message::payload);

        testSubject.handle(initialResult -> initialResultConsumed.set(true),
                           update -> updateConsumed.set(true),
                           error -> errorConsumed.set(true));

        assertTrue(canceled.get());
        assertTrue(initialResultConsumed.get());
        assertFalse(updateConsumed.get());
        assertTrue(errorConsumed.get());
    }

    @Test
    void handleInvokesErrorConsumerOnExceptionThrownByTheInitialResultConsumer() {
        AtomicBoolean canceled = new AtomicBoolean(false);
        AtomicBoolean updateConsumed = new AtomicBoolean(false);
        AtomicBoolean errorConsumed = new AtomicBoolean(false);

        SubscriptionQueryResponseMessages testResponseMessage = new GenericSubscriptionQueryResponseMessages(
                Flux.just(new GenericQueryResponseMessage(INITIAL_TYPE, INITIAL_PAYLOAD)),
                Flux.just(new GenericSubscriptionQueryUpdateMessage(UPDATE_TYPE, UPDATE_PAYLOAD)),
                () -> canceled.set(true)
        );
        SubscriptionQueryResponse<Object, Object> testSubject =
                new GenericSubscriptionQueryResponse<>(testResponseMessage, Message::payload, Message::payload);

        testSubject.handle(initialResult -> {
                               throw new RuntimeException("oops");
                           },
                           update -> updateConsumed.set(true),
                           error -> errorConsumed.set(true));

        assertTrue(canceled.get());
        assertFalse(updateConsumed.get());
        assertTrue(errorConsumed.get());
    }

    @Test
    void handleInvokesErrorConsumerOnExceptionThrownByTheUpdateConsumer() {
        AtomicBoolean canceled = new AtomicBoolean(false);
        AtomicBoolean initialResultConsumed = new AtomicBoolean(false);
        AtomicBoolean errorConsumed = new AtomicBoolean(false);

        SubscriptionQueryResponseMessages testResponseMessage = new GenericSubscriptionQueryResponseMessages(
                Flux.just(new GenericQueryResponseMessage(INITIAL_TYPE, INITIAL_PAYLOAD)),
                Flux.just(new GenericSubscriptionQueryUpdateMessage(UPDATE_TYPE, UPDATE_PAYLOAD)),
                () -> canceled.set(true)
        );
        SubscriptionQueryResponse<Object, Object> testSubject =
                new GenericSubscriptionQueryResponse<>(testResponseMessage, Message::payload, Message::payload);

        testSubject.handle(initialResult -> initialResultConsumed.set(true),
                           update -> {
                               throw new RuntimeException("oops");
                           },
                           error -> errorConsumed.set(true));

        assertTrue(canceled.get());
        assertTrue(initialResultConsumed.get());
        assertTrue(errorConsumed.get());
    }
}