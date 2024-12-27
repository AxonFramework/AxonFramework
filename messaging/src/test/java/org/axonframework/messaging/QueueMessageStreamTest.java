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

package org.axonframework.messaging;

import org.axonframework.common.Context;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class QueueMessageStreamTest extends MessageStreamTest<EventMessage<String>> {

    @Override
    MessageStream<EventMessage<String>> completedTestSubject(List<EventMessage<String>> messages) {
        QueueMessageStream<EventMessage<String>> testSubject = new QueueMessageStream<>();
        messages.forEach(m -> testSubject.offer(m, Context.empty()));
        testSubject.complete();
        return testSubject;
    }

    @Override
    protected QueueMessageStream<EventMessage<String>> uncompletedTestSubject(List<EventMessage<String>> messages) {
        QueueMessageStream<EventMessage<String>> testSubject = new QueueMessageStream<>();
        messages.forEach(m -> testSubject.offer(m, Context.empty()));
        return testSubject;
    }

    @Override
    protected void publishAdditionalMessage(MessageStream<EventMessage<String>> testSubject, EventMessage<String> randomMessage) {
        ((QueueMessageStream<EventMessage<String>>)testSubject).offer(randomMessage, Context.empty());
    }

    @Override
    MessageStream<EventMessage<String>> failingTestSubject(List<EventMessage<String>> messages, Exception failure) {
        QueueMessageStream<EventMessage<String>> testSubject = new QueueMessageStream<>();
        messages.forEach(m -> testSubject.offer(m, Context.empty()));
        testSubject.completeExceptionally(failure);
        return testSubject;
    }

    @Override
    EventMessage<String> createRandomMessage() {
        return new GenericEventMessage<>(new QualifiedName("test", "message", "0.0.1"),
                                         "test-" + ThreadLocalRandom.current().nextInt(10000));
    }

    @Test
    void shouldInvokeConsumeCallbackWhenMessageIsConsumed() {
        QueueMessageStream<EventMessage<String>> testSubject = uncompletedTestSubject(List.of(createRandomMessage()));

        AtomicBoolean invoked = new AtomicBoolean(false);
        testSubject.onConsumeCallback(() -> invoked.set(true));

        assertFalse(invoked.get());
        assertTrue(testSubject.next().isPresent());
        assertTrue(invoked.get());
    }

    @Test
    void shouldRefuseNewItemWhenCapacityHasBeenReached() {
        QueueMessageStream<EventMessage<String>> testSubject = new QueueMessageStream<>(new ArrayBlockingQueue<>(2));

        EventMessage<String> message1 = createRandomMessage();
        EventMessage<String> message2 = createRandomMessage();

        assertTrue(testSubject.offer(message1, Context.empty()));
        assertTrue(testSubject.offer(message2, Context.empty()));
        assertFalse(testSubject.offer(createRandomMessage(), Context.empty()));

        assertSame(message1, testSubject.next().map(MessageStream.Entry::message).orElse(null));

        assertTrue(testSubject.offer(createRandomMessage(), Context.empty()));
        assertFalse(testSubject.offer(createRandomMessage(), Context.empty()));

        assertSame(message2, testSubject.next().map(MessageStream.Entry::message).orElse(null));
    }
}