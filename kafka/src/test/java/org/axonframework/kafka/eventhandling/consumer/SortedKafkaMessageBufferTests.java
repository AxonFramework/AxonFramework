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


import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.axonframework.eventsourcing.eventstore.EventUtils.asTrackedEventMessage;

/**
 * Tests for {@link SortedKafkaMessageBuffer}.
 *
 * @author Nakul Mishra.
 */
public class SortedKafkaMessageBufferTests extends JSR166TestCase {

    public void testCreateBuffer_WithNonPositiveCapacity() {
        try {
            new SortedKafkaMessageBuffer<>(-1);
            shouldThrow();
        } catch (IllegalArgumentException success) {
        }
    }

    public void testPutInvalidMessageInABuffer() throws InterruptedException {
        try {
            new SortedKafkaMessageBuffer<>().put(null);
            shouldThrow();
        } catch (IllegalArgumentException success) {
        }
    }

    /**
     * A new buffer has the indicated capacity
     */
    public void testCreateBuffer() {
        assertThat(new SortedKafkaMessageBuffer(SIZE).remainingCapacity()).isEqualTo(SIZE);
    }

    /**
     * Queue transitions from empty to full when elements added
     */
    public void testEmptyFull() {
        SortedKafkaMessageBuffer<KafkaEventMessage> buff = populatedBuffer(0, 2, 2);
        assertThat(buff.isEmpty()).isTrue();
        assertEquals(2, buff.remainingCapacity());
        try {
            buff.put(message(0, 0, 0, "m0"));
            buff.put(message(0, 0, 0, "m1"));
            assertThat(buff.remainingCapacity()).isOne();
            assertThat(buff.isEmpty()).isFalse();
            buff.put(message(0, 1, 1, "m1"));
            assertThat(buff.isEmpty()).isFalse();
            assertThat(buff.remainingCapacity()).isZero();
        } catch (InterruptedException failure) {
            failure.printStackTrace();
        }
    }

    public void testIsEmpty() {
        SortedKafkaMessageBuffer<? extends Comparable> buff = new SortedKafkaMessageBuffer<>();
        assertThat(buff.isEmpty()).isTrue();
        assertThat(buff.size()).isZero();
    }

    /**
     * remainingCapacity decreases on add, increases on remove
     */
    public void testRemainingCapacity() throws InterruptedException {
        int size = ThreadLocalRandom.current().nextInt(1, SIZE);
        SortedKafkaMessageBuffer<KafkaEventMessage> buff = populatedBuffer(size, size, 2 * size);
        int spare = buff.remainingCapacity();
        int capacity = spare + size;
        for (int i = 0; i < size; i++) {
            assertThat(buff.remainingCapacity()).isEqualTo(spare + i);
            assertThat(buff.size() + buff.remainingCapacity()).isEqualTo(capacity);
            assertThat(buff.take()).isNotNull();
        }
        for (int i = 0; i < size; i++) {
            assertThat(buff.remainingCapacity()).isEqualTo(capacity - i);
            assertThat(buff.size() + buff.remainingCapacity()).isEqualTo(capacity);
            buff.put(message(0, i, i, "a"));
            assertThat(buff.peek()).isNotNull();
        }
    }

