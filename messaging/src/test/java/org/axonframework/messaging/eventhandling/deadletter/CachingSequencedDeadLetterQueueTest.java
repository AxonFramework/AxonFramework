/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.eventhandling.deadletter;

import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.GenericDeadLetter;
import org.axonframework.messaging.deadletter.InMemorySequencedDeadLetterQueue;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.EventTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CachingSequencedDeadLetterQueue}.
 *
 * @author Mateusz Nowak
 */
class CachingSequencedDeadLetterQueueTest {

    private static final String SEQUENCE_ID_1 = "sequence-1";
    private static final String SEQUENCE_ID_2 = "sequence-2";

    private SequencedDeadLetterQueue<EventMessage> delegate;
    private CachingSequencedDeadLetterQueue<EventMessage> cachingQueue;

    @Nested
    class WhenDelegateStartsEmpty {

        @BeforeEach
        void setUp() {
            delegate = InMemorySequencedDeadLetterQueue.defaultQueue();
            cachingQueue = new CachingSequencedDeadLetterQueue<>(delegate);
        }

        @Test
        void containsReturnsFalseForUnknownSequence() {
            // given
            // empty queue

            // when
            Boolean result = cachingQueue.contains(SEQUENCE_ID_1).join();

            // then
            assertThat(result).isFalse();
        }

        @Test
        void containsReturnsFalseWithoutDelegateCallForUnknownSequence() {
            // given
            // empty queue, unknown sequence is not present

            // when
            Boolean firstResult = cachingQueue.contains(SEQUENCE_ID_1).join();
            Boolean secondResult = cachingQueue.contains(SEQUENCE_ID_1).join();

            // then
            assertThat(firstResult).isFalse();
            assertThat(secondResult).isFalse();
            // Both calls should be fast (cache hit on second call)
        }

        @Test
        void enqueueMarksSequenceAsEnqueued() {
            // given
            EventMessage event = EventTestUtils.createEvent(1);
            DeadLetter<EventMessage> letter = new GenericDeadLetter<>(SEQUENCE_ID_1, event);

            // when
            cachingQueue.enqueue(SEQUENCE_ID_1, letter).join();

            // then
            assertThat(cachingQueue.contains(SEQUENCE_ID_1).join()).isTrue();
            assertThat(cachingQueue.cacheEnqueuedSize()).isEqualTo(1);
        }

        @Test
        void enqueueIfPresentReturnsFalseForUnknownSequence() {
            // given
            EventMessage event = EventTestUtils.createEvent(1);

            // when
            Boolean result = cachingQueue.enqueueIfPresent(
                    SEQUENCE_ID_1,
                    () -> new GenericDeadLetter<>(SEQUENCE_ID_1, event)
            ).join();

            // then
            assertThat(result).isFalse();
            assertThat(delegate.size().join()).isZero();
        }

        @Test
        void enqueueIfPresentReturnsTrueForEnqueuedSequence() {
            // given
            EventMessage event1 = EventTestUtils.createEvent(1);
            EventMessage event2 = EventTestUtils.createEvent(2);
            cachingQueue.enqueue(SEQUENCE_ID_1, new GenericDeadLetter<>(SEQUENCE_ID_1, event1)).join();

            // when
            Boolean result = cachingQueue.enqueueIfPresent(
                    SEQUENCE_ID_1,
                    () -> new GenericDeadLetter<>(SEQUENCE_ID_1, event2)
            ).join();

            // then
            assertThat(result).isTrue();
            assertThat(delegate.sequenceSize(SEQUENCE_ID_1).join()).isEqualTo(2);
        }

        @Test
        void clearClearsCache() {
            // given
            EventMessage event = EventTestUtils.createEvent(1);
            cachingQueue.enqueue(SEQUENCE_ID_1, new GenericDeadLetter<>(SEQUENCE_ID_1, event)).join();
            assertThat(cachingQueue.cacheEnqueuedSize()).isEqualTo(1);

            // when
            cachingQueue.clear().join();

            // then
            assertThat(cachingQueue.cacheEnqueuedSize()).isZero();
            assertThat(delegate.size().join()).isZero();
        }

        @Test
        void onSegmentReleasedClearsCacheOnly() {
            // given
            EventMessage event = EventTestUtils.createEvent(1);
            cachingQueue.enqueue(SEQUENCE_ID_1, new GenericDeadLetter<>(SEQUENCE_ID_1, event)).join();
            assertThat(cachingQueue.cacheEnqueuedSize()).isEqualTo(1);
            long delegateSizeBefore = delegate.size().join();

            // when
            cachingQueue.invalidateCache();

            // then
            assertThat(cachingQueue.cacheEnqueuedSize()).isZero();
            assertThat(delegate.size().join()).isEqualTo(delegateSizeBefore);
        }

