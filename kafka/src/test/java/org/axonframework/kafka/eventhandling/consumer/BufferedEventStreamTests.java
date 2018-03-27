/*
 * Copyright (c) 2010-2018. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.kafka.eventhandling.consumer;

import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.axonframework.eventsourcing.GenericTrackedDomainEventMessage;
import org.axonframework.messaging.MetaData;
import org.junit.*;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * Tests for {@link BufferedEventStream}
 *
 * @author Nakul Mishra
 */
public class BufferedEventStreamTests {

    private static final TimeUnit DEFAULT_TIMEOUT_UNIT = TimeUnit.NANOSECONDS;

    @Test
    public void testPeekOnAnEmptyStream() {
        assertFalse(emptyStream().peek().isPresent());
    }

    @Test
    public void testPeekOnNonEmptyStream() throws InterruptedException {
        GenericTrackedDomainEventMessage<String> firstMessage = trackedDomainEvent("foo");
        BufferedEventStream testSubject = stream(Arrays.asList(firstMessage,
                                                                               trackedDomainEvent("bar")));
        assertTrue(testSubject.peek().isPresent());
        assertThat(testSubject.peek().get(), is(firstMessage));
    }

    @Test
    public void testPeekOnAProgressiveStream() throws InterruptedException {
        GenericTrackedDomainEventMessage<String> firstMessage = trackedDomainEvent("foo");
        GenericTrackedDomainEventMessage<String> secondMessage = trackedDomainEvent("bar");
        BufferedEventStream testSubject = stream(Arrays.asList(firstMessage, secondMessage));
        assertThat(testSubject.peek().get().getPayload(), is(firstMessage.getPayload()));
        testSubject.nextAvailable();
        assertThat(testSubject.peek().get().getPayload(), is(secondMessage.getPayload()));
        testSubject.nextAvailable();
        assertFalse(testSubject.peek().isPresent());
    }

    @Test
    public void testPeekOnAnInterruptedStream() throws InterruptedException {
        try {
            BufferedEventStream testSubject = stream(
                    singletonList(trackedDomainEvent("foo")));
            Thread.currentThread().interrupt();
            assertFalse(testSubject.peek().isPresent());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void testHasNextAvailableOnAnEmptyStream() {
        assertFalse(emptyStream().hasNextAvailable(1, DEFAULT_TIMEOUT_UNIT));
    }

    @Test
    public void testHasNextAvailableOnNonEmptyStream() throws InterruptedException {
        BufferedEventStream testSubject = stream(singletonList(trackedDomainEvent("foo")));
        assertTrue(testSubject.hasNextAvailable(1, DEFAULT_TIMEOUT_UNIT));
    }

    @Test
    public void testHasNextAvailableOnAProgressiveStream() throws InterruptedException {
        GenericTrackedDomainEventMessage<String> firstMessage = trackedDomainEvent("foo");
        GenericTrackedDomainEventMessage<String> secondMessage = trackedDomainEvent("bar");
        BufferedEventStream testSubject = stream(Arrays.asList(firstMessage, secondMessage));
        assertTrue(testSubject.hasNextAvailable(1, DEFAULT_TIMEOUT_UNIT));
        testSubject.nextAvailable();
        assertTrue(testSubject.hasNextAvailable(1, DEFAULT_TIMEOUT_UNIT));
        testSubject.nextAvailable();
        assertFalse(testSubject.hasNextAvailable(1, DEFAULT_TIMEOUT_UNIT));
    }

    @Test
    public void testHasNextOnAnInterruptedStream() throws InterruptedException {
        try {
            BufferedEventStream testSubject = stream(singletonList(trackedDomainEvent("foo")));
            Thread.currentThread().interrupt();
            assertFalse(testSubject.hasNextAvailable(1, DEFAULT_TIMEOUT_UNIT));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void testNextAvailableOnAProgressiveStream() throws InterruptedException {
        GenericTrackedDomainEventMessage<String> firstMessage = trackedDomainEvent("foo");
        GenericTrackedDomainEventMessage<String> secondMessage = trackedDomainEvent("bar");
        BufferedEventStream testSubject = stream(Arrays.asList(firstMessage, secondMessage));
        assertThat(testSubject.nextAvailable(), is(firstMessage));
        assertThat(testSubject.nextAvailable(), is(secondMessage));
    }

    @Test
    public void testNextAvailableOnAnInterruptedStream() throws InterruptedException {
        try {
            BufferedEventStream testSubject = stream(singletonList(trackedDomainEvent("foo")));
            Thread.currentThread().interrupt();
            assertNull(testSubject.nextAvailable());
        } finally {
            Thread.interrupted();
        }
    }

    private BufferedEventStream emptyStream() {
        return new BufferedEventStream(new MessageBuffer<>());
    }

    private BufferedEventStream stream(List<GenericTrackedDomainEventMessage<String>> messages)
            throws InterruptedException {
        MessageBuffer<MessageAndMetadata> buffer = new MessageBuffer<>(messages.size());

        int time = 1;
        int offset = 0;
        for (GenericTrackedDomainEventMessage<String> message : messages) {
            buffer.put(new MessageAndMetadata(message, 0, offset++, time++));
        }

        return new BufferedEventStream(buffer);
    }

    private GenericTrackedDomainEventMessage<String> trackedDomainEvent(String aggregateId) {
        return new GenericTrackedDomainEventMessage<>(null,
                                                      domainMessage(aggregateId));
    }

    private GenericDomainEventMessage<String> domainMessage(String aggregateId) {
        return new GenericDomainEventMessage<>("Stub", aggregateId, 1L, "Payload", MetaData.with("key", "value"));
    }
}