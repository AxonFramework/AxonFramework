package org.axonframework.kafka.eventhandling.consumer;

import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.axonframework.eventsourcing.GenericTrackedDomainEventMessage;
import org.axonframework.messaging.MetaData;
import org.junit.*;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link KafkaMessageStream}
 * @author Nakul Mishra
 */
public class KafkaMessageStreamTests {

    private static final TimeUnit DEFAULT_TIMEOUT_UNIT = TimeUnit.NANOSECONDS;

    @Test
    public void testPeekOnAnEmptyStream() {
        assertFalse(emptyStream().peek().isPresent());
    }

    @Test
    public void testPeekOnNonEmptyStream() {
        GenericTrackedDomainEventMessage<String> firstMessage = trackedDomainEvent("foo");
        KafkaMessageStream<String, byte[]> testSubject = stream(Arrays.asList(firstMessage, trackedDomainEvent("bar")));
        assertTrue(testSubject.peek().isPresent());
        assertThat(testSubject.peek().get(), is(firstMessage));
    }

    @Test
    public void testPeekOnAProgressiveStream() throws InterruptedException {
        GenericTrackedDomainEventMessage<String> firstMessage = trackedDomainEvent("foo");
        GenericTrackedDomainEventMessage<String> secondMessage = trackedDomainEvent("bar");
        KafkaMessageStream<String, byte[]> testSubject = stream(Arrays.asList(firstMessage, secondMessage));
        assertThat(testSubject.peek().get(), is(firstMessage));
        testSubject.nextAvailable();
        assertThat(testSubject.peek().get(), is(secondMessage));
        assertThat(testSubject.peek().get(), is(secondMessage));
        testSubject.nextAvailable();
        assertFalse(testSubject.peek().isPresent());
    }

    @Test
    public void testPeekOnAnInterruptedStream() {
        try {
            KafkaMessageStream<String, byte[]> testSubject = stream(
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
    public void testHasNextAvailableOnNonEmptyStream() {
        KafkaMessageStream<String, byte[]> testSubject = stream(singletonList(trackedDomainEvent("foo")));
        assertTrue(testSubject.hasNextAvailable(1, DEFAULT_TIMEOUT_UNIT));
    }

    @Test
    public void testHasNextAvailableOnAProgressiveStream() throws InterruptedException {
        GenericTrackedDomainEventMessage<String> firstMessage = trackedDomainEvent("foo");
        GenericTrackedDomainEventMessage<String> secondMessage = trackedDomainEvent("bar");
        KafkaMessageStream<String, byte[]> testSubject = stream(Arrays.asList(firstMessage, secondMessage));
        assertTrue(testSubject.hasNextAvailable(1, DEFAULT_TIMEOUT_UNIT));
        testSubject.nextAvailable();
        assertTrue(testSubject.hasNextAvailable(1, DEFAULT_TIMEOUT_UNIT));
        testSubject.nextAvailable();
        assertFalse(testSubject.hasNextAvailable(1, DEFAULT_TIMEOUT_UNIT));
    }

    @Test
    public void testHasNextOnAnInterruptedStream() {
        try {
            KafkaMessageStream<String, byte[]> testSubject = stream(singletonList(trackedDomainEvent("foo")));
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
        KafkaMessageStream<String, byte[]> testSubject = stream(Arrays.asList(firstMessage, secondMessage));
        assertThat(testSubject.nextAvailable(), is(firstMessage));
        assertThat(testSubject.nextAvailable(), is(secondMessage));
    }

    @Test
    public void testNextAvailableOnAnInterruptedStream() {
        try {
            KafkaMessageStream<String, byte[]> testSubject = stream(singletonList(trackedDomainEvent("foo")));
            Thread.currentThread().interrupt();
            assertNull(testSubject.nextAvailable());
        } finally {
            Thread.interrupted();
        }
    }


    @Test
    public void close() {
        Fetcher<String, byte[]> channel = messageChannel();
        KafkaMessageStream<String, byte[]> mock = new KafkaMessageStream<>(new PriorityBlockingQueue<>(), channel);
        mock.close();
        verify(channel, times(1)).shutdown();
    }

    private KafkaMessageStream<String, byte[]> emptyStream() {
        return new KafkaMessageStream<String, byte[]>(new PriorityBlockingQueue<>(), messageChannel());
    }

    private GenericTrackedDomainEventMessage<String> trackedDomainEvent(String aggregateId) {
        return new GenericTrackedDomainEventMessage<>(null,
                                                      domainMessage(aggregateId));
    }

    private GenericDomainEventMessage<String> domainMessage(String aggregateId) {
        return new GenericDomainEventMessage<>("Stub", aggregateId, 1L, "Payload", MetaData.with("key", "value"));
    }

    private KafkaMessageStream<String, byte[]> stream(List<GenericTrackedDomainEventMessage<String>> messages) {
        PriorityBlockingQueue<MessageAndTimestamp> buffer = new PriorityBlockingQueue<>(messages.size());

        for (GenericTrackedDomainEventMessage<String> message : messages) {
            buffer.add(new MessageAndTimestamp(message, 0));
        }

        return new KafkaMessageStream<>(buffer, messageChannel());
    }

    @SuppressWarnings("unchecked")
    private Fetcher<String, byte[]> messageChannel() {
        return mock(Fetcher.class);
    }
}