        @Test
        void containsWithLazyInitReturnsCorrectResultWhenDelegateModifiedBeforeFirstUse() {
            // given
            // With lazy initialization, the cache checks delegate state on first use.
            // If delegate is modified before first cache use, the cache will see that state.
            EventMessage event = EventTestUtils.createEvent(1);
            // Bypass caching queue and add directly to delegate BEFORE first cache use
            delegate.enqueue(SEQUENCE_ID_1, new GenericDeadLetter<>(SEQUENCE_ID_1, event)).join();

            // when
            // First cache use initializes by checking amountOfSequences(), which is now 1.
            // Cache sees queue as non-empty, so it queries delegate for the sequence.
            Boolean result = cachingQueue.contains(SEQUENCE_ID_1).join();

            // then
            // With lazy init, cache discovers the entry that was added directly to delegate
            assertThat(result).isTrue();
            assertThat(cachingQueue.cacheEnqueuedSize()).isEqualTo(1);
        }

        @Test
        void containsDoesNotQueryDelegateForUnknownSequenceWhenCacheInitializedWhileEmpty() {
            // given
            // Initialize cache while queue is empty by calling any method that triggers init
            cachingQueue.contains("trigger-init").join();
            // Now add directly to delegate - cache won't know about this
            EventMessage event = EventTestUtils.createEvent(1);
            delegate.enqueue(SEQUENCE_ID_1, new GenericDeadLetter<>(SEQUENCE_ID_1, event)).join();

            // when
            // Cache was initialized when queue was empty, so it optimizes by assuming
            // any unknown identifier is not present
            Boolean result = cachingQueue.contains(SEQUENCE_ID_1).join();

            // then
            // Returns false because cache optimization says "not present" without querying delegate
            assertThat(result).isFalse();
            assertThat(cachingQueue.cacheEnqueuedSize()).isZero();
        }
    }

    @Nested
    class WhenDelegateStartsNonEmpty {

        @BeforeEach
        void setUp() {
            delegate = InMemorySequencedDeadLetterQueue.defaultQueue();
            // Pre-populate delegate before creating caching queue
            EventMessage event = EventTestUtils.createEvent(1);
            delegate.enqueue(SEQUENCE_ID_1, new GenericDeadLetter<>(SEQUENCE_ID_1, event)).join();

            cachingQueue = new CachingSequencedDeadLetterQueue<>(delegate);
        }

        @Test
        void containsMightBePresentForUnknownSequence() {
            // given
            // Queue started non-empty, so unknown sequences might be present

            // when
            Boolean result = cachingQueue.contains(SEQUENCE_ID_2).join();

            // then
            // Must query delegate since cache doesn't know about SEQUENCE_ID_2
            assertThat(result).isFalse();
            // Cache should now remember it's not present
            assertThat(cachingQueue.cacheNonEnqueuedSize()).isEqualTo(1);
        }

        @Test
        void containsUpdatesNonEnqueuedCache() {
            // given
            // Unknown sequence in non-empty queue

            // when
            cachingQueue.contains(SEQUENCE_ID_2).join();
            cachingQueue.contains(SEQUENCE_ID_2).join();

            // then
            assertThat(cachingQueue.cacheNonEnqueuedSize()).isEqualTo(1);
        }

        @Test
        void containsReturnsTrueForExistingSequence() {
            // given
            // SEQUENCE_ID_1 was pre-populated

            // when
            Boolean result = cachingQueue.contains(SEQUENCE_ID_1).join();

            // then
            assertThat(result).isTrue();
            assertThat(cachingQueue.cacheEnqueuedSize()).isEqualTo(1);
        }

        @Test
        void onSegmentReleasedClearsNonEnqueuedCache() {
            // given
            cachingQueue.contains(SEQUENCE_ID_2).join();
            assertThat(cachingQueue.cacheNonEnqueuedSize()).isEqualTo(1);

            // when
            cachingQueue.invalidateCache();

            // then
            assertThat(cachingQueue.cacheNonEnqueuedSize()).isZero();
            assertThat(cachingQueue.cacheEnqueuedSize()).isZero();
        }

        @Test
        void enqueueIfPresentUsesCache() {
            // given
            cachingQueue.contains(SEQUENCE_ID_2).join();
            assertThat(cachingQueue.cacheNonEnqueuedSize()).isEqualTo(1);
            EventMessage event = EventTestUtils.createEvent(2);

            // when
            Boolean result = cachingQueue.enqueueIfPresent(
                    SEQUENCE_ID_2,
                    () -> new GenericDeadLetter<>(SEQUENCE_ID_2, event)
            ).join();

            // then
            // Cache knows SEQUENCE_ID_2 is not present, so returns false without calling delegate
            assertThat(result).isFalse();
        }

        @Test
        void containsUpdatesCacheWhenDelegateQueried() {
            // given
            // When the queue started non-empty, cache must query delegate for unknown identifiers
            EventMessage event = EventTestUtils.createEvent(2);
            // Add directly to delegate - cache doesn't know about this yet
            delegate.enqueue(SEQUENCE_ID_2, new GenericDeadLetter<>(SEQUENCE_ID_2, event)).join();
            assertThat(cachingQueue.cacheEnqueuedSize()).isZero();

            // when
            // Cache queries delegate and discovers SEQUENCE_ID_2 is present
            Boolean result = cachingQueue.contains(SEQUENCE_ID_2).join();

            // then
            assertThat(result).isTrue();
            assertThat(cachingQueue.cacheEnqueuedSize()).isEqualTo(1);
        }
    }