    static SortedKafkaMessageBuffer<KafkaEventMessage> populatedBuffer(int size, int minCapacity, int maxCapacity) {
        SortedKafkaMessageBuffer<KafkaEventMessage> buff = null;
        try {
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            int capacity = rnd.nextInt(minCapacity, maxCapacity + 1);
            buff = new SortedKafkaMessageBuffer<>(capacity);
            assertTrue(buff.isEmpty());
            // shuffle circular array elements so they wrap
            {
                int n = rnd.nextInt(capacity);
                for (int i = 0; i < n; i++) {
                    buff.put(message(42, 42, 42, "42"));
                }
                for (int i = 0; i < n; i++) {
                    buff.poll(1, TimeUnit.NANOSECONDS);
                }
            }
            for (int i = 0; i < size; i++) {
                buff.put(message(i, i, i, "ma"));
            }
            assertEquals(size == 0, buff.isEmpty());
            assertEquals(capacity - size, buff.remainingCapacity());
            assertEquals(size, buff.size());
            if (size > 0) {
                assertThat(buff.peek()).isNotNull();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return buff;
    }


    /**
     * put blocks interruptibly if full
     */
    public void testBlockingPut() {
        final SortedKafkaMessageBuffer<KafkaEventMessage> buff = new SortedKafkaMessageBuffer<>(SIZE);
        final CountDownLatch pleaseInterrupt = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                for (int i = 0; i < SIZE; ++i) {
                    buff.put(message(i, i, i, "m"));
                }
                assertThat(buff.size()).isEqualTo(SIZE);
                assertThat(buff.remainingCapacity()).isZero();

                Thread.currentThread().interrupt();
                try {
                    buff.put(message(99, 99, 99, "m"));
                    shouldThrow();
                } catch (InterruptedException success) {
                }

                assertThat(Thread.interrupted()).isFalse();

                pleaseInterrupt.countDown();
                try {
                    buff.put(message(99, 99, 99, "m"));
                    shouldThrow();
                } catch (InterruptedException success) {
                }
                assertThat(Thread.interrupted()).isFalse();
            }
        });

