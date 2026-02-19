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
            Boolean result = cachingQueue.contains(SEQUENCE_ID_1, null).join();

            // then
            assertThat(result).isFalse();
        }

        @Test
        void containsReturnsFalseWithoutDelegateCallForUnknownSequence() {
            // given
            // empty queue, unknown sequence is not present

            // when
            Boolean firstResult = cachingQueue.contains(SEQUENCE_ID_1, null).join();
            Boolean secondResult = cachingQueue.contains(SEQUENCE_ID_1, null).join();

            // then
            assertThat(firstResult).isFalse();
            assertThat(secondResult).isFalse();
        }

        @Test
        void enqueueMarksSequenceAsEnqueued() {
            // given
            EventMessage event = EventTestUtils.createEvent(1);
            DeadLetter<EventMessage> letter = new GenericDeadLetter<>(SEQUENCE_ID_1, event);

            // when
            cachingQueue.enqueue(SEQUENCE_ID_1, letter, null).join();

            // then
            assertThat(cachingQueue.contains(SEQUENCE_ID_1, null).join()).isTrue();
            assertThat(cachingQueue.cacheEnqueuedSize()).isEqualTo(1);
        }

        @Test
        void enqueueIfPresentReturnsFalseForUnknownSequence() {
            // given
            EventMessage event = EventTestUtils.createEvent(1);

            // when
            var result = cachingQueue.enqueueIfPresent(
                    SEQUENCE_ID_1,
                    () -> new GenericDeadLetter<>(SEQUENCE_ID_1, event),
                    null
            ).join();

            // then
            assertThat(result).isFalse();
            assertThat(delegate.size(null).join()).isZero();
        }

        @Test
        void enqueueIfPresentReturnsTrueForEnqueuedSequence() {
            // given
            EventMessage event1 = EventTestUtils.createEvent(1);
            EventMessage event2 = EventTestUtils.createEvent(2);
            cachingQueue.enqueue(SEQUENCE_ID_1, new GenericDeadLetter<>(SEQUENCE_ID_1, event1), null).join();

            // when
            var result = cachingQueue.enqueueIfPresent(
                    SEQUENCE_ID_1,
                    () -> new GenericDeadLetter<>(SEQUENCE_ID_1, event2),
                    null
            ).join();

            // then
            assertThat(result).isTrue();
            assertThat(delegate.sequenceSize(SEQUENCE_ID_1, null).join()).isEqualTo(2);
        }

        @Test
        void clearClearsCache() {
            // given
            EventMessage event = EventTestUtils.createEvent(1);
            cachingQueue.enqueue(SEQUENCE_ID_1, new GenericDeadLetter<>(SEQUENCE_ID_1, event), null).join();
            assertThat(cachingQueue.cacheEnqueuedSize()).isEqualTo(1);

            // when
            cachingQueue.clear(null).join();

            // then
            assertThat(cachingQueue.cacheEnqueuedSize()).isZero();
            assertThat(delegate.size(null).join()).isZero();
        }

        @Test
        void invalidateCacheClearsCacheOnly() {
            // given
            EventMessage event = EventTestUtils.createEvent(1);
            cachingQueue.enqueue(SEQUENCE_ID_1, new GenericDeadLetter<>(SEQUENCE_ID_1, event), null).join();
            assertThat(cachingQueue.cacheEnqueuedSize()).isEqualTo(1);
            long delegateSizeBefore = delegate.size(null).join();

            // when
            cachingQueue.invalidateCache();

            // then
            assertThat(cachingQueue.cacheEnqueuedSize()).isZero();
            assertThat(delegate.size(null).join()).isEqualTo(delegateSizeBefore);
        }

        @Test
        void containsWithLazyInitReturnsCorrectResultWhenDelegateModifiedBeforeFirstUse() {
            // given
            // With lazy initialization, the cache checks delegate state on first use.
            // If delegate is modified before first cache use, the cache will see that state.
            EventMessage event = EventTestUtils.createEvent(1);
            // Bypass caching queue and add directly to delegate BEFORE first cache use
            delegate.enqueue(SEQUENCE_ID_1, new GenericDeadLetter<>(SEQUENCE_ID_1, event), null).join();

            // when
            // First cache use initializes by checking amountOfSequences(), which is now 1.
            // Cache sees queue as non-empty, so it queries delegate for the sequence.
            Boolean result = cachingQueue.contains(SEQUENCE_ID_1, null).join();

            // then
            // With lazy init, cache discovers the entry that was added directly to delegate
            assertThat(result).isTrue();
            assertThat(cachingQueue.cacheEnqueuedSize()).isEqualTo(1);
        }

    }

    @Nested
    class WhenDelegateStartsNonEmpty {

        @BeforeEach
        void setUp() {
            delegate = InMemorySequencedDeadLetterQueue.defaultQueue();
            // Pre-populate delegate before creating caching queue
            EventMessage event = EventTestUtils.createEvent(1);
            delegate.enqueue(SEQUENCE_ID_1, new GenericDeadLetter<>(SEQUENCE_ID_1, event), null).join();

            cachingQueue = new CachingSequencedDeadLetterQueue<>(delegate);
        }

        @Test
        void containsMightBePresentForUnknownSequence() {
            // given
            // Queue started non-empty, so unknown sequences might be present

            // when
            Boolean result = cachingQueue.contains(SEQUENCE_ID_2, null).join();

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
            cachingQueue.contains(SEQUENCE_ID_2, null).join();
            cachingQueue.contains(SEQUENCE_ID_2, null).join();

            // then
            assertThat(cachingQueue.cacheNonEnqueuedSize()).isEqualTo(1);
        }

        @Test
        void containsReturnsTrueForExistingSequence() {
            // given
            // SEQUENCE_ID_1 was pre-populated

            // when
            Boolean result = cachingQueue.contains(SEQUENCE_ID_1, null).join();

            // then
            assertThat(result).isTrue();
            assertThat(cachingQueue.cacheEnqueuedSize()).isEqualTo(1);
        }

        @Test
        void invalidateCacheClearsNonEnqueuedCache() {
            // given
            cachingQueue.contains(SEQUENCE_ID_2, null).join();
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
            cachingQueue.contains(SEQUENCE_ID_2, null).join();
            assertThat(cachingQueue.cacheNonEnqueuedSize()).isEqualTo(1);
            EventMessage event = EventTestUtils.createEvent(2);

            // when
            var result = cachingQueue.enqueueIfPresent(
                    SEQUENCE_ID_2,
                    () -> new GenericDeadLetter<>(SEQUENCE_ID_2, event),
                    null
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
            delegate.enqueue(SEQUENCE_ID_2, new GenericDeadLetter<>(SEQUENCE_ID_2, event), null).join();
            assertThat(cachingQueue.cacheEnqueuedSize()).isZero();

            // when
            // Cache queries delegate and discovers SEQUENCE_ID_2 is present
            Boolean result = cachingQueue.contains(SEQUENCE_ID_2, null).join();

            // then
            assertThat(result).isTrue();
            assertThat(cachingQueue.cacheEnqueuedSize()).isEqualTo(1);
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
            delegate.enqueue("existing", new GenericDeadLetter<>("existing", event), null).join();

            int customMaxSize = 3;
            cachingQueue = new CachingSequencedDeadLetterQueue<>(delegate, customMaxSize);

            // when
            for (int i = 0; i < 5; i++) {
                cachingQueue.contains("unknown-" + i, null).join();
            }

            // then
            // LRU eviction should keep only customMaxSize entries
            assertThat(cachingQueue.cacheNonEnqueuedSize()).isEqualTo(customMaxSize);
        }
    }

    @Nested
    class WhenCacheIsNotYetInitialized {

        @Test
        void cacheEnqueuedSizeReturnsZeroBeforeInit() {
            // given
            delegate = InMemorySequencedDeadLetterQueue.defaultQueue();
            cachingQueue = new CachingSequencedDeadLetterQueue<>(delegate);
            // Cache is lazily initialized - no operations have been performed yet

            // when
            int size = cachingQueue.cacheEnqueuedSize();

            // then
            assertThat(size).isZero();
        }

        @Test
        void cacheNonEnqueuedSizeReturnsZeroBeforeInit() {
            // given
            delegate = InMemorySequencedDeadLetterQueue.defaultQueue();
            cachingQueue = new CachingSequencedDeadLetterQueue<>(delegate);
            // Cache is lazily initialized - no operations have been performed yet

            // when
            int size = cachingQueue.cacheNonEnqueuedSize();

            // then
            assertThat(size).isZero();
        }
    }
}
