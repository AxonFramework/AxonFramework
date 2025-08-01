/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.eventhandling;

import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static org.junit.jupiter.api.Assertions.*;

class GapAwareTrackingTokenTest {

    @Test
    void gapAwareTokenConcurrency() throws InterruptedException {
        AtomicLong counter = new AtomicLong();
        AtomicReference<GapAwareTrackingToken> currentToken = new AtomicReference<>(GapAwareTrackingToken.newInstance(-1,
                                                                                                                      emptySortedSet()));

        ExecutorService executorService = Executors.newCachedThreadPool();

        // we need more threads than available processors, for a high likelihood to trigger this concurrency issue
        int threadCount = Runtime.getRuntime().availableProcessors() + 1;
        Future<?>[] results = new Future<?>[threadCount];
        for (int i = 0; i < threadCount; i++) {
            results[i] = executorService.submit(() -> {
                long deadline = System.currentTimeMillis() + 1000;
                while (System.currentTimeMillis() < deadline) {
                    long next = counter.getAndIncrement();
                    currentToken.getAndUpdate(t -> t.advanceTo(next, Integer.MAX_VALUE));
                }
            });
        }
        executorService.shutdown();
        assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS),
                   "ExecutorService not stopped within expected reasonable time frame");

        for (Future<?> result : results) {
            assertDoesNotThrow(() -> result.get(1, TimeUnit.SECONDS));
        }

        assertTrue(counter.get() > 0, "The test did not seem to have generated any tokens");
        assertEquals(counter.get() - 1, currentToken.get().getIndex());
        assertEquals(emptySortedSet(), currentToken.get().getGaps());
    }

    @Test
    void whenAdvancingToPositionGapsAheadOfThatPositionAreNeverCleanedUp() {
        List<Long> gaps = asList(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);
        GapAwareTrackingToken token = GapAwareTrackingToken.newInstance(100, gaps);

        GapAwareTrackingToken actual = token.advanceTo(2L, 0);
        assertTrue(actual.hasGaps());
        assertIterableEquals(Arrays.asList(3L, 4L, 5L, 6L, 7L, 8L, 9L), actual.getGaps());
        assertEquals(100, actual.getIndex());
    }

    @Test
    void gapAwareTokenConcurrency_HighConcurrency() throws InterruptedException {
        long counter = 0;
        AtomicReference<GapAwareTrackingToken> currentToken = new AtomicReference<>(GapAwareTrackingToken.newInstance(-1,
                                                                                                                      emptySortedSet()));

        ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        Future<?>[] results = new Future<?>[1_000_000];
        for (int i = 0; i < 1_000_000; i++) {
            long next = counter++;
            results[i] = executorService.submit(() -> {
                currentToken.getAndUpdate(t -> t.advanceTo(next, Integer.MAX_VALUE));
            });
        }
        executorService.shutdown();
        assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS),
                   "ExecutorService not stopped within expected reasonable time frame");

        for (Future<?> result : results) {
            assertDoesNotThrow(() -> result.get(1, TimeUnit.SECONDS));
        }

        assertTrue(counter > 0, "The test did not seem to have generated any tokens");
        assertEquals(counter - 1, currentToken.get().getIndex());
        assertEquals(emptySortedSet(), currentToken.get().getGaps());
    }

    @Test
    void initializationWithNullGaps() {
        GapAwareTrackingToken token = new GapAwareTrackingToken(10, null);
        assertNotNull(token.getGaps());
        assertEquals(0, token.getGaps().size());
    }

    @Test
    void advanceToWithoutGaps() {
        GapAwareTrackingToken subject = GapAwareTrackingToken.newInstance(0L, Collections.emptyList());
        subject = subject.advanceTo(1L, 10);
        assertEquals(1L, subject.getIndex());
        assertEquals(emptySortedSet(), subject.getGaps());
    }

    @Test
    void advanceToWithInitialGaps() {
        GapAwareTrackingToken subject = GapAwareTrackingToken.newInstance(10L, asList(1L, 5L, 6L));
        subject = subject.advanceTo(5L, 10);
        assertEquals(10L, subject.getIndex());
        assertEquals(Stream.of(1L, 6L).collect(Collectors.toCollection(TreeSet::new)), subject.getGaps());
    }

    @Test
    void advanceToWithNewGaps() {
        GapAwareTrackingToken subject = GapAwareTrackingToken.newInstance(10L, Collections.emptyList());
        subject = subject.advanceTo(13L, 10);
        assertEquals(13L, subject.getIndex());
        assertEquals(Stream.of(11L, 12L).collect(Collectors.toCollection(TreeSet::new)), subject.getGaps());
    }

    @Test
    void advanceToGapClearsOldGaps() {
        GapAwareTrackingToken subject = GapAwareTrackingToken.newInstance(15L, asList(1L, 5L, 12L));
        subject = subject.advanceTo(12L, 10);
        assertEquals(15L, subject.getIndex());
        assertEquals(Stream.of(5L).collect(Collectors.toCollection(TreeSet::new)), subject.getGaps());
    }

    @Test
    void advanceToOldGapClearsOldGaps() {
        GapAwareTrackingToken subject = GapAwareTrackingToken.newInstance(15L, asList(1L, 5L, 12L));
        subject = subject.advanceTo(1L, 10);
        assertEquals(15L, subject.getIndex());
        assertEquals(Stream.of(5L, 12L).collect(Collectors.toCollection(TreeSet::new)), subject.getGaps());
    }

    @Test
    void advanceToHigherSequenceClearsOldGaps() {
        GapAwareTrackingToken subject = GapAwareTrackingToken.newInstance(15L, asList(1L, 5L, 12L));
        subject = subject.advanceTo(16L, 10);
        assertEquals(16L, subject.getIndex());
        assertEquals(Stream.of(12L).collect(Collectors.toCollection(TreeSet::new)), subject.getGaps());
    }

    @Test
    void advanceToLowerSequenceThatIsNotAGapNotAllowed() {
        GapAwareTrackingToken subject = GapAwareTrackingToken.newInstance(15L, asList(1L, 5L, 12L));
        assertThrows(IllegalArgumentException.class, () -> subject.advanceTo(4L, 10));
    }

    @Test
    void newInstanceWithGapHigherThanSequenceNotAllowed() {
        assertThrows(IllegalArgumentException.class, () -> GapAwareTrackingToken.newInstance(9L, asList(1L, 5L, 12L)));
    }

    @Test
    void tokenCoversOther() {
        GapAwareTrackingToken token1 = GapAwareTrackingToken.newInstance(3L, singleton(1L));
        GapAwareTrackingToken token2 = GapAwareTrackingToken.newInstance(4L, singleton(2L));
        GapAwareTrackingToken token3 = GapAwareTrackingToken.newInstance(2L, asList(0L, 1L));
        GapAwareTrackingToken token4 = GapAwareTrackingToken.newInstance(3L, emptySortedSet());
        GapAwareTrackingToken token5 = GapAwareTrackingToken.newInstance(3L, singleton(2L));
        GapAwareTrackingToken token6 = GapAwareTrackingToken.newInstance(1L, emptySortedSet());
        GapAwareTrackingToken token7 = GapAwareTrackingToken.newInstance(2L, singleton(1L));

        assertFalse(token1.covers(token2));
        assertFalse(token2.covers(token1));
        assertTrue(token1.covers(token3));
        assertFalse(token3.covers(token1));
        assertTrue(token4.covers(token1));
        assertFalse(token1.covers(token4));
        assertFalse(token2.covers(token4));
        assertFalse(token4.covers(token2));
        assertTrue(token4.covers(token5));
        assertFalse(token5.covers(token4));
        assertFalse(token1.covers(token5));
        assertFalse(token5.covers(token1));
        assertFalse(token1.covers(token6));
        assertFalse(token6.covers(token1));

        assertFalse(token3.covers(token7));
    }

    @Test
    void occurrenceOfInconsistentRangeException() {
        // verifies issue 655 (https://github.com/AxonFramework/AxonFramework/issues/655)
        GapAwareTrackingToken.newInstance(10L, asList(0L, 1L, 2L, 8L, 9L))
                             .advanceTo(0L, 5)
                             .covers(GapAwareTrackingToken.newInstance(0L, emptySet()));
    }

    @Test
    void lowerBound() {
        GapAwareTrackingToken token1 = GapAwareTrackingToken.newInstance(3L, singleton(1L));
        GapAwareTrackingToken token2 = GapAwareTrackingToken.newInstance(4L, singleton(2L));
        GapAwareTrackingToken token3 = GapAwareTrackingToken.newInstance(2L, asList(0L, 1L));
        GapAwareTrackingToken token4 = GapAwareTrackingToken.newInstance(3L, emptySortedSet());
        GapAwareTrackingToken token5 = GapAwareTrackingToken.newInstance(3L, singleton(2L));
        GapAwareTrackingToken token6 = GapAwareTrackingToken.newInstance(1L, emptySortedSet());

        assertEquals(token1.lowerBound(token2), GapAwareTrackingToken.newInstance(3L, asList(1L, 2L)));
        assertEquals(token1.lowerBound(token3), token3);
        assertEquals(token1.lowerBound(token4), token1);
        assertEquals(token1.lowerBound(token5), GapAwareTrackingToken.newInstance(3L, asList(1L, 2L)));
        assertEquals(token1.lowerBound(token6), GapAwareTrackingToken.newInstance(0L, emptySortedSet()));
        assertEquals(token2.lowerBound(token3), GapAwareTrackingToken.newInstance(-1L, emptySortedSet()));
    }

    @Test
    void upperBound() {
        GapAwareTrackingToken token0 = GapAwareTrackingToken.newInstance(9, emptyList());
        GapAwareTrackingToken token1 = GapAwareTrackingToken.newInstance(10, singleton(9L));
        GapAwareTrackingToken token2 = GapAwareTrackingToken.newInstance(10, asList(9L, 8L));
        GapAwareTrackingToken token3 = GapAwareTrackingToken.newInstance(15, singletonList(14L));
        GapAwareTrackingToken token4 = GapAwareTrackingToken.newInstance(15, asList(14L, 9L, 8L));
        GapAwareTrackingToken token5 = GapAwareTrackingToken.newInstance(14, emptyList());

        assertEquals(GapAwareTrackingToken.newInstance(10, emptyList()), token0.upperBound(token1));
        assertEquals(token1, token1.upperBound(token2));
        assertEquals(token3, token1.upperBound(token3));
        assertEquals(GapAwareTrackingToken.newInstance(15, asList(14L, 9L)), token1.upperBound(token4));
        assertEquals(GapAwareTrackingToken.newInstance(15, asList(14L, 9L, 8L)), token2.upperBound(token4));
        assertEquals(GapAwareTrackingToken.newInstance(15, emptyList()), token5.upperBound(token3));
    }

    @Test
    void gapTruncationRetainsEquality() {
        GapAwareTrackingToken token1 = GapAwareTrackingToken.newInstance(15, asList(14L, 9L, 8L));

        assertEquals(token1, token1.withGapsTruncatedAt(8L));
        assertEquals(token1.withGapsTruncatedAt(9L), token1);
        assertEquals(token1.withGapsTruncatedAt(9L), token1.withGapsTruncatedAt(8L));
        assertEquals(token1.withGapsTruncatedAt(16L), token1.withGapsTruncatedAt(16L));
    }

    @Test
    void truncateGaps() {
        GapAwareTrackingToken token1 = GapAwareTrackingToken.newInstance(15, asList(14L, 9L, 8L));

        assertSame(token1, token1.withGapsTruncatedAt(7));


        assertEquals(asTreeSet(9L, 14L), token1.withGapsTruncatedAt(9L).getGaps());
        assertEquals(asTreeSet(14L), token1.withGapsTruncatedAt(10L).getGaps());
        assertEquals(emptySet(), token1.withGapsTruncatedAt(15L).getGaps());
    }

    @Test
    void gapTruncatedTokenCoveredByOriginal() {
        GapAwareTrackingToken token1 = GapAwareTrackingToken.newInstance(15, asList(14L, 9L, 8L));

        assertTrue(token1.covers(token1.withGapsTruncatedAt(10)));
        assertTrue(token1.withGapsTruncatedAt(10).covers(token1));
    }

    @Test
    void position() {
        GapAwareTrackingToken token = GapAwareTrackingToken.newInstance(15, asList(14L, 9L, 8L));

        assertEquals(15L, token.position().getAsLong());
    }

    @Test
    void noMemoryLeakWhenJumpingLargeGaps() {
        GapAwareTrackingToken token = GapAwareTrackingToken.newInstance(0, emptyList());
        GapAwareTrackingToken advancedToken = token.advanceTo(Long.MAX_VALUE, 1_234);
        assertEquals(1_234, advancedToken.getGaps().size());
    }

    private TreeSet<Long> asTreeSet(Long... elements) {
        return new TreeSet<>(asList(elements));
    }
}