        await(pleaseInterrupt);
        assertThreadBlocks(t, Thread.State.WAITING);
        t.interrupt();
        awaitTermination(t);
        assertThat(SIZE).isEqualTo(buff.size());
        assertThat(buff.remainingCapacity()).isZero();
    }

    /**
     * Checks that thread eventually enters the expected blocked thread state.
     */
    void assertThreadBlocks(Thread thread, Thread.State expected) {
        // always sleep at least 1 ms, with high probability avoiding
        // transitory states
        for (long retries = LONG_DELAY_MS * 3 / 4; retries-- > 0; ) {
            try {
                delay(1);
            } catch (InterruptedException fail) {
                fail("Unexpected InterruptedException");
            }
            Thread.State s = thread.getState();
            if (s == expected) {
                return;
            } else if (s == Thread.State.TERMINATED) {
                fail("Unexpected thread termination");
            }
        }
        fail("timed out waiting for thread to enter thread state " + expected);
    }

    public void testPutAndPoll_TimestampOrdering() throws InterruptedException {
        List<KafkaEventMessage> messages = asList(message(2, 0, 0, "m0"),
                                                  message(2, 1, 1, "m1"),
                                                  message(2, 2, 2, "m2"),
                                                  message(2, 3, 8, "m8"),
                                                  message(2, 4, 9, "m9"),
                                                  message(2, 5, 11, "m11"),
                                                  message(0, 0, 3, "m3"),
                                                  message(0, 1, 4, "m4"),
                                                  message(0, 2, 5, "m5"),
                                                  message(0, 3, 10, "m10"),
                                                  message(0, 4, 7, "m7"),
                                                  message(1, 0, 6, "m6"));
        SortedKafkaMessageBuffer<KafkaEventMessage> buffer = new SortedKafkaMessageBuffer<>();
        for (int i = 0; i < messages.size(); i++) {
            buffer.put(messages.get(i));
            assertThat(buffer.size()).isEqualTo(i + 1);
        }
        for (int i = 0; i < messages.size(); i++) {
            Object payload = buffer.poll(0, NANOSECONDS).value().getPayload();
            assertThat(payload).isEqualTo("m" + i);
            assertThat(buffer.size()).isEqualTo(messages.size() - (i + 1));
        }
        assertThat(buffer.isEmpty()).isTrue();
    }

    public void testPutAndTake_TimestampOrdering() throws InterruptedException {
        List<KafkaEventMessage> messages = asList(message(2, 0, 0, "m0"),
                                                  message(2, 1, 1, "m1"),
                                                  message(2, 2, 2, "m2"),
                                                  message(2, 3, 8, "m8"),
                                                  message(2, 4, 9, "m9"),
                                                  message(2, 5, 11, "m11"),
                                                  message(0, 0, 3, "m3"),
                                                  message(0, 1, 4, "m4"),
                                                  message(0, 2, 5, "m5"),
                                                  message(0, 3, 10, "m10"),
                                                  message(0, 4, 7, "m7"),
                                                  message(1, 0, 6, "m6"));
        SortedKafkaMessageBuffer<KafkaEventMessage> buffer = new SortedKafkaMessageBuffer<>();
        for (int i = 0; i < messages.size(); i++) {
            buffer.put(messages.get(i));
            assertThat(buffer.size()).isEqualTo(i + 1);
        }
        for (int i = 0; i < messages.size(); i++) {
            Object payload = buffer.take().value().getPayload();
            assertThat(payload).isEqualTo("m" + i);
            assertThat(buffer.size()).isEqualTo(messages.size() - (i + 1));
        }
        assertThat(buffer.isEmpty()).isTrue();
    }

    public void testPutAndPoll_ProgressiveBuffer() throws InterruptedException {
        SortedKafkaMessageBuffer<KafkaEventMessage> buffer = new SortedKafkaMessageBuffer<>();
        assertThat(buffer.poll(10, NANOSECONDS)).isNull();
        buffer.put(message(0, 10, 10, "m10"));
        buffer.put(message(0, 11, 11, "m11"));
        buffer.put(message(1, 20, 5, "m5"));
        buffer.put(message(100, 0, 0, "m0"));
        assertThat(buffer.poll(0, NANOSECONDS).value().getPayload()).isEqualTo("m0");
        assertThat(buffer.poll(0, NANOSECONDS).value().getPayload()).isEqualTo("m5");
        buffer.put(message(0, 9, 9, "m9"));
        assertThat(buffer.poll(0, NANOSECONDS).value().getPayload()).isEqualTo("m9");
        assertThat(buffer.poll(0, NANOSECONDS).value().getPayload()).isEqualTo("m10");
        assertThat(buffer.poll(0, NANOSECONDS).value().getPayload()).isEqualTo("m11");
        assertThat(new SortedKafkaMessageBuffer<>().poll(0, NANOSECONDS)).isNull();
    }

    public void testPutAndTake_ProgressiveBuffer() throws InterruptedException {
        SortedKafkaMessageBuffer<KafkaEventMessage> buffer = new SortedKafkaMessageBuffer<>();
        buffer.put(message(0, 10, 10, "m10"));
        buffer.put(message(0, 11, 11, "m11"));
        buffer.put(message(1, 20, 5, "m5"));
        buffer.put(message(100, 0, 0, "m0"));
        assertThat(buffer.take().value().getPayload()).isEqualTo("m0");
        assertThat(buffer.take().value().getPayload()).isEqualTo("m5");
        buffer.put(message(0, 9, 9, "m9"));
        assertThat(buffer.take().value().getPayload()).isEqualTo("m9");
        assertThat(buffer.take().value().getPayload()).isEqualTo("m10");
        assertThat(buffer.take().value().getPayload()).isEqualTo("m11");
        assertThat(new SortedKafkaMessageBuffer<>().poll(0, NANOSECONDS)).isNull();
    }

    public void testPutAndPoll_MessagesPublishedAtSameTime_OnSamePartition()
            throws InterruptedException {
        List<KafkaEventMessage> messages = asList(message(0, 0, 1, "m0"),
                                                  message(0, 1, 1, "m1"),
                                                  message(0, 2, 1, "m2"));
        SortedKafkaMessageBuffer<KafkaEventMessage> buffer = new SortedKafkaMessageBuffer<>();
        for (int i = 0; i < messages.size(); i++) {
            buffer.put(messages.get(i));
            assertThat(buffer.size()).isEqualTo(i + 1);
        }
        for (int i = 0; i < messages.size(); i++) {
            Object payload = buffer.poll(0, NANOSECONDS).value().getPayload();
            assertThat(payload).isEqualTo("m" + i);
            assertThat(buffer.size()).isEqualTo(messages.size() - (i + 1));
        }
        assertThat(buffer.isEmpty()).isTrue();
    }

    public void testPutAndTake_MessagesPublishedAtSameTime_OnSamePartition()
            throws InterruptedException {
        List<KafkaEventMessage> messages = asList(message(0, 0, 1, "m0"),
                                                  message(0, 1, 1, "m1"),
                                                  message(0, 2, 1, "m2"));
        SortedKafkaMessageBuffer<KafkaEventMessage> buffer = new SortedKafkaMessageBuffer<>();
        for (int i = 0; i < messages.size(); i++) {
            buffer.put(messages.get(i));
            assertThat(buffer.size()).isEqualTo(i + 1);
        }
        for (int i = 0; i < messages.size(); i++) {
            Object payload = buffer.take().value().getPayload();
            assertThat(payload).isEqualTo("m" + i);
            assertThat(buffer.size()).isEqualTo(messages.size() - (i + 1));
        }
        assertThat(buffer.isEmpty()).isTrue();
    }

    public void testPutAndPoll_MessagesPublishedAtSameTime_AcrossDifferentPartitions()
            throws InterruptedException {
        List<KafkaEventMessage> messages = asList(message(0, 0, 1, "m0"),
                                                  message(1, 0, 1, "m1"),
                                                  message(2, 0, 1, "m2"));
        SortedKafkaMessageBuffer<KafkaEventMessage> buffer = new SortedKafkaMessageBuffer<>();
        for (int i = 0; i < messages.size(); i++) {
            buffer.put(messages.get(i));
            assertThat(buffer.size()).isEqualTo(i + 1);
        }
        for (int i = 0; i < messages.size(); i++) {
            Object payload = buffer.poll(0, NANOSECONDS).value().getPayload();
            assertThat(payload).isEqualTo("m" + i);
            assertThat(buffer.size()).isEqualTo(messages.size() - (i + 1));
        }
        assertThat(buffer.isEmpty()).isTrue();
    }

    public void testPutAndTake_MessagesPublishedAtSameTime_AcrossDifferentPartitions()
            throws InterruptedException {
        List<KafkaEventMessage> messages = asList(message(0, 0, 1, "m0"),
                                                  message(1, 0, 1, "m1"),
                                                  message(2, 0, 1, "m2"));
        SortedKafkaMessageBuffer<KafkaEventMessage> buffer = new SortedKafkaMessageBuffer<>();
        for (int i = 0; i < messages.size(); i++) {
            buffer.put(messages.get(i));
            assertThat(buffer.size()).isEqualTo(i + 1);
        }
        for (int i = 0; i < messages.size(); i++) {
            Object payload = buffer.take().value().getPayload();
            assertThat(payload).isEqualTo("m" + i);
            assertThat(buffer.size()).isEqualTo(messages.size() - (i + 1));
        }
        assertThat(buffer.isEmpty()).isTrue();
    }

    public void testPeekAndPoll_ProgressiveBuffer() throws InterruptedException {
        SortedKafkaMessageBuffer<KafkaEventMessage> buffer = new SortedKafkaMessageBuffer<>();
        assertThat(buffer.peek()).isNull();
        //insert two messages - timestamp in ASCENDING order
        buffer.put(message(0, 10, 10, "m10"));
        assertThat(buffer.peek().value().getPayload()).isEqualTo(("m10"));
        buffer.put(message(0, 11, 11, "m11"));
        assertThat(buffer.peek().value().getPayload()).isEqualTo(("m10"));
        //insert two messages - timestamp in DESCENDING order
        buffer.put(message(1, 20, 5, "m5"));
        assertThat(buffer.peek().value().getPayload()).isEqualTo(("m5"));
        buffer.put(message(100, 0, 0, "m0"));
        assertThat(buffer.peek().value().getPayload()).isEqualTo(("m0"));
        //remove m0
        assertThat(buffer.poll(0, NANOSECONDS).value().getPayload()).isEqualTo("m0");
        assertThat(buffer.peek().value().getPayload()).isEqualTo(("m5"));
        //remove m5
        assertThat(buffer.poll(0, NANOSECONDS).value().getPayload()).isEqualTo("m5");
        assertThat(buffer.peek().value().getPayload()).isEqualTo(("m10"));
        //add m9
        buffer.put(message(0, 9, 9, "m9"));
        assertThat(buffer.peek().value().getPayload()).isEqualTo(("m9"));
        //remove m9
        assertThat(buffer.poll(0, NANOSECONDS).value().getPayload()).isEqualTo("m9");
        assertThat(buffer.peek().value().getPayload()).isEqualTo(("m10"));
        //remove m10
        assertThat(buffer.poll(0, NANOSECONDS).value().getPayload()).isEqualTo("m10");
        assertThat(buffer.peek().value().getPayload()).isEqualTo(("m11"));
        //finally remove last message(m11)
        assertThat(buffer.poll(0, NANOSECONDS).value().getPayload()).isEqualTo("m11");
        assertThat(buffer.peek()).isNull();
        assertThat(buffer.isEmpty()).isTrue();
    }

    public void testPeek_MessagesPublishedAtTheSameTime_AcrossDifferentPartitions()
            throws InterruptedException {
        SortedKafkaMessageBuffer<KafkaEventMessage> buffer = new SortedKafkaMessageBuffer<>();
        List<KafkaEventMessage> messages = asList(message(0, 0, 1, "m0"),
                                                  message(2, 1, 1, "m1"),
                                                  message(1, 0, 1, "m2")
        );
        assertThat(new SortedKafkaMessageBuffer<>().peek()).isNull();
        for (KafkaEventMessage message : messages) {
            buffer.put(message);
        }
        assertThat(buffer.peek().value().getPayload()).isEqualTo("m0");
        buffer.poll(0, NANOSECONDS);
        assertThat(buffer.peek().value().getPayload()).isEqualTo("m2");
        buffer.poll(0, NANOSECONDS);
        assertThat(buffer.peek().value().getPayload()).isEqualTo("m1");
        buffer.poll(0, NANOSECONDS);
        assertThat(buffer.isEmpty()).isTrue();
    }

    public void testPoll_OffsetOrdering() throws InterruptedException {
        SortedKafkaMessageBuffer<KafkaEventMessage> buffer = new SortedKafkaMessageBuffer<>();
        List<KafkaEventMessage> messages = asList(message(1, 1, 1, "m-p1"),
                                                  message(0, 1, 2, "m-p0-1"),
                                                  message(0, 0, 2, "m-p0-0"),
                                                  message(2, 2, 0, "m-p2"));
        for (KafkaEventMessage message : messages) {
            buffer.put(message);
        }
        // m-p2, published at T0
        // m-p1, published at T1
        // m-p0-0, published at T2.
        // m-p0-1, also published at T2 but has offset(1) greater than m-p0-0 offset(0).
        List<KafkaEventMessage> ordered = asList(message(2, 2, 0, "m-p2"),
                                                 message(1, 1, 1, "m-p1"),
                                                 message(0, 0, 2, "m-p0-0"),
                                                 message(0, 1, 2, "m-p0-1"));

        for (int i = 0; i < messages.size(); i++) {
            assertThat(buffer.poll(0, MILLISECONDS).value().getPayload()).isEqualTo(ordered.get(i).value()
                                                                                           .getPayload());
        }
        assertThat(buffer.isEmpty()).isTrue();
    }

    public void testTake_OffsetOrdering() throws InterruptedException {
        SortedKafkaMessageBuffer<KafkaEventMessage> buffer = new SortedKafkaMessageBuffer<>();
        List<KafkaEventMessage> messages = asList(message(1, 1, 1, "m-p1"),
                                                  message(0, 1, 2, "m-p0-1"),
                                                  message(0, 0, 2, "m-p0-0"),
                                                  message(2, 2, 0, "m-p2"));
        for (KafkaEventMessage message : messages) {
            buffer.put(message);
        }
        // m-p2, published at T0
        // m-p1, published at T1
        // m-p0-0, published at T2.
        // m-p0-1, also published at T2 but has offset(1) greater than m-p0-0 offset(0).
        List<KafkaEventMessage> ordered = asList(message(2, 2, 0, "m-p2"),
                                                 message(1, 1, 1, "m-p1"),
                                                 message(0, 0, 2, "m-p0-0"),
                                                 message(0, 1, 2, "m-p0-1"));

        for (int i = 0; i < messages.size(); i++) {
            assertThat(buffer.take().value().getPayload()).isEqualTo(ordered.get(i).value().getPayload());
        }
        assertThat(buffer.isEmpty()).isTrue();
    }

    public void testPoll_OnAnInterruptedStream() throws InterruptedException {
        try {
            SortedKafkaMessageBuffer<KafkaEventMessage> buffer = new SortedKafkaMessageBuffer<>();
            Thread.currentThread().interrupt();
            try {
                buffer.poll(0, NANOSECONDS);
                shouldThrow();
            } catch (InterruptedException success) {
            }
        } finally {
            Thread.interrupted();
        }
    }

    public void testPeek_OnAnInterruptedStream() {
        try {
            SortedKafkaMessageBuffer<KafkaEventMessage> buffer = new SortedKafkaMessageBuffer<>();
            Thread.currentThread().interrupt();
            assertThat(buffer.peek()).isNull();
        } finally {
            Thread.interrupted();
        }
    }

    public void testPut_OnAnInterruptedStream() throws InterruptedException {
        try {
            SortedKafkaMessageBuffer<KafkaEventMessage> buffer = new SortedKafkaMessageBuffer<>();
            Thread.currentThread().interrupt();
            try {
                buffer.put(message(0, 0, 1, "foo"));
                shouldThrow();
            } catch (InterruptedException success) {
            }
        } finally {
            Thread.interrupted();
        }
    }

    public void testTake_OnAnInterruptedStream() throws InterruptedException {
        try {
            SortedKafkaMessageBuffer<KafkaEventMessage> buffer = new SortedKafkaMessageBuffer<>();
            Thread.currentThread().interrupt();
            try {
                buffer.take();
                shouldThrow();
            } catch (InterruptedException success) {
            }
        } finally {
            Thread.interrupted();
        }
    }

    public void testConcurrentPeekAndPoll() throws Throwable {
        SortedKafkaMessageBuffer<KafkaEventMessage> buffer = new SortedKafkaMessageBuffer<>();
        AtomicInteger partition = new AtomicInteger(0);
        concurrent(4, () -> {
            for (int i = 0; i < 100; i++) {
                try {
                    buffer.put(message(partition.getAndIncrement(), i, i + 1, "m"));
                } catch (InterruptedException e) {
                    fail("This shouldn't happen");
                }
                assertThat(buffer.size() > 0);
                assertThat(buffer.peek()).isNotNull();
                try {
                    assertThat(buffer.poll(0, TimeUnit.NANOSECONDS)).isNotNull();
                } catch (InterruptedException e) {
                    fail("This shouldn't happen");
                }
            }
        });
        assertThat(buffer.isEmpty()).isTrue();
    }

    public void testConcurrentPeekAndTake() throws Throwable {
        SortedKafkaMessageBuffer<KafkaEventMessage> buffer = new SortedKafkaMessageBuffer<>();
        AtomicInteger partition = new AtomicInteger(0);
        concurrent(4, () -> {
            for (int i = 0; i < 100; i++) {
                try {
                    buffer.put(message(partition.getAndIncrement(), i, i + 1, "m"));
                } catch (InterruptedException e) {
                    fail("This shouldn't happen");
                }
                assertThat(buffer.size() > 0);
                assertThat(buffer.peek()).isNotNull();
                try {
                    assertThat(buffer.take()).isNotNull();
                } catch (InterruptedException e) {
                    fail("This shouldn't happen");
                }
            }
        });
        assertThat(buffer.isEmpty()).isTrue();
    }


    private static void concurrent(int noOfThreads, Runnable task) throws Throwable {
        CyclicBarrier barrier = new CyclicBarrier(noOfThreads + 1);
        ExecutorService pool = Executors.newFixedThreadPool(noOfThreads);
        Collection<Future<Void>> futures = new LinkedList<>();
        for (int i = 0; i < noOfThreads; i++) {
            futures.add(pool.submit(() -> {
                barrier.await();
                task.run();
                return null;
            }));
        }
        barrier.await();
        pool.shutdown();

        for (Future<Void> future : futures) {
            try {
                future.get(1, TimeUnit.MINUTES);
            } catch (ExecutionException e) {
                if (isAssertionError(e)) {
                    throw e.getCause();
                }
                throw e.getCause();
            } catch (TimeoutException e) {
                fail("Updates took to long.");
            }
        }
        //Test should complete in max 1 Minute.
        assertThat(pool.awaitTermination(1, TimeUnit.MINUTES))
                .as("Excepted to finish in a minute but took longer")
                .isTrue();
    }

    private static boolean isAssertionError(ExecutionException e) {
        return e.getCause() instanceof AssertionError;
    }

    private static KafkaEventMessage message(int partition, int offset, int timestamp, String value) {
        return new KafkaEventMessage(asTrackedEventMessage(asEventMessage(value), null), partition, offset, timestamp);
    }
}