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
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link KafkaMessageStream}
 *
 * @author Nakul Mishra
 */
public class KafkaMessageStreamTests {

    private static final TimeUnit DEFAULT_TIMEOUT_UNIT = NANOSECONDS;

    @Test
    public void testPeek_OnAnEmptyStream_ShouldContainNoElement() {
        assertThat(emptyStream().peek().isPresent()).isFalse();
    }

    @Test
    public void testPeek_OnNonEmptyStream_ShouldContainSomeElement() throws InterruptedException {
        GenericTrackedDomainEventMessage<String> firstMessage = trackedDomainEvent("foo");
        KafkaMessageStream testSubject = stream(Arrays.asList(firstMessage, trackedDomainEvent("bar")));
        assertThat(testSubject.peek().isPresent()).isTrue();
        assertThat(testSubject.peek().get()).isEqualTo(firstMessage);
    }

    @Test
    public void testPeek_OnAProgressiveStream_ShouldContainElementsInCorrectOrder() throws InterruptedException {
        GenericTrackedDomainEventMessage<String> firstMessage = trackedDomainEvent("foo");
        GenericTrackedDomainEventMessage<String> secondMessage = trackedDomainEvent("bar");
        KafkaMessageStream testSubject = stream(Arrays.asList(firstMessage, secondMessage));
        assertThat(testSubject.peek().get().getPayload()).isEqualTo(firstMessage.getPayload());
        testSubject.nextAvailable();
        assertThat(testSubject.peek().get()).isEqualTo(secondMessage);
        testSubject.nextAvailable();
        assertThat(testSubject.peek().isPresent()).isFalse();
    }

    @Test
    public void testPeek_OnAnInterruptedStream_ShouldThrowException() throws InterruptedException {
        try {
            KafkaMessageStream testSubject = stream(
                    singletonList(trackedDomainEvent("foo")));
            Thread.currentThread().interrupt();
            assertThat(testSubject.peek().isPresent()).isFalse();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void testHasNextAvailable_OnAnEmptyStream_ShouldContainNoElement() {
        assertThat(emptyStream().hasNextAvailable(1, DEFAULT_TIMEOUT_UNIT)).isFalse();
    }

    @Test
    public void testHasNextAvailable_OnNonEmptyStream_ShouldContainSomeElement() throws InterruptedException {
        KafkaMessageStream testSubject = stream(singletonList(trackedDomainEvent("foo")));
        assertThat(testSubject.hasNextAvailable(1, DEFAULT_TIMEOUT_UNIT)).isTrue();
    }

    @Test
    public void testHasNextAvailable_OnAProgressiveStream_ShouldContainElementsInCorrectOrder()
            throws InterruptedException {
        GenericTrackedDomainEventMessage<String> firstMessage = trackedDomainEvent("foo");
        GenericTrackedDomainEventMessage<String> secondMessage = trackedDomainEvent("bar");
        KafkaMessageStream testSubject = stream(Arrays.asList(firstMessage, secondMessage));
        assertThat(testSubject.hasNextAvailable(1, DEFAULT_TIMEOUT_UNIT)).isTrue();
        testSubject.nextAvailable();
        assertThat(testSubject.hasNextAvailable(1, DEFAULT_TIMEOUT_UNIT)).isTrue();
        testSubject.nextAvailable();
        assertThat(testSubject.hasNextAvailable(1, DEFAULT_TIMEOUT_UNIT)).isFalse();
    }

    @Test
    public void testHasNext_OnAnInterruptedStream_ShouldThrowAnException() throws InterruptedException {
        try {
            KafkaMessageStream testSubject = stream(singletonList(trackedDomainEvent("foo")));
            Thread.currentThread().interrupt();
            assertThat(testSubject.hasNextAvailable(1, DEFAULT_TIMEOUT_UNIT)).isFalse();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void testNextAvailable_OnAProgressiveStream_ShouldContainElementInCorrectOrder()
            throws InterruptedException {
        GenericTrackedDomainEventMessage<String> firstMessage = trackedDomainEvent("foo");
        GenericTrackedDomainEventMessage<String> secondMessage = trackedDomainEvent("bar");
        KafkaMessageStream testSubject = stream(Arrays.asList(firstMessage, secondMessage));
        assertThat(testSubject.nextAvailable()).isEqualTo(firstMessage);
        assertThat(testSubject.nextAvailable()).isEqualTo(secondMessage);
    }

    @Test
    public void testNextAvailable_OnAnInterruptedStream_ShouldThrowAnException() throws InterruptedException {
        try {
            KafkaMessageStream testSubject = stream(singletonList(trackedDomainEvent("foo")));
            Thread.currentThread().interrupt();
            assertThat(testSubject.nextAvailable()).isNull();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void testClosing_MessageStream_ShouldInvokeTheCloseHandler() {
        Runnable closeHandler = mock(Runnable.class);
        KafkaMessageStream mock = new KafkaMessageStream(new SortedKafkaMessageBuffer<>(), closeHandler);
        verify(closeHandler, never()).run();
        mock.close();
        verify(closeHandler).run();
    }

    private static KafkaMessageStream emptyStream() {
        Runnable closeHandler = mock(Runnable.class);
        return new KafkaMessageStream(new SortedKafkaMessageBuffer<>(), closeHandler);
    }

    private static GenericTrackedDomainEventMessage<String> trackedDomainEvent(String aggregateId) {
        return new GenericTrackedDomainEventMessage<>(null,
                                                      domainMessage(aggregateId));
    }

    private static GenericDomainEventMessage<String> domainMessage(String aggregateId) {
        return new GenericDomainEventMessage<>("Stub", aggregateId, 1L, "Payload", MetaData.with("key", "value"));
    }

    private static KafkaMessageStream stream(List<GenericTrackedDomainEventMessage<String>> messages)
            throws InterruptedException {
        SortedKafkaMessageBuffer<KafkaEventMessage> buffer = new SortedKafkaMessageBuffer<>(messages.size());

        for (int i = 0; i < messages.size(); i++) {
            buffer.put(new KafkaEventMessage(messages.get(i), 0, i, 1));
        }

        Runnable closeHandler = mock(Runnable.class);
        return new KafkaMessageStream(buffer, closeHandler);
    }
}