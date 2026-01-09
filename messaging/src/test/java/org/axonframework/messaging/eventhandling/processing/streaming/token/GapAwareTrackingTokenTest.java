/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.messaging.eventhandling.processing.streaming.token;

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

        // we need more threads than available processing, for a high likelihood to trigger this concurrency issue
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
    void samePositionAs() {
        // Same index, same gaps = same position
        GapAwareTrackingToken token1 = GapAwareTrackingToken.newInstance(3L, singleton(1L));
        GapAwareTrackingToken token1Copy = GapAwareTrackingToken.newInstance(3L, singleton(1L));
        assertTrue(token1.samePositionAs(token1));
        assertTrue(token1.samePositionAs(token1Copy));

        // Same index, different gaps = same position (past gaps don't matter)
        GapAwareTrackingToken token2 = GapAwareTrackingToken.newInstance(3L, singleton(2L));
        assertTrue(token1.samePositionAs(token2));
        assertTrue(token2.samePositionAs(token1));

        // Null is never the same
        assertFalse(token1.samePositionAs(null));
        assertFalse(token2.samePositionAs(null));

        // Same index, one with gaps one without = same position
        GapAwareTrackingToken token3 = GapAwareTrackingToken.newInstance(3L, emptySortedSet());
        assertTrue(token1.samePositionAs(token3));
        assertTrue(token3.samePositionAs(token1));

        // Different index = different position
        GapAwareTrackingToken token4 = GapAwareTrackingToken.newInstance(4L, emptySortedSet());
        assertFalse(token1.samePositionAs(token4));
        assertFalse(token4.samePositionAs(token1));

        // One token's index is in the other's gaps = NOT same
        // token5 is at index 7, token6 has index 10 with gap at 7
        GapAwareTrackingToken token5 = GapAwareTrackingToken.newInstance(7L, emptySortedSet());
        GapAwareTrackingToken token6 = GapAwareTrackingToken.newInstance(10L, singleton(7L));
        assertFalse(token5.samePositionAs(token6));  // token6 skipped event 7, token5 is pointing at 7
        assertFalse(token6.samePositionAs(token5));  // different indices anyway
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
    void upperBoundRemovesGapsWhenOtherTokenFillsThem() {
        // upper(T1,T2) = 7, 8, 10+  --> token not changed
        GapAwareTrackingToken token0_0 = GapAwareTrackingToken.newInstance(9, asList(7L, 8L));
        GapAwareTrackingToken token0_1 = GapAwareTrackingToken.newInstance(0, emptyList());
        assertEquals(token0_0, token0_0.upperBound(token0_1));

        // upper(T1,T2) = 7, 8, 10+  --> token not changed
        GapAwareTrackingToken token1_0 = GapAwareTrackingToken.newInstance(9, asList(7L, 8L));
        GapAwareTrackingToken token1_1 = GapAwareTrackingToken.newInstance(6, emptyList());
        assertEquals(token1_0, token1_0.upperBound(token1_1));

        // upper(T1,T2) = 8, 10+     --> token changed
        GapAwareTrackingToken token2_0 = GapAwareTrackingToken.newInstance(9, asList(7L, 8L));
        GapAwareTrackingToken token2_1 = GapAwareTrackingToken.newInstance(7, emptyList());
        assertEquals(GapAwareTrackingToken.newInstance(9, singletonList(8L)), token2_0.upperBound(token2_1));

        // upper(T1,T2) = 10+        --> token changed
        GapAwareTrackingToken token3_0 = GapAwareTrackingToken.newInstance(9, singletonList(8L));
        GapAwareTrackingToken token3_1 = GapAwareTrackingToken.newInstance(8, emptyList());
        assertEquals(GapAwareTrackingToken.newInstance(9, emptyList()), token3_0.upperBound(token3_1));

        // upper(T1,T2) = 10+        --> token not changed
        GapAwareTrackingToken token4_0 = GapAwareTrackingToken.newInstance(9, emptyList());
        GapAwareTrackingToken token4_1 = GapAwareTrackingToken.newInstance(9, emptyList());
        assertEquals(GapAwareTrackingToken.newInstance(9, emptyList()), token4_0.upperBound(token4_1));

        // upper(T1,T2) = 11+        --> token changed
        GapAwareTrackingToken token5_0 = GapAwareTrackingToken.newInstance(9, emptyList());
        GapAwareTrackingToken token5_1 = GapAwareTrackingToken.newInstance(10, emptyList());
        assertEquals(GapAwareTrackingToken.newInstance(10, emptyList()), token5_0.upperBound(token5_1));
    }



    @Test
    void upperBoundGapsKeptIfAfterLowerIndex() {
        GapAwareTrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(5, asList(3L, 4L));
        GapAwareTrackingToken replayToken = GapAwareTrackingToken.newInstance(2, emptyList());

        assertEquals(GapAwareTrackingToken.newInstance(5, asList(3L, 4L)), tokenAtReset.upperBound(replayToken));
    }

    @Test
    void upperBoundGapsRemovesGapsThatAreAtLowerIndex() {
        GapAwareTrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(5, asList(3L, 4L));
        GapAwareTrackingToken replayToken = GapAwareTrackingToken.newInstance(3, emptyList());

        assertEquals(GapAwareTrackingToken.newInstance(5, singletonList(4L)), tokenAtReset.upperBound(replayToken));
    }

    @Test
    void upperBoundRemovesGapsAreNotAtLower() {
        GapAwareTrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(5, asList(3L, 4L));
        GapAwareTrackingToken replayToken = GapAwareTrackingToken.newInstance(4, singletonList(3L));

        assertEquals(GapAwareTrackingToken.newInstance(5, singletonList(3L)), tokenAtReset.upperBound(replayToken));
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

    /**
     * Tests demonstrating how {@code lowerBound()} can be used to detect whether a position
     * was a gap at the time of another token (reference token).
     * <p>
     * Key insight: When computing {@code lowerBound()}, if the minimum index falls on a gap,
     * the algorithm walks backwards to find the first non-gap position. This behavior
     * naturally distinguishes:
     * <ul>
     *   <li>Positions that were gaps (lowerBound walks back → different from the other token)</li>
     *   <li>Positions that were already seen (lowerBound stays → same as the other token)</li>
     * </ul>
     */
    @Nested
    class LowerBoundForGapDetection {

        /**
         * Scenario: Position 7 was a gap in the reference token.
         * <pre>
         * tokenAtReset: index=10, gaps=[7, 8]
         *   Timeline: [0-6 seen] [7 GAP] [8 GAP] [9-10 seen]
         *
         * newToken: index=7, gaps=[]
         *   Represents: position 7
         *
         * lowerBound calculation:
         *   - mergedGaps = [7, 8]
         *   - mergedIndex = min(10, 7) = 7
         *   - 7 IS in gaps → walk back to 6
         *   - Result: index=6
         *
         * lowerBound.samePositionAs(newToken)? → 6 == 7? NO
         * Therefore: Position 7 was a gap in the reference token
         * </pre>
         */
        @Test
        void lowerBoundDetectsGapFilledEvent() {
            GapAwareTrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10L, asList(7L, 8L));
            GapAwareTrackingToken newToken = GapAwareTrackingToken.newInstance(7L, emptyList());

            GapAwareTrackingToken lowerBound = tokenAtReset.lowerBound(newToken);

            // lowerBound walked back past gap 7 to index 6
            assertEquals(6L, lowerBound.getIndex());
            assertTrue(lowerBound.getGaps().isEmpty());

            // lowerBound is NOT same as newToken → position was a gap
            assertFalse(lowerBound.samePositionAs(newToken));
        }

        /**
         * Scenario: Position 5 was already seen in the reference token.
         * <pre>
         * tokenAtReset: index=10, gaps=[7, 8]
         *   Timeline: [0-6 seen] [7 GAP] [8 GAP] [9-10 seen]
         *
         * newToken: index=5, gaps=[]
         *   Represents: position 5
         *
         * lowerBound calculation:
         *   - mergedGaps = [7, 8]
         *   - mergedIndex = min(10, 5) = 5
         *   - 5 is NOT in gaps → stays 5
         *   - Result: index=5
         *
         * lowerBound.samePositionAs(newToken)? → 5 == 5? YES
         * Therefore: Position 5 was already seen in the reference token
         * </pre>
         */
        @Test
        void lowerBoundDetectsAlreadyProcessedEvent() {
            GapAwareTrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10L, asList(7L, 8L));
            GapAwareTrackingToken newToken = GapAwareTrackingToken.newInstance(5L, emptyList());

            GapAwareTrackingToken lowerBound = tokenAtReset.lowerBound(newToken);

            // lowerBound stays at index 5 (not a gap)
            assertEquals(5L, lowerBound.getIndex());

            // lowerBound IS same as newToken → position was already seen
            assertTrue(lowerBound.samePositionAs(newToken));
        }

        /**
         * Scenario: Position 8 was also a gap, consecutive with gap at 7.
         * <pre>
         * tokenAtReset: index=10, gaps=[7, 8]
         *   Timeline: [0-6 seen] [7 GAP] [8 GAP] [9-10 seen]
         *
         * newToken: index=8, gaps=[]
         *   Represents: position 8
         *
         * lowerBound calculation:
         *   - mergedGaps = [7, 8]
         *   - mergedIndex = min(10, 8) = 8
         *   - 8 IS in gaps → walk back: 7 also in gaps → walk to 6
         *   - Result: index=6
         *
         * lowerBound.samePositionAs(newToken)? → 6 == 8? NO
         * Therefore: Position 8 was a gap in the reference token
         * </pre>
         */
        @Test
        void lowerBoundDetectsConsecutiveGapFill() {
            GapAwareTrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10L, asList(7L, 8L));
            GapAwareTrackingToken newToken = GapAwareTrackingToken.newInstance(8L, emptyList());

            GapAwareTrackingToken lowerBound = tokenAtReset.lowerBound(newToken);

            // lowerBound walked back past gaps 8 and 7 to index 6
            assertEquals(6L, lowerBound.getIndex());

            // lowerBound is NOT same as newToken → position was a gap
            assertFalse(lowerBound.samePositionAs(newToken));
        }

        /**
         * Scenario: Position 6 (boundary case - just before the gap).
         * <pre>
         * tokenAtReset: index=10, gaps=[7, 8]
         *   Timeline: [0-6 seen] [7 GAP] [8 GAP] [9-10 seen]
         *
         * newToken: index=6, gaps=[]
         *   Represents: position 6
         *
         * lowerBound calculation:
         *   - mergedGaps = [7, 8]
         *   - mergedIndex = min(10, 6) = 6
         *   - 6 is NOT in gaps → stays 6
         *   - Result: index=6
         *
         * lowerBound.samePositionAs(newToken)? → 6 == 6? YES
         * Therefore: Position 6 was already seen in the reference token
         * </pre>
         */
        @Test
        void lowerBoundDetectsBoundaryBeforeGap() {
            GapAwareTrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10L, asList(7L, 8L));
            GapAwareTrackingToken newToken = GapAwareTrackingToken.newInstance(6L, emptyList());

            GapAwareTrackingToken lowerBound = tokenAtReset.lowerBound(newToken);

            // lowerBound stays at index 6
            assertEquals(6L, lowerBound.getIndex());

            // lowerBound IS same as newToken → position was already seen
            assertTrue(lowerBound.samePositionAs(newToken));
        }

        /**
         * Scenario: Position 9 (boundary case - just after the gaps).
         * <pre>
         * tokenAtReset: index=10, gaps=[7, 8]
         *   Timeline: [0-6 seen] [7 GAP] [8 GAP] [9-10 seen]
         *
         * newToken: index=9, gaps=[]
         *   Represents: position 9
         *
         * lowerBound calculation:
         *   - mergedGaps = [7, 8]
         *   - mergedIndex = min(10, 9) = 9
         *   - 9 is NOT in gaps → stays 9
         *   - Result: index=9, gaps=[7, 8] (gaps below 9 kept)
         *
         * lowerBound.samePositionAs(newToken)? → 9 == 9? YES
         * Therefore: Position 9 was already seen in the reference token
         * </pre>
         */
        @Test
        void lowerBoundDetectsBoundaryAfterGap() {
            GapAwareTrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10L, asList(7L, 8L));
            GapAwareTrackingToken newToken = GapAwareTrackingToken.newInstance(9L, emptyList());

            GapAwareTrackingToken lowerBound = tokenAtReset.lowerBound(newToken);

            // lowerBound stays at index 9
            assertEquals(9L, lowerBound.getIndex());
            // Note: gaps 7, 8 are retained since they're below the merged index
            assertEquals(asList(7L, 8L), new ArrayList<>(lowerBound.getGaps()));

            // lowerBound IS same as newToken → position was already seen
            assertTrue(lowerBound.samePositionAs(newToken));
        }

        /**
         * Scenario: Gap at the very beginning (position 0).
         * <pre>
         * tokenAtReset: index=5, gaps=[0, 1]
         *   Timeline: [0 GAP] [1 GAP] [2-5 seen]
         *
         * newToken: index=0, gaps=[]
         *   Represents: position 0
         *
         * lowerBound calculation:
         *   - mergedGaps = [0, 1]
         *   - mergedIndex = min(5, 0) = 0
         *   - 0 IS in gaps → walk back to -1
         *   - Result: index=-1
         *
         * lowerBound.samePositionAs(newToken)? → -1 == 0? NO
         * Therefore: Position 0 was a gap in the reference token
         * </pre>
         */
        @Test
        void lowerBoundDetectsGapAtStart() {
            GapAwareTrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(5L, asList(0L, 1L));
            GapAwareTrackingToken newToken = GapAwareTrackingToken.newInstance(0L, emptyList());

            GapAwareTrackingToken lowerBound = tokenAtReset.lowerBound(newToken);

            // lowerBound walked back past gap 0 to index -1
            assertEquals(-1L, lowerBound.getIndex());

            // lowerBound is NOT same as newToken → position was a gap
            assertFalse(lowerBound.samePositionAs(newToken));
        }

        /**
         * Scenario: Multiple scattered gaps.
         * <pre>
         * tokenAtReset: index=15, gaps=[3, 7, 8, 12]
         *   Timeline: [0-2 seen] [3 GAP] [4-6 seen] [7 GAP] [8 GAP] [9-11 seen] [12 GAP] [13-15 seen]
         *
         * Various positions tested:
         *   - Position 3: was a gap
         *   - Position 5: was seen
         *   - Position 12: was a gap
         *   - Position 13: was seen
         * </pre>
         */
        @Test
        void lowerBoundWithMultipleScatteredGaps() {
            GapAwareTrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(15L, asList(3L, 7L, 8L, 12L));

            // Position 3 was a gap
            GapAwareTrackingToken event3 = GapAwareTrackingToken.newInstance(3L, emptyList());
            GapAwareTrackingToken lowerBound3 = tokenAtReset.lowerBound(event3);
            assertEquals(2L, lowerBound3.getIndex()); // walked back past gap 3
            assertFalse(lowerBound3.samePositionAs(event3));

            // Position 5 was seen
            GapAwareTrackingToken event5 = GapAwareTrackingToken.newInstance(5L, emptyList());
            GapAwareTrackingToken lowerBound5 = tokenAtReset.lowerBound(event5);
            assertEquals(5L, lowerBound5.getIndex()); // stays at 5
            assertTrue(lowerBound5.samePositionAs(event5));

            // Position 12 was a gap
            GapAwareTrackingToken event12 = GapAwareTrackingToken.newInstance(12L, emptyList());
            GapAwareTrackingToken lowerBound12 = tokenAtReset.lowerBound(event12);
            assertEquals(11L, lowerBound12.getIndex()); // walked back past gap 12
            assertFalse(lowerBound12.samePositionAs(event12));

            // Position 13 was seen
            GapAwareTrackingToken event13 = GapAwareTrackingToken.newInstance(13L, emptyList());
            GapAwareTrackingToken lowerBound13 = tokenAtReset.lowerBound(event13);
            assertEquals(13L, lowerBound13.getIndex()); // stays at 13
            assertTrue(lowerBound13.samePositionAs(event13));
        }
    }
}
