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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SequenceIdentifierCache}.
 *
 * @author Mateusz Nowak
 */
class SequenceIdentifierCacheTest {

    private static final String SEQUENCE_ID_1 = "sequence-1";
    private static final String SEQUENCE_ID_2 = "sequence-2";
    private static final String SEQUENCE_ID_3 = "sequence-3";

    @Nested
    class WhenStartedEmpty {

        private SequenceIdentifierCache cache;

        @BeforeEach
        void setUp() {
            cache = new SequenceIdentifierCache();
        }

        @Test
        void unknownIdentifierIsNotPresent() {
            // given
            // cache is empty

            // when
            boolean result = cache.mightBePresent(SEQUENCE_ID_1);

            // then
            assertThat(result).isFalse();
        }

        @Test
        void enqueuedIdentifierIsPresent() {
            // given
            cache.markEnqueued(SEQUENCE_ID_1);

            // when
            boolean result = cache.mightBePresent(SEQUENCE_ID_1);

            // then
            assertThat(result).isTrue();
        }

        @Test
        void notEnqueuedIdentifierIsNotPresent() {
            // given
            cache.markNotEnqueued(SEQUENCE_ID_1);

            // when
            boolean result = cache.mightBePresent(SEQUENCE_ID_1);

            // then
            assertThat(result).isFalse();
        }

        @Test
        void markEnqueuedThenNotEnqueuedMakesIdentifierNotPresent() {
            // given
            cache.markEnqueued(SEQUENCE_ID_1);

            // when
            cache.markNotEnqueued(SEQUENCE_ID_1);

            // then
            assertThat(cache.mightBePresent(SEQUENCE_ID_1)).isFalse();
        }

        @Test
        void markNotEnqueuedThenEnqueuedMakesIdentifierPresent() {
            // given
            cache.markNotEnqueued(SEQUENCE_ID_1);

            // when
            cache.markEnqueued(SEQUENCE_ID_1);

            // then
            assertThat(cache.mightBePresent(SEQUENCE_ID_1)).isTrue();
        }

        @Test
        void clearRemovesAllEnqueuedIdentifiers() {
            // given
            cache.markEnqueued(SEQUENCE_ID_1);
            cache.markEnqueued(SEQUENCE_ID_2);

            // when
            cache.clear();

            // then
            assertThat(cache.mightBePresent(SEQUENCE_ID_1)).isFalse();
            assertThat(cache.mightBePresent(SEQUENCE_ID_2)).isFalse();
            assertThat(cache.enqueuedSize()).isZero();
        }

        @Test
        void enqueuedSizeReturnsCorrectCount() {
            // given
            cache.markEnqueued(SEQUENCE_ID_1);
            cache.markEnqueued(SEQUENCE_ID_2);

            // when
            int size = cache.enqueuedSize();

            // then
            assertThat(size).isEqualTo(2);
        }

        @Test
        void nonEnqueuedSizeRemainsZero() {
            // given
            // when started empty, non-enqueued identifiers are not tracked
            cache.markNotEnqueued(SEQUENCE_ID_1);

            // when
            int size = cache.nonEnqueuedSize();

            // then
            assertThat(size).isZero();
        }
    }

    @Nested
    class WhenStartedNonEmpty {

        private SequenceIdentifierCache cache;

        @BeforeEach
        void setUp() {
            cache = new SequenceIdentifierCache(false);
        }

        @Test
        void unknownIdentifierMightBePresent() {
            // given
            // cache is empty but DLQ started non-empty

            // when
            boolean result = cache.mightBePresent(SEQUENCE_ID_1);

            // then
            assertThat(result).isTrue();
        }

        @Test
        void enqueuedIdentifierIsPresent() {
            // given
            cache.markEnqueued(SEQUENCE_ID_1);

            // when
            boolean result = cache.mightBePresent(SEQUENCE_ID_1);

            // then
            assertThat(result).isTrue();
        }

        @Test
        void notEnqueuedIdentifierIsNotPresent() {
            // given
            cache.markNotEnqueued(SEQUENCE_ID_1);

            // when
            boolean result = cache.mightBePresent(SEQUENCE_ID_1);

            // then
            assertThat(result).isFalse();
        }

        @Test
        void markEnqueuedThenNotEnqueuedMakesIdentifierNotPresent() {
            // given
            cache.markEnqueued(SEQUENCE_ID_1);

            // when
            cache.markNotEnqueued(SEQUENCE_ID_1);

            // then
            assertThat(cache.mightBePresent(SEQUENCE_ID_1)).isFalse();
        }

        @Test
        void markNotEnqueuedThenEnqueuedMakesIdentifierPresent() {
            // given
            cache.markNotEnqueued(SEQUENCE_ID_1);

            // when
            cache.markEnqueued(SEQUENCE_ID_1);

            // then
            assertThat(cache.mightBePresent(SEQUENCE_ID_1)).isTrue();
        }