    @Nested
    class WhenDelegatingOperations {

        @BeforeEach
        void setUp() {
            delegate = InMemorySequencedDeadLetterQueue.defaultQueue();
            cachingQueue = new CachingSequencedDeadLetterQueue<>(delegate);
        }

        @Test
        void sizeIsDelegated() {
            // given
            EventMessage event = EventTestUtils.createEvent(1);
            cachingQueue.enqueue(SEQUENCE_ID_1, new GenericDeadLetter<>(SEQUENCE_ID_1, event)).join();

            // when
            Long size = cachingQueue.size().join();

            // then
            assertThat(size).isEqualTo(1L);
        }

        @Test
        void amountOfSequencesIsDelegated() {
            // given
            EventMessage event1 = EventTestUtils.createEvent(1);
            EventMessage event2 = EventTestUtils.createEvent(2);
            cachingQueue.enqueue(SEQUENCE_ID_1, new GenericDeadLetter<>(SEQUENCE_ID_1, event1)).join();
            cachingQueue.enqueue(SEQUENCE_ID_2, new GenericDeadLetter<>(SEQUENCE_ID_2, event2)).join();

            // when
            Long amount = cachingQueue.amountOfSequences().join();

            // then
            assertThat(amount).isEqualTo(2L);
        }

        @Test
        void sequenceSizeIsDelegated() {
            // given
            EventMessage event1 = EventTestUtils.createEvent(1);
            EventMessage event2 = EventTestUtils.createEvent(2);
            cachingQueue.enqueue(SEQUENCE_ID_1, new GenericDeadLetter<>(SEQUENCE_ID_1, event1)).join();
            cachingQueue.enqueue(SEQUENCE_ID_1, new GenericDeadLetter<>(SEQUENCE_ID_1, event2)).join();

            // when
            Long size = cachingQueue.sequenceSize(SEQUENCE_ID_1).join();

            // then
            assertThat(size).isEqualTo(2L);
        }

        @Test
        void isFullIsDelegated() {
            // given
            // Default queue has max sequences of 1024

            // when
            Boolean isFull = cachingQueue.isFull(SEQUENCE_ID_1).join();

            // then
            assertThat(isFull).isFalse();
        }

        @Test
        void deadLettersIsDelegated() {
            // given
            EventMessage event = EventTestUtils.createEvent(1);
            cachingQueue.enqueue(SEQUENCE_ID_1, new GenericDeadLetter<>(SEQUENCE_ID_1, event)).join();

            // when
            Iterable<Iterable<DeadLetter<? extends EventMessage>>> deadLetters =
                    cachingQueue.deadLetters().join();

            // then
            assertThat(deadLetters).hasSize(1);
        }

        @Test
        void deadLetterSequenceIsDelegated() {
            // given
            EventMessage event = EventTestUtils.createEvent(1);
            cachingQueue.enqueue(SEQUENCE_ID_1, new GenericDeadLetter<>(SEQUENCE_ID_1, event)).join();

            // when
            Iterable<DeadLetter<? extends EventMessage>> sequence =
                    cachingQueue.deadLetterSequence(SEQUENCE_ID_1).join();

            // then
            assertThat(sequence).hasSize(1);
        }

        @Test
        void evictIsDelegated() {
            // given
            EventMessage event = EventTestUtils.createEvent(1);
            DeadLetter<EventMessage> letter = new GenericDeadLetter<>(SEQUENCE_ID_1, event);
            cachingQueue.enqueue(SEQUENCE_ID_1, letter).join();

            // when
            cachingQueue.evict(letter).join();

            // then
            assertThat(delegate.contains(SEQUENCE_ID_1).join()).isFalse();
        }

        @Test
        void requeueIsDelegated() {
            // given
            EventMessage event = EventTestUtils.createEvent(1);
            DeadLetter<EventMessage> letter = new GenericDeadLetter<>(SEQUENCE_ID_1, event);
            cachingQueue.enqueue(SEQUENCE_ID_1, letter).join();

            // when
            cachingQueue.requeue(letter, l -> l.withDiagnostics(l.diagnostics())).join();

            // then
            assertThat(delegate.contains(SEQUENCE_ID_1).join()).isTrue();
        }
    }

    @Nested
    class WhenUsingCustomCacheMaxSize {

        @Test
        void customMaxSizeIsApplied() {
            // given
            delegate = InMemorySequencedDeadLetterQueue.defaultQueue();
            // Pre-populate to make startedEmpty=false
            EventMessage event = EventTestUtils.createEvent(1);
            delegate.enqueue("existing", new GenericDeadLetter<>("existing", event)).join();

            int customMaxSize = 3;
            cachingQueue = new CachingSequencedDeadLetterQueue<>(delegate, customMaxSize);

            // when
            for (int i = 0; i < 5; i++) {
                cachingQueue.contains("unknown-" + i).join();
            }

            // then
            // LRU eviction should keep only customMaxSize entries
            assertThat(cachingQueue.cacheNonEnqueuedSize()).isEqualTo(customMaxSize);
        }
    }
}
