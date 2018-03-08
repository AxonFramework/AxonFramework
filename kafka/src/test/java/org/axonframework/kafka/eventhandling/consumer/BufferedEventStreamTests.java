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
    public void testPeekOnNonEmptyStream() {
        GenericTrackedDomainEventMessage<String> firstMessage = trackedDomainEvent("foo");
        BufferedEventStream<String, byte[]> testSubject = stream(Arrays.asList(firstMessage,
                                                                               trackedDomainEvent("bar")));
        assertTrue(testSubject.peek().isPresent());
        assertThat(testSubject.peek().get(), is(firstMessage));
    }

    @Test
    public void testPeekOnAProgressiveStream() {
        GenericTrackedDomainEventMessage<String> firstMessage = trackedDomainEvent("foo");
        GenericTrackedDomainEventMessage<String> secondMessage = trackedDomainEvent("bar");
        BufferedEventStream<String, byte[]> testSubject = stream(Arrays.asList(firstMessage, secondMessage));
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
            BufferedEventStream<String, byte[]> testSubject = stream(
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
        BufferedEventStream<String, byte[]> testSubject = stream(singletonList(trackedDomainEvent("foo")));
        assertTrue(testSubject.hasNextAvailable(1, DEFAULT_TIMEOUT_UNIT));
        System.out.println("Thread.currentThread().isInterrupted() = " + Thread.currentThread().isInterrupted());
    }

    @Test
    public void testHasNextAvailableOnAProgressiveStream() {
        GenericTrackedDomainEventMessage<String> firstMessage = trackedDomainEvent("foo");
        GenericTrackedDomainEventMessage<String> secondMessage = trackedDomainEvent("bar");
        BufferedEventStream<String, byte[]> testSubject = stream(Arrays.asList(firstMessage, secondMessage));
        assertTrue(testSubject.hasNextAvailable(1, DEFAULT_TIMEOUT_UNIT));
        testSubject.nextAvailable();
        assertTrue(testSubject.hasNextAvailable(1, DEFAULT_TIMEOUT_UNIT));
        testSubject.nextAvailable();
        assertFalse(testSubject.hasNextAvailable(1, DEFAULT_TIMEOUT_UNIT));
    }

    @Test
    public void testHasNextOnAnInterruptedStream() {
        try {
            BufferedEventStream<String, byte[]> testSubject = stream(singletonList(trackedDomainEvent("foo")));
            Thread.currentThread().interrupt();
            assertFalse(testSubject.hasNextAvailable(1, DEFAULT_TIMEOUT_UNIT));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void testNextAvailableOnAProgressiveStream() {
        GenericTrackedDomainEventMessage<String> firstMessage = trackedDomainEvent("foo");
        GenericTrackedDomainEventMessage<String> secondMessage = trackedDomainEvent("bar");
        BufferedEventStream<String, byte[]> testSubject = stream(Arrays.asList(firstMessage, secondMessage));
        assertThat(testSubject.nextAvailable(), is(firstMessage));
        assertThat(testSubject.nextAvailable(), is(secondMessage));
    }

    @Test
    public void testNextAvailableOnAnInterruptedStream() {
        try {
            BufferedEventStream<String, byte[]> testSubject = stream(singletonList(trackedDomainEvent("foo")));
            Thread.currentThread().interrupt();
            assertNull(testSubject.nextAvailable());
        } finally {
            Thread.interrupted();
        }
    }

    private BufferedEventStream<String, byte[]> emptyStream() {
        return new BufferedEventStream<>(new PriorityBlockingQueue<>());
    }

    private BufferedEventStream<String, byte[]> stream(List<GenericTrackedDomainEventMessage<String>> messages) {
        PriorityBlockingQueue<MessageAndTimestamp> buffer = new PriorityBlockingQueue<>(messages.size());

        for (GenericTrackedDomainEventMessage<String> message : messages) {
            buffer.add(new MessageAndTimestamp(message, 0));
        }

        return new BufferedEventStream<>(buffer);
    }

    private GenericTrackedDomainEventMessage<String> trackedDomainEvent(String aggregateId) {
        return new GenericTrackedDomainEventMessage<>(null,
                                                      domainMessage(aggregateId));
    }

    private GenericDomainEventMessage<String> domainMessage(String aggregateId) {
        return new GenericDomainEventMessage<>("Stub", aggregateId, 1L, "Payload", MetaData.with("key", "value"));
    }
}