        @Test
        void markEnqueuedRemovesFromNonEnqueued() {
            // given
            cache.markNotEnqueued(SEQUENCE_ID_1);
            assertThat(cache.nonEnqueuedSize()).isEqualTo(1);

            // when
            cache.markEnqueued(SEQUENCE_ID_1);

            // then
            assertThat(cache.nonEnqueuedSize()).isZero();
            assertThat(cache.enqueuedSize()).isEqualTo(1);
        }

        @Test
        void clearRemovesAllIdentifiers() {
            // given
            cache.markEnqueued(SEQUENCE_ID_1);
            cache.markNotEnqueued(SEQUENCE_ID_2);

            // when
            cache.clear();

            // then
            assertThat(cache.enqueuedSize()).isZero();
            assertThat(cache.nonEnqueuedSize()).isZero();
            // After clear, unknown identifiers might be present again
            assertThat(cache.mightBePresent(SEQUENCE_ID_1)).isTrue();
            assertThat(cache.mightBePresent(SEQUENCE_ID_2)).isTrue();
        }

        @Test
        void nonEnqueuedSizeReturnsCorrectCount() {
            // given
            cache.markNotEnqueued(SEQUENCE_ID_1);
            cache.markNotEnqueued(SEQUENCE_ID_2);

            // when
            int size = cache.nonEnqueuedSize();

            // then
            assertThat(size).isEqualTo(2);
        }
    }

    @Nested
    class WhenLruEvictionOccurs {

        @Test
        void evictsOldestNonEnqueuedIdentifierWhenMaxSizeExceeded() {
            // given
            int maxSize = 3;
            SequenceIdentifierCache cache = new SequenceIdentifierCache(false, maxSize);

            cache.markNotEnqueued("id-1");
            cache.markNotEnqueued("id-2");
            cache.markNotEnqueued("id-3");
            assertThat(cache.nonEnqueuedSize()).isEqualTo(3);

            // when
            cache.markNotEnqueued("id-4");

            // then
            assertThat(cache.nonEnqueuedSize()).isEqualTo(3);
            // "id-1" was evicted (oldest), so it might be present now
            assertThat(cache.mightBePresent("id-1")).isTrue();
            // Others are still cached as not present
            assertThat(cache.mightBePresent("id-2")).isFalse();
            assertThat(cache.mightBePresent("id-3")).isFalse();
            assertThat(cache.mightBePresent("id-4")).isFalse();
        }

        @Test
        void evictsInFifoOrder() {
            // given
            int maxSize = 2;
            SequenceIdentifierCache cache = new SequenceIdentifierCache(false, maxSize);

            // when
            cache.markNotEnqueued("first");
            cache.markNotEnqueued("second");
            cache.markNotEnqueued("third");
            cache.markNotEnqueued("fourth");

            // then
            assertThat(cache.nonEnqueuedSize()).isEqualTo(2);
            // "first" and "second" were evicted
            assertThat(cache.mightBePresent("first")).isTrue();
            assertThat(cache.mightBePresent("second")).isTrue();
            // "third" and "fourth" are still cached
            assertThat(cache.mightBePresent("third")).isFalse();
            assertThat(cache.mightBePresent("fourth")).isFalse();
        }
    }

    @Nested
    class WhenMethodChaining {

        @Test
        void markEnqueuedReturnsInstance() {
            // given
            SequenceIdentifierCache cache = new SequenceIdentifierCache();

            // when
            SequenceIdentifierCache result = cache.markEnqueued(SEQUENCE_ID_1);

            // then
            assertThat(result).isSameAs(cache);
        }

        @Test
        void markNotEnqueuedReturnsInstance() {
            // given
            SequenceIdentifierCache cache = new SequenceIdentifierCache();

            // when
            SequenceIdentifierCache result = cache.markNotEnqueued(SEQUENCE_ID_1);

            // then
            assertThat(result).isSameAs(cache);
        }

        @Test
        void clearReturnsInstance() {
            // given
            SequenceIdentifierCache cache = new SequenceIdentifierCache();

            // when
            SequenceIdentifierCache result = cache.clear();

            // then
            assertThat(result).isSameAs(cache);
        }

        @Test
        void chainingWorksCorrectly() {
            // given
            SequenceIdentifierCache cache = new SequenceIdentifierCache(false);

            // when
            cache.markEnqueued(SEQUENCE_ID_1)
                 .markNotEnqueued(SEQUENCE_ID_2)
                 .markEnqueued(SEQUENCE_ID_3);

            // then
            assertThat(cache.mightBePresent(SEQUENCE_ID_1)).isTrue();
            assertThat(cache.mightBePresent(SEQUENCE_ID_2)).isFalse();
            assertThat(cache.mightBePresent(SEQUENCE_ID_3)).isTrue();
        }
    }

    @Nested
    class WhenUsingDefaultMaxSize {

        @Test
        void defaultMaxSizeIsApplied() {
            // given
            SequenceIdentifierCache cache = new SequenceIdentifierCache(false);

            // when
            for (int i = 0; i < SequenceIdentifierCache.DEFAULT_MAX_SIZE + 10; i++) {
                cache.markNotEnqueued("id-" + i);
            }

            // then
            assertThat(cache.nonEnqueuedSize()).isEqualTo(SequenceIdentifierCache.DEFAULT_MAX_SIZE);
        }
    }
}
