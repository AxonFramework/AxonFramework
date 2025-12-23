/*
 * Copyright (c) 2010-2021. Axon Framework
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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class validating the {@link ReplayToken}.
 *
 * @author Allard Buijze
 */
class ReplayTokenTest {

    private TrackingToken innerToken;

    @BeforeEach
    void setUp() {
        innerToken = GapAwareTrackingToken.newInstance(10, Collections.singleton(9L));
    }

    @Test
    void advanceReplayTokenWithinReplaySegment() {
        ReplayToken testSubject = new ReplayToken(innerToken);
        TrackingToken actual = testSubject.advancedTo(GapAwareTrackingToken.newInstance(8, emptySet()));
        assertTrue(actual instanceof ReplayToken);
        assertTrue(ReplayToken.isReplay(actual));
    }

    @Test
    void regularTokenIsProvidedWhenResetBeyondCurrentPosition() {
        TrackingToken token1 = new GlobalSequenceTrackingToken(1);
        TrackingToken token2 = new GlobalSequenceTrackingToken(2);

        TrackingToken actual = ReplayToken.createReplayToken(token1, token2);
        assertSame(token2, actual);
    }

    @Test
    void replayTokenIsProvidedWhenResetAtCurrentPosition() {
        TrackingToken token1 = new GlobalSequenceTrackingToken(2);
        TrackingToken token2 = new GlobalSequenceTrackingToken(2);

        TrackingToken actual = ReplayToken.createReplayToken(token1, token2);
        assertInstanceOf(ReplayToken.class, actual);
        assertTrue(ReplayToken.isReplay(actual));
    }

    @Test
    void serializationDeserialization() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        ReplayToken replayToken = new ReplayToken(innerToken);
        String serializedReplayToken = objectMapper.writer().writeValueAsString(replayToken);
        ReplayToken deserializedReplayToken = objectMapper.readerFor(ReplayToken.class)
                .readValue(serializedReplayToken);
        assertEquals(replayToken, deserializedReplayToken);
    }

    @Test
    void position() {
        GapAwareTrackingToken startPosition = GapAwareTrackingToken.newInstance(11L, Collections.singleton(9L));

        TrackingToken replayToken = ReplayToken.createReplayToken(innerToken, startPosition);

        assertTrue(replayToken.position().isPresent());
        assertEquals(11L, replayToken.position().getAsLong());
    }

    @Test
    void positionIsZeroAtReset() {
        TrackingToken replayToken = ReplayToken.createReplayToken(innerToken);
        assertEquals(0, replayToken.position().orElse(-1));
    }

    @Test
    void getTokenAtReset() {
        ReplayToken testSubject = (ReplayToken) ReplayToken.createReplayToken(innerToken);
        TrackingToken actual = testSubject.advancedTo(GapAwareTrackingToken.newInstance(6, emptySet()));
        assertTrue(actual instanceof ReplayToken);
        assertEquals(testSubject.getTokenAtReset(), innerToken);
    }

    @Test
    void createReplayTokenReturnsStartPositionIfTokenAtResetIsNull() {
        TrackingToken tokenAtReset = null;
        TrackingToken startPosition = new GlobalSequenceTrackingToken(1);

        //noinspection ConstantConditions
        TrackingToken result = ReplayToken.createReplayToken(tokenAtReset, startPosition);

        assertEquals(startPosition, result);
    }

    @Test
    void createReplayTokenReturnsStartPositionIfStartPositionCoversTokenAtReset() {
        TrackingToken tokenAtReset = new GlobalSequenceTrackingToken(1);
        TrackingToken startPosition = new GlobalSequenceTrackingToken(2);

        TrackingToken result = ReplayToken.createReplayToken(tokenAtReset, startPosition);

        assertEquals(startPosition, result);
    }

    @Test
    void createReplayTokenReturnsWrappedReplayTokenIfTokenAtResetIsReplayToken() {
        TrackingToken tokenAtReset = ReplayToken.createReplayToken(new GlobalSequenceTrackingToken(1));
        TrackingToken startPosition = new GlobalSequenceTrackingToken(2);

        TrackingToken result = ReplayToken.createReplayToken(tokenAtReset, startPosition);

        assertEquals(startPosition, result);
    }

    @Test
    void createReplayTokenReturnsReplayToken() {
        TrackingToken tokenAtReset = new GlobalSequenceTrackingToken(2);
        TrackingToken startPosition = new GlobalSequenceTrackingToken(1);

        TrackingToken result = ReplayToken.createReplayToken(tokenAtReset, startPosition);

        assertTrue(result instanceof ReplayToken);
        assertEquals(tokenAtReset, ((ReplayToken) result).getTokenAtReset());
        assertEquals(startPosition, ((ReplayToken) result).getCurrentToken());
    }

    /**
     * Tests for {@link ReplayToken#advancedTo(TrackingToken)} with {@link GlobalSequenceTrackingToken}.
     * These tests verify replay detection behavior with simple sequential tokens.
     */
    @Nested
    class AdvancedToWithGlobalSequenceTrackingToken {

        @Test
        void eventBeforeResetPositionIsReplay() {
            // tokenAtReset at index 7 - processor saw events 0-7 before reset
            TrackingToken tokenAtReset = new GlobalSequenceTrackingToken(7);
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            // Event at index 5 - clearly before reset position
            TrackingToken newToken = new GlobalSequenceTrackingToken(5);
            TrackingToken result = replayToken.advancedTo(newToken);

            assertInstanceOf(ReplayToken.class, result);
            assertTrue(ReplayToken.isReplay(result), "Event before reset position should be a replay");
        }

        @Test
        void eventAtExactResetPositionIsReplay() {
            // tokenAtReset at index 7
            TrackingToken tokenAtReset = new GlobalSequenceTrackingToken(7);
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            // Advance to position 6 first
            replayToken = (ReplayToken) replayToken.advancedTo(new GlobalSequenceTrackingToken(6));

            // Event at index 7 - exactly at reset position (was already processed before reset)
            TrackingToken newToken = new GlobalSequenceTrackingToken(7);
            TrackingToken result = replayToken.advancedTo(newToken);

            assertInstanceOf(ReplayToken.class, result);
            assertTrue(ReplayToken.isReplay(result), "Event at exact reset position should be a replay");
        }

        @Test
        void eventAfterResetPositionEndsReplay() {
            // tokenAtReset at index 7
            TrackingToken tokenAtReset = new GlobalSequenceTrackingToken(7);
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            // Event at index 8 - after reset position (new event, never seen before)
            TrackingToken newToken = new GlobalSequenceTrackingToken(8);
            TrackingToken result = replayToken.advancedTo(newToken);

            // Should exit replay mode entirely
            assertInstanceOf(GlobalSequenceTrackingToken.class, result);
            assertFalse(ReplayToken.isReplay(result), "Event after reset position should not be a replay");
        }

        @Test
        void replayProgressesThroughMultipleEvents() {
            TrackingToken tokenAtReset = new GlobalSequenceTrackingToken(5);
            TrackingToken currentToken = ReplayToken.createReplayToken(tokenAtReset, null);

            // Process events 0 through 5 - all should be replays
            for (int i = 0; i <= 5; i++) {
                currentToken = ((ReplayToken) currentToken).advancedTo(new GlobalSequenceTrackingToken(i));
                assertInstanceOf(ReplayToken.class, currentToken);
                assertTrue(ReplayToken.isReplay(currentToken), "Event " + i + " should be a replay");
            }

            // Event 6 should end the replay
            currentToken = ((ReplayToken) currentToken).advancedTo(new GlobalSequenceTrackingToken(6));
            assertFalse(ReplayToken.isReplay(currentToken), "Event 6 should not be a replay");
        }
    }

    /**
     * Tests for {@link ReplayToken#advancedTo(TrackingToken)} with {@link GapAwareTrackingToken}.
     * These tests verify replay detection behavior when gaps are involved.
     */
    @Nested
    class AdvancedToWithGapAwareTrackingToken {

        @Test
        void eventBeforeResetPositionWithoutGapsIsReplay() {
            // tokenAtReset at index 10, no gaps - processor saw events 0-10 before reset
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, emptySet());
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            // Event at index 5 - clearly before reset position
            TrackingToken newToken = GapAwareTrackingToken.newInstance(5, emptySet());
            TrackingToken result = replayToken.advancedTo(newToken);

            assertInstanceOf(ReplayToken.class, result);
            assertTrue(ReplayToken.isReplay(result), "Event before reset position should be a replay");
        }

        @Test
        void eventAtResetPositionWithoutGapsIsReplay() {
            // tokenAtReset at index 10, no gaps
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, emptySet());
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            // Advance to position 9
            replayToken = (ReplayToken) replayToken.advancedTo(GapAwareTrackingToken.newInstance(9, emptySet()));

            // Event at index 10 - exactly at reset position
            TrackingToken newToken = GapAwareTrackingToken.newInstance(10, emptySet());
            TrackingToken result = replayToken.advancedTo(newToken);

            assertInstanceOf(ReplayToken.class, result);
            assertTrue(ReplayToken.isReplay(result), "Event at exact reset position should be a replay");
        }

        @Test
        void eventAfterResetPositionWithoutGapsEndsReplay() {
            // tokenAtReset at index 10, no gaps
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, emptySet());
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            // Event at index 11 - after reset position
            TrackingToken newToken = GapAwareTrackingToken.newInstance(11, emptySet());
            TrackingToken result = replayToken.advancedTo(newToken);

            assertFalse(ReplayToken.isReplay(result), "Event after reset position should not be a replay");
        }

        @Test
        void eventInGapOfTokenAtResetIsNotReplay() {
            // tokenAtReset at index 10 with gaps at 7,8 - processor saw 0-6,9,10 before reset
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, setOf(7L, 8L));
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            // Event at index 7 - this was a gap, never processed before reset
            TrackingToken newToken = GapAwareTrackingToken.newInstance(7, emptySet());
            TrackingToken result = replayToken.advancedTo(newToken);

            assertInstanceOf(ReplayToken.class, result, "Should still be in replay mode");
            assertFalse(ReplayToken.isReplay(result),
                    "Event that was in gap of tokenAtReset should NOT be marked as replay");
        }

        @Test
        void eventAfterGapButBeforeResetIndexIsReplay() {
            // tokenAtReset at index 10 with gaps at 7,8 - processor saw 0-6,9,10 before reset
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, setOf(7L, 8L));
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            // Event at index 9 - after the gaps but before reset index, WAS processed before
            // Note: newToken has gaps 7,8 because they weren't seen yet during this replay
            TrackingToken newToken = GapAwareTrackingToken.newInstance(9, setOf(7L, 8L));
            TrackingToken result = replayToken.advancedTo(newToken);

            assertInstanceOf(ReplayToken.class, result);
            assertTrue(ReplayToken.isReplay(result),
                    "Event at index 9 was processed before reset, should be a replay");
        }

        // EXAMPLE: forgot about events that were gaps - tokenAtReset NEVER covers the newToken
        // IS IT VALID SCENARIO? I have null token at the beginning and then advance to 9
        @Test
        void eventPreviouslyProcessedShouldBeReplayEvenWhenGapsFilledDuringReplay() {
            // tokenAtReset at index 10 with gaps at 7,8
            // Before reset, processor saw: 0,1,2,3,4,5,6,9,10 (NOT 7,8)
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, setOf(7L, 8L));
            TrackingToken currentToken = ReplayToken.createReplayToken(tokenAtReset, null);

            // Now event 9 arrives - currentToken has NO gaps (they were filled)
            // Event 9 WAS processed before reset, so it SHOULD be a replay
            TrackingToken newToken = GapAwareTrackingToken.newInstance(9, emptySet());
            TrackingToken result = ((ReplayToken) currentToken).advancedTo(newToken);

            assertInstanceOf(ReplayToken.class, result, "Should still be in replay mode");
            assertTrue(ReplayToken.isReplay(result),
                    "Event 9 was processed before reset, should be marked as replay even though gaps were filled");
        }

        @Test
        void eventPreviouslyProcessedShouldBeReplayEvenWhenGapsFilledDuringReplayStartNotNull() {
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, setOf(7L, 8L));
            TrackingToken currentToken = ReplayToken.createReplayToken(tokenAtReset, new GapAwareTrackingToken(0L, emptyList()));

            TrackingToken newToken = GapAwareTrackingToken.newInstance(9, emptySet());
            TrackingToken result = ((ReplayToken) currentToken).advancedTo(newToken);

            assertInstanceOf(ReplayToken.class, result, "Should still be in replay mode");
            assertTrue(ReplayToken.isReplay(result),
                    "Event 9 was processed before reset, should be marked as replay even though gaps were filled");
        }

        @Test
        void eventAtResetIndexShouldBeReplayEvenWhenGapsFilledDuringReplay() {
            // tokenAtReset at index 10 with gaps at 7,8
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, setOf(7L, 8L));
            TrackingToken currentToken = ReplayToken.createReplayToken(tokenAtReset, null);

            // During replay, gaps get filled, process 0-9
            // Event 10 - at reset index, WAS processed before reset
            TrackingToken newToken = GapAwareTrackingToken.newInstance(10, emptySet());
            TrackingToken result = ((ReplayToken) currentToken).advancedTo(newToken);

            assertInstanceOf(ReplayToken.class, result, "Should still be in replay mode at reset index");
            assertTrue(ReplayToken.isReplay(result),
                    "Event 10 was processed before reset, should be marked as replay");
        }

        @Test
        void eventAtResetIndexShouldBeReplayEvenWhenGapsFilledDuringReplay2() {
            TrackingToken tokenAtReset = new GlobalSequenceTrackingToken(10);
            TrackingToken currentToken = ReplayToken.createReplayToken(tokenAtReset, null);

            TrackingToken newToken = new GlobalSequenceTrackingToken(10);
            TrackingToken result = ((ReplayToken) currentToken).advancedTo(newToken);

            assertInstanceOf(ReplayToken.class, result, "Should still be in replay mode at reset index");
            assertTrue(ReplayToken.isReplay(result),
                    "Event 10 was processed before reset, should be marked as replay");
        }

        // upperBound helps
        @Test
        void replayBeforeResetIndexAdnGaps() {
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, setOf(7L, 8L));

            TrackingToken currentToken = ReplayToken.createReplayToken(tokenAtReset, null);

            TrackingToken newToken = GapAwareTrackingToken.newInstance(6, emptySet());
            TrackingToken result = ((ReplayToken) currentToken).advancedTo(newToken);

            assertTrue(ReplayToken.isReplay(result));
        }

        @Test
        void replayAtGapAndWithAnotherGap() {
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, setOf(7L, 8L));

            TrackingToken currentToken = ReplayToken.createReplayToken(tokenAtReset, null);

            TrackingToken newToken = GapAwareTrackingToken.newInstance(8, setOf(7L));
            TrackingToken result = ((ReplayToken) currentToken).advancedTo(newToken);

            assertFalse(ReplayToken.isReplay(result));
        }

        /**
         * Tests the specific scenario where both internal checks return false:
         * <ol>
         *   <li>{@code eventWasSeenBeforeReset()} returns {@code false} - because tokenAtReset.covers() fails for gap positions</li>
         *   <li>{@code wasDeliveredBeforeReset()} returns {@code false} - because lowerBound walks back past the gap</li>
         * </ol>
         * <p>
         * This verifies that gap-filled events are correctly identified as NEW events (not replays)
         * even though we're still within the replay period.
         * <p>
         * Scenario:
         * <pre>
         * tokenAtReset: index=10, gaps=[7, 8]
         *   → Events 0-6 were delivered before reset
         *   → Events 7-8 were skipped (gaps)
         *   → Events 9-10 were delivered before reset
         *
         * newToken: index=7, gaps=[]
         *   → Gap at position 7 is now being filled (late-arriving event)
         *
         * Flow in advancedTo():
         *   1. replayIsComplete(7) → false (still within replay range)
         *   2. eventWasSeenBeforeReset(7) → false (tokenAtReset doesn't cover position 7 - it's in gaps!)
         *   3. advanceWithUpdatedResetToken() is called
         *   4. wasDeliveredBeforeReset(7) → false (lowerBound at 7 walks back to 6, so 6 ≠ 7)
         *
         * Result: lastMessageWasReplay = false → this is a NEW event, not a replay!
         * </pre>
         */
        @Test
        void gapFilledDuringReplay_eventWasSeenBeforeResetFalse_wasDeliveredBeforeResetFalse() {
            // Setup: tokenAtReset at index 10 with gaps at 7, 8
            // Before reset, processor saw: 0,1,2,3,4,5,6,9,10 (NOT 7,8)
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, setOf(7L, 8L));
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            // Now gap at position 7 gets filled during replay
            // This event was NEVER delivered before reset - it's a new event!
            TrackingToken newToken = GapAwareTrackingToken.newInstance(7, emptySet());
            TrackingToken result = replayToken.advancedTo(newToken);

            // Should still be in replay MODE (we haven't passed tokenAtReset yet)
            assertInstanceOf(ReplayToken.class, result,
                    "Should still be a ReplayToken - we're within the replay period");

            // But this specific event is NOT a replay - it was never delivered before
            assertFalse(ReplayToken.isReplay(result),
                    "Gap-filled event at position 7 was NEVER delivered before reset, " +
                            "so it should NOT be marked as a replay. " +
                            "This tests: eventWasSeenBeforeReset=false AND wasDeliveredBeforeReset=false");
        }

        // upperBound helps
        @Test
        void replayBeforeResetIndexEvenWhenGapsWereFilledDuringReplay() {
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, setOf(7L, 8L));

            TrackingToken currentToken = ReplayToken.createReplayToken(tokenAtReset, null);
            for (int i = 0; i <= 9; i++) {
                currentToken = ((ReplayToken) currentToken).advancedTo(
                        GapAwareTrackingToken.newInstance(i, emptySet())
                );
                System.out.println("Current token: " + currentToken + " isReplay: " + ReplayToken.isReplay(currentToken));
            }

            TrackingToken newToken = GapAwareTrackingToken.newInstance(10, emptySet());
            TrackingToken result = ((ReplayToken) currentToken).advancedTo(newToken);

            assertTrue(ReplayToken.isReplay(result));
        }

        // upperBound helps
        @Test
        void replayStartsBeforeResetIndexWithoutGaps() {
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, setOf(7L, 8L));

            TrackingToken currentToken = ReplayToken.createReplayToken(tokenAtReset, null);

            TrackingToken newToken = GapAwareTrackingToken.newInstance(9, emptySet());
            TrackingToken result = ((ReplayToken) currentToken).advancedTo(newToken);

            assertTrue(ReplayToken.isReplay(result));
        }

        // upperBound helps
        @Test
        void replayStartsAtResetIndexWithoutGaps() {
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, setOf(7L, 8L));

            TrackingToken currentToken = ReplayToken.createReplayToken(tokenAtReset, null);

            TrackingToken newToken = GapAwareTrackingToken.newInstance(10, emptySet());
            TrackingToken result = ((ReplayToken) currentToken).advancedTo(newToken);

            assertTrue(ReplayToken.isReplay(result));
        }

        @Test
        void replayEndsAfterResetIndexEvenWhenGapsWereFilledDuringReplay() {
            // tokenAtReset at index 10 with gaps at 7,8
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, setOf(7L, 8L));

            // During replay, gaps get filled, process 0-10
            TrackingToken currentToken = ReplayToken.createReplayToken(tokenAtReset, null);
            for (int i = 0; i <= 10; i++) {
                currentToken = ((ReplayToken) currentToken).advancedTo(
                        GapAwareTrackingToken.newInstance(i, emptySet())
                );
            }

            // Event 11 - after reset index, never processed before
            TrackingToken newToken = GapAwareTrackingToken.newInstance(11, emptySet());
            TrackingToken result = ((ReplayToken) currentToken).advancedTo(newToken);

            assertFalse(ReplayToken.isReplay(result),
                    "Event 11 was never processed before reset, should not be a replay");
        }

        @Test
        void replayEndsAfterResetIndexEvenWhenGapsWereNotFilledDuringReplay() {
            // tokenAtReset at index 10 with gaps at 7,8
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, setOf(7L, 8L));
            TrackingToken currentToken = ReplayToken.createReplayToken(tokenAtReset, null);

            // Event 11 - after reset index, never processed before
            TrackingToken newToken = GapAwareTrackingToken.newInstance(11, setOf(7L, 8L));
            TrackingToken result = ((ReplayToken) currentToken).advancedTo(newToken);

            assertFalse(ReplayToken.isReplay(result),
                    "Event 11 was never processed before reset, should not be a replay");
        }

        /**
         * BUG SCENARIO: Only some gaps filled during replay.
         */
        @Test
        void eventPreviouslyProcessedShouldBeReplayWhenOnlySomeGapsFilled() {
            // tokenAtReset at index 10 with gaps at 7,8
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, setOf(7L, 8L));

            // During replay, only gap 7 gets filled (8 is still missing)
            // Process 0-7, then jump to 9
            TrackingToken currentToken = ReplayToken.createReplayToken(tokenAtReset, null);
            for (int i = 0; i <= 7; i++) {
                currentToken = ((ReplayToken) currentToken).advancedTo(
                        GapAwareTrackingToken.newInstance(i, emptySet())
                );
            }

            // Skip 8 (still a gap), go to 9
            // currentToken now has gap at 8 only
            TrackingToken newToken = GapAwareTrackingToken.newInstance(9, setOf(8L));
            TrackingToken result = ((ReplayToken) currentToken).advancedTo(newToken);

            assertInstanceOf(ReplayToken.class, result);
            assertTrue(ReplayToken.isReplay(result),
                    "Event 9 was processed before reset, should be replay even with partial gap fill");
        }

        @Test
        void gapsInTokenAtResetShouldNotAffectEventsBeforeGaps() {
            // tokenAtReset at index 10 with gaps at 7,8
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, setOf(7L, 8L));
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            // Event 6 - before the gaps, definitely was processed before reset
            TrackingToken newToken = GapAwareTrackingToken.newInstance(6, emptySet());
            TrackingToken result = replayToken.advancedTo(newToken);

            assertInstanceOf(ReplayToken.class, result);
            assertTrue(ReplayToken.isReplay(result),
                    "Event 6 (before gaps) was processed before reset, should be replay");
        }

        /**
         * Edge case: tokenAtReset has gap at index just before its index.
         */
        @Test
        void gapJustBeforeResetIndex() {
            // tokenAtReset at index 10 with gap at 9
            // Processor saw: 0-8, 10 (NOT 9)
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, setOf(9L));
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            // Now gap 9 gets filled during replay
            TrackingToken token9 = GapAwareTrackingToken.newInstance(9, emptySet());
            TrackingToken resultAt9 = replayToken.advancedTo(token9);

            assertInstanceOf(ReplayToken.class, resultAt9);
            // Event 9 was NOT processed before reset (was a gap), so should NOT be marked as replay
            assertFalse(ReplayToken.isReplay(resultAt9),
                    "Event 9 was a gap before reset, should NOT be marked as replay");

            // Event 10 - was processed before reset
            TrackingToken token10 = GapAwareTrackingToken.newInstance(10, emptySet());
            TrackingToken resultAt10 = ((ReplayToken) resultAt9).advancedTo(token10);

            assertInstanceOf(ReplayToken.class, resultAt10);
            assertTrue(ReplayToken.isReplay(resultAt10),
                    "Event 10 was processed before reset, should be marked as replay");
        }

        /**
         * Edge case: Multiple disjoint gaps in tokenAtReset.
         */
        @Test
        void multipleDisjointGapsInTokenAtReset() {
            // tokenAtReset at index 15 with gaps at 3,4,9,10
            // Processor saw: 0-2, 5-8, 11-15
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(15, setOf(3L, 4L, 9L, 10L));
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            // Test events at various positions
            // Event 2 - was processed
            TrackingToken result2 = replayToken.advancedTo(GapAwareTrackingToken.newInstance(2, emptySet()));
            assertTrue(ReplayToken.isReplay(result2), "Event 2 was processed, should be replay");

            // Event 3 - was NOT processed (gap)
            TrackingToken result3 = ((ReplayToken) result2).advancedTo(GapAwareTrackingToken.newInstance(3, emptySet()));
            assertFalse(ReplayToken.isReplay(result3), "Event 3 was a gap, should NOT be replay");

            // Event 5 - was processed
            TrackingToken result5 = ((ReplayToken) result3).advancedTo(
                    GapAwareTrackingToken.newInstance(5, setOf(4L)));
            assertTrue(ReplayToken.isReplay(result5), "Event 5 was processed, should be replay");
        }

        @Test
        void allGapsFilledThenEventAtResetIndex() {
            // tokenAtReset at index 5 with gap at 3
            // Before reset, processor saw: 0,1,2,4,5 (NOT 3)
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(5, setOf(3L));
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            // Process 0,1,2,3 (gap filled!)
            // Process 4 - was processed before reset
            // currentToken now is GapAwareTrackingToken(3, {}) - no gaps!
            TrackingToken token4 = GapAwareTrackingToken.newInstance(4, emptySet());
            TrackingToken resultAt4 = replayToken.advancedTo(token4);

            assertInstanceOf(ReplayToken.class, resultAt4, "Should still be in replay mode");
            assertTrue(ReplayToken.isReplay(resultAt4),
                    "Event 4 was processed before reset, should be marked as replay");

            // Process 5 - was processed before reset
            TrackingToken token5 = GapAwareTrackingToken.newInstance(5, emptySet());
            TrackingToken resultAt5 = ((ReplayToken) resultAt4).advancedTo(token5);

            assertInstanceOf(ReplayToken.class, resultAt5, "Should still be in replay mode at reset index");
            assertTrue(ReplayToken.isReplay(resultAt5),
                    "Event 5 was processed before reset, should be marked as replay");
        }

        @Test
        void gapFilledDuringReplay() {
            // Setup: tokenAtReset at index 5 with gap at 3
            // Before reset, processor saw: 0,1,2,4,5 (NOT 3)
            GapAwareTrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(5, setOf(3L));

            // During replay, events arrive INCLUDING the previously missing event 3
            // After processing events 0,1,2,3 the currentToken would be at index 3 with no gaps
            GapAwareTrackingToken currentTokenAfterFillingGap = GapAwareTrackingToken.newInstance(3, emptySet());

            // Create replay token with this state
            ReplayToken replayToken = new ReplayToken(tokenAtReset, currentTokenAfterFillingGap, null);

            // Now event 4 arrives - this event WAS processed before reset
            GapAwareTrackingToken newToken = GapAwareTrackingToken.newInstance(4, emptySet());

            TrackingToken result = replayToken.advancedTo(newToken);

            // Should still be in replay mode
            assertInstanceOf(ReplayToken.class, result, "Should still be in replay mode");
            assertTrue(ReplayToken.isReplay(result),
                    "Event 4 was processed before reset (it was NOT in the gap), " +
                            "so it SHOULD be marked as replay");
        }

        @Test
        void gapsNotFilledDuringReplay() {
            // Setup: tokenAtReset at index 5 with gap at 3
            GapAwareTrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(5, setOf(3L));

            // During replay, gap 3 is NOT filled - we skip from 2 to 4
            // After processing events 0,1,2 and then 4, currentToken has gap at 3
            GapAwareTrackingToken currentTokenWithGap = GapAwareTrackingToken.newInstance(4, setOf(3L));

            // Create replay token with this state
            ReplayToken replayToken = new ReplayToken(tokenAtReset, currentTokenWithGap, null);

            // Now event 5 arrives - this event WAS processed before reset
            GapAwareTrackingToken newToken = GapAwareTrackingToken.newInstance(5, setOf(3L));

            TrackingToken result = replayToken.advancedTo(newToken);

            assertInstanceOf(ReplayToken.class, result, "Should still be in replay mode");
            assertTrue(ReplayToken.isReplay(result),
                    "Event 5 was processed before reset, correctly marked as replay when gaps match");
        }

        @Test
        void gapFilledThenEventAtResetIndex() {
            // Setup: tokenAtReset at index 5 with gap at 3
            // Before reset, processor saw: 0,1,2,4,5 (NOT 3)
            GapAwareTrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(5, setOf(3L));

            // After processing 0,1,2,3,4 - gap filled, currentToken at 4 with no gaps
            GapAwareTrackingToken currentToken = GapAwareTrackingToken.newInstance(4, emptySet());

            ReplayToken replayToken = new ReplayToken(tokenAtReset, currentToken, null);

            // Now event 5 arrives - this event WAS processed before reset
            GapAwareTrackingToken newToken = GapAwareTrackingToken.newInstance(5, emptySet());

            TrackingToken result = replayToken.advancedTo(newToken);

            // Event 5 at the reset index should STILL be a replay because it was processed before.
            assertTrue(ReplayToken.isReplay(result),
                    "Event 5 was processed before reset, should be marked as replay");
            assertInstanceOf(ReplayToken.class, result,
                    "Should still be in replay mode at reset index - " +
                            "event 5 was processed before reset");
        }

        @Test
        void multipleGapsFilledCausesIncorrectReplayStatus() {
            // Setup: tokenAtReset at index 10 with gaps at 3,4,7
            // Before reset, processor saw: 0,1,2,5,6,8,9,10 (NOT 3,4,7)
            GapAwareTrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, setOf(3L, 4L, 7L));

            // After processing 0-8 sequentially (all gaps filled!)
            GapAwareTrackingToken currentToken = GapAwareTrackingToken.newInstance(8, emptySet());

            ReplayToken replayToken = new ReplayToken(tokenAtReset, currentToken, null);

            // Now event 9 arrives - this event WAS processed before reset
            GapAwareTrackingToken newToken = GapAwareTrackingToken.newInstance(9, emptySet());

            TrackingToken result = replayToken.advancedTo(newToken);

            assertInstanceOf(ReplayToken.class, result, "Should still be in replay mode");
            assertTrue(ReplayToken.isReplay(result),
                    "Event 9 was processed before reset, should be marked as replay");
        }

        @Test
        void eventAfterFilledGapIsReplay() {
            // Setup: tokenAtReset at index 10 with gap at 5
            // Before reset, processor saw: 0,1,2,3,4,6,7,8,9,10 (NOT 5)
            GapAwareTrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, setOf(5L));

            // After processing 0-5 sequentially (gap 5 filled!)
            GapAwareTrackingToken currentToken = GapAwareTrackingToken.newInstance(5, emptySet());

            ReplayToken replayToken = new ReplayToken(tokenAtReset, currentToken, null);

            // Now event 6 arrives - this event WAS processed before reset (it was NOT a gap)
            GapAwareTrackingToken newToken = GapAwareTrackingToken.newInstance(6, emptySet());

            TrackingToken result = replayToken.advancedTo(newToken);

            assertInstanceOf(ReplayToken.class, result, "Should still be in replay mode");
            assertTrue(ReplayToken.isReplay(result),
                    "Event 6 was processed before reset (was NOT a gap), should be marked as replay");
        }

        /**
         * Edge case: Gap at index 0.
         */
        @Test
        void gapAtIndexZeroFilledDuringReplay() {
            // Setup: tokenAtReset at index 5 with gap at 0
            // Before reset, processor saw: 1,2,3,4,5 (NOT 0)
            GapAwareTrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(5, setOf(0L));

            // Gap 0 is filled first during replay
            GapAwareTrackingToken currentToken = GapAwareTrackingToken.newInstance(0, emptySet());

            ReplayToken replayToken = new ReplayToken(tokenAtReset, currentToken, null);

            // Now event 1 arrives - this event WAS processed before reset
            GapAwareTrackingToken newToken = GapAwareTrackingToken.newInstance(1, emptySet());

            TrackingToken result = replayToken.advancedTo(newToken);

            assertInstanceOf(ReplayToken.class, result, "Should still be in replay mode");
            // BUG: tokenAtReset.covers(newToken) returns false
            assertTrue(ReplayToken.isReplay(result),
                    "Event 1 was processed before reset, should be marked as replay");
        }

        /**
         * Edge case: Partial gap fill - only some gaps filled.
         * Gap 3 filled, gap 7 not filled. Event 5 arrives.
         */
        @Test
        void partialGapFill_eventBetweenGaps() {
            // Setup: tokenAtReset at index 10 with gaps at 3,7
            // Before reset, processor saw: 0,1,2,4,5,6,8,9,10 (NOT 3,7)
            GapAwareTrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, setOf(3L, 7L));

            // Gap 3 filled, processing up to index 4
            // currentToken at 4 with only gap 7 remaining (but 7 > 4, so not in gaps yet)
            GapAwareTrackingToken currentToken = GapAwareTrackingToken.newInstance(4, emptySet());

            ReplayToken replayToken = new ReplayToken(tokenAtReset, currentToken, null);

            // Event 5 arrives - WAS processed before reset
            GapAwareTrackingToken newToken = GapAwareTrackingToken.newInstance(5, emptySet());

            TrackingToken result = replayToken.advancedTo(newToken);

            assertInstanceOf(ReplayToken.class, result, "Should still be in replay mode");
            assertTrue(ReplayToken.isReplay(result),
                    "Event 5 was processed before reset, should be marked as replay");
        }

        @Test
        void advancedToShouldKeepReplayWhenGlobalSequenceTokenReachesSameIndex() {
            GlobalSequenceTrackingToken tokenAtReset = new GlobalSequenceTrackingToken(6);

            TrackingToken replayToken = ReplayToken.createReplayToken(tokenAtReset, null);

            // Advance to the same index as tokenAtReset
            GlobalSequenceTrackingToken newToken = new GlobalSequenceTrackingToken(6);
            TrackingToken advancedToken = ((ReplayToken) replayToken).advancedTo(newToken);

            assertTrue(ReplayToken.isReplay(advancedToken),
                    "Should have exited replay mode since newToken (index 6) covers tokenAtReset (index 6)");
        }

        /**
         * When newToken index is before the gap position in tokenAtReset,
         * replay should still be in progress.
         */
        @Test
        void advancedToShouldRemainInReplayWhenNewTokenIsBeforeGapPosition() {
            // tokenAtReset: Index 6, Gaps [2]
            // newToken during replay: Index 1, Gaps []
            //
            // The newToken (index 1) is before the gap at position 2,
            // so replay should still be in progress.
            GapAwareTrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(6, Collections.singleton(2L));
            GapAwareTrackingToken newToken = GapAwareTrackingToken.newInstance(1, emptySet());

            TrackingToken replayToken = ReplayToken.createReplayToken(tokenAtReset, null);
            TrackingToken advancedToken = ((ReplayToken) replayToken).advancedTo(newToken);

            // The advanced token should still be a ReplayToken since we haven't caught up to tokenAtReset
            assertTrue(advancedToken instanceof ReplayToken,
                    "Should still be a ReplayToken since index 1 has not reached index 6");
            // And it should still be in replay mode
            assertTrue(ReplayToken.isReplay(advancedToken),
                    "Should still be in replay mode since newToken (index 1) is before the reset position (index 6)");
        }

        /**
         * When newToken has the same index as tokenAtReset but tokenAtReset has gaps,
         * replay should still be in progress because we're at the reset position.
         * Event 6 WAS processed before reset, so it's a replay.
         */
        @Test
        void advancedToShouldRemainInReplayWhenNewTokenHasSameIndexButTokenAtResetHasGaps() {
            // tokenAtReset: Index 6, Gaps [2]
            // newToken during replay: Index 6, Gaps []
            //
            // The newToken has the same index (6) as tokenAtReset.
            // Event 6 WAS processed before reset (it's not in the gaps),
            // so it should be marked as a replay.
            // The gap at position 2 was filled during replay, but that doesn't
            // change the fact that event 6 was already seen before reset.
            GapAwareTrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(6, Collections.singleton(2L));
            GapAwareTrackingToken newToken = GapAwareTrackingToken.newInstance(6, emptySet());

            TrackingToken replayToken = ReplayToken.createReplayToken(tokenAtReset, null);
            TrackingToken advancedToken = ((ReplayToken) replayToken).advancedTo(newToken);

            // Should still be in replay mode - event 6 was processed before reset
            assertTrue(ReplayToken.isReplay(advancedToken),
                    "Event 6 was processed before reset, should be marked as replay");
            assertInstanceOf(ReplayToken.class, advancedToken,
                    "Should still be a ReplayToken at the reset index");
        }

        @Test
        void advancedToShouldRemainInReplayWhenTokenAtResetHasGapsAndNewTokenDoesNot() {
            // tokenAtReset: Index 6, Gaps [1]
            // newToken during replay: Index 2, Gaps []
            //
            // The newToken (index 2) does NOT cover tokenAtReset (index 6),
            // so replay should still be in progress.
            GapAwareTrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(6, Collections.singleton(1L));
            GapAwareTrackingToken newToken = GapAwareTrackingToken.newInstance(2, emptySet());

            TrackingToken replayToken = ReplayToken.createReplayToken(tokenAtReset, null);
            TrackingToken advancedToken = ((ReplayToken) replayToken).advancedTo(newToken);

            // The advanced token should still be a ReplayToken since we haven't caught up to tokenAtReset
            assertTrue(advancedToken instanceof ReplayToken,
                    "Should still be a ReplayToken since index 2 has not reached index 6");
            // And it should still be in replay mode
            assertTrue(ReplayToken.isReplay(advancedToken),
                    "Should still be in replay mode since we haven't caught up to the reset position");
        }

        /**
         * Test with MultiSourceTrackingToken scenario.
         * <p>
         * During reset:
         * - A: Index 6, Gaps [1]
         * - B: Index 4, Gaps [1]
         * <p>
         * During replay the new token:
         * - A: Index 2, Gaps []
         * - B: Index 1, Gaps []
         * <p>
         * Source B's newToken is at index 1, which IS in the gaps of tokenAtReset for B.
         * This means event 1 for source B was NOT processed before reset - it's a new event.
         * Therefore, this should NOT be marked as a replay.
         */
        @Test
        void advancedToShouldNotBeReplayWhenNewTokenIsAtGapPosition() {
            // Setup MultiSourceTrackingToken at reset with gaps
            Map<String, TrackingToken> tokensAtReset = new HashMap<>();
            tokensAtReset.put("A", GapAwareTrackingToken.newInstance(6, Collections.singleton(1L)));
            tokensAtReset.put("B", GapAwareTrackingToken.newInstance(4, Collections.singleton(1L)));
            MultiSourceTrackingToken multiTokenAtReset = new MultiSourceTrackingToken(tokensAtReset);

            // Create replay token starting from the beginning
            TrackingToken replayToken = ReplayToken.createReplayToken(multiTokenAtReset, null);
            assertInstanceOf(ReplayToken.class, replayToken);

            // Simulate advancing during replay with a token where B is at a gap position
            Map<String, TrackingToken> newTokens = new HashMap<>();
            newTokens.put("A", GapAwareTrackingToken.newInstance(2, emptySet())); // Index 2 was processed before (not in gaps)
            newTokens.put("B", GapAwareTrackingToken.newInstance(1, emptySet())); // Index 1 was NOT processed before (in gaps!)
            MultiSourceTrackingToken newMultiToken = new MultiSourceTrackingToken(newTokens);

            TrackingToken advancedToken = ((ReplayToken) replayToken).advancedTo(newMultiToken);

            // Should still be in replay MODE (not exited yet), but this specific event is NOT a replay
            // because source B's event at index 1 was never processed before reset
            assertInstanceOf(ReplayToken.class, advancedToken,
                    "Should still be a ReplayToken since we haven't caught up to reset position");
            assertFalse(ReplayToken.isReplay(advancedToken),
                    "Should NOT be marked as replay - source B's event at index 1 was a gap (never processed before reset)");
        }

        /**
         * Test with MultiSourceTrackingToken where all positions were processed before reset.
         */
        @Test
        void advancedToShouldBeReplayWithMultiSourceTrackingTokenWhenAllPositionsWereProcessed() {
            // Setup MultiSourceTrackingToken at reset with gaps
            Map<String, TrackingToken> tokensAtReset = new HashMap<>();
            tokensAtReset.put("A", GapAwareTrackingToken.newInstance(6, Collections.singleton(1L)));
            tokensAtReset.put("B", GapAwareTrackingToken.newInstance(4, Collections.singleton(1L)));
            MultiSourceTrackingToken multiTokenAtReset = new MultiSourceTrackingToken(tokensAtReset);

            // Create replay token starting from the beginning
            TrackingToken replayToken = ReplayToken.createReplayToken(multiTokenAtReset, null);
            assertInstanceOf(ReplayToken.class, replayToken);

            // Simulate advancing during replay with positions that WERE processed before reset
            Map<String, TrackingToken> newTokens = new HashMap<>();
            newTokens.put("A", GapAwareTrackingToken.newInstance(2, emptySet())); // Index 2 was processed before (not in gaps)
            newTokens.put("B", GapAwareTrackingToken.newInstance(2, emptySet())); // Index 2 was processed before (not in gaps)
            MultiSourceTrackingToken newMultiToken = new MultiSourceTrackingToken(newTokens);

            TrackingToken advancedToken = ((ReplayToken) replayToken).advancedTo(newMultiToken);

            // Both positions were processed before reset, so this IS a replay
            assertInstanceOf(ReplayToken.class, advancedToken,
                    "Should still be a ReplayToken since we haven't caught up to reset position");
            assertTrue(ReplayToken.isReplay(advancedToken),
                    "Should be marked as replay - both source positions were processed before reset");
        }

        /**
         * Tests that advancing through gaps correctly maintains replay status.
         * When we process an event that fills a gap in the tokenAtReset, we should still be in replay
         * until we've processed all events up to the reset position.
         */
        @Test
        void advancedToShouldRemainInReplayWhenProcessingEventsWithinGaps() {
            // Token at reset: index 6 with gap at position 1
            GapAwareTrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(6, Collections.singleton(1L));

            TrackingToken replayToken = ReplayToken.createReplayToken(tokenAtReset, null);

            // Process events up to index 5 (still before the reset index of 6)
            GapAwareTrackingToken partialToken = GapAwareTrackingToken.newInstance(5, emptySet());
            TrackingToken advancedToken = ((ReplayToken) replayToken).advancedTo(partialToken);

            assertTrue(advancedToken instanceof ReplayToken,
                    "Should still be a ReplayToken since index 5 hasn't reached index 6");
            assertTrue(ReplayToken.isReplay(advancedToken),
                    "Should still be in replay mode");
        }

        /**
         * Tests that when both tokens have the same index AND same gaps, we're still in replay.
         * This is the "just reached the reset position" scenario.
         */
        @Test
        void advancedToShouldRemainInReplayWhenAtSamePositionWithSameGaps() {
            // Token at reset: index 6 with gap at position 1
            GapAwareTrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(6, Collections.singleton(1L));

            TrackingToken replayToken = ReplayToken.createReplayToken(tokenAtReset, null);

            // Advance to the exact same position with same gaps
            GapAwareTrackingToken sameToken = GapAwareTrackingToken.newInstance(6, Collections.singleton(1L));
            TrackingToken advancedToken = ((ReplayToken) replayToken).advancedTo(sameToken);

            // Should still be in replay - we're at the boundary, processing the last replay event
            assertTrue(advancedToken instanceof ReplayToken,
                    "Should still be a ReplayToken when at exact same position");
            assertTrue(ReplayToken.isReplay(advancedToken),
                    "Should still be in replay mode when at the reset position (boundary)");
        }

        /**
         * Tests that replay exits only when newToken has passed tokenAtReset's index.
         */
        @Test
        void advancedToShouldExitReplayWhenPastTokenAtResetIndex() {
            // Token at reset: index 6 with gap at position 1
            GapAwareTrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(6, Collections.singleton(1L));

            TrackingToken replayToken = ReplayToken.createReplayToken(tokenAtReset, null);

            // Advance to index 7 - this is PAST the reset position
            GapAwareTrackingToken pastToken = GapAwareTrackingToken.newInstance(7, emptySet());
            TrackingToken advancedToken = ((ReplayToken) replayToken).advancedTo(pastToken);

            // Should exit replay - we've passed the reset position
            assertFalse(ReplayToken.isReplay(advancedToken),
                    "Should exit replay mode because index 7 is past the reset position of index 6");
        }

        @Test
        void multiSourceTrackingTokenAndFilledGaps() {
            Map<String, TrackingToken> tokensAtReset = new HashMap<>();
            tokensAtReset.put("localEventStore", GapAwareTrackingToken.newInstance(11079, setOf(10220L, 10221L, 10222L, 10223L, 10224L, 10225L, 10226L, 10227L, 10228L)));
            tokensAtReset.put("globalEventStore", GapAwareTrackingToken.newInstance(38341, setOf(37921L, 37922L, 37923L, 37924L, 37925L, 37926L, 37927L)));
            MultiSourceTrackingToken multiTokenAtReset = new MultiSourceTrackingToken(tokensAtReset);

            Map<String, TrackingToken> startPosition = new HashMap<>();
            startPosition.put("localEventStore", GapAwareTrackingToken.newInstance(9960, emptySet()));
            startPosition.put("globalEventStore", GapAwareTrackingToken.newInstance(37192, emptySet()));
            MultiSourceTrackingToken multiStartPosition = new MultiSourceTrackingToken(startPosition);

            TrackingToken replayToken = ReplayToken.createReplayToken(multiTokenAtReset, multiStartPosition);

            // Advance to this position
            Map<String, TrackingToken> nextPositions = new HashMap<>();
            nextPositions.put("localEventStore", GapAwareTrackingToken.newInstance(9961, emptySet()));
            nextPositions.put("globalEventStore", GapAwareTrackingToken.newInstance(37192, emptySet()));
            TrackingToken nextToken = new MultiSourceTrackingToken(nextPositions);

            TrackingToken advancedToken = ((ReplayToken) replayToken).advancedTo(nextToken);

            assertInstanceOf(ReplayToken.class, advancedToken);
            assertTrue(ReplayToken.isReplay(advancedToken));
        }

        @Test
        void multiSourceTrackingTokenAndFilledALotOfGaps() {
            Map<String, TrackingToken> tokensAtReset = new HashMap<>();
            tokensAtReset.put("localEventStore", GapAwareTrackingToken.newInstance(11079, list(10155, 10156, 10157, 10158, 10159, 10160, 10161, 10162, 10163, 10164, 10165, 10166, 10167, 10168, 10169, 10170, 10171, 10172, 10173, 10174, 10175, 10176, 10177, 10178, 10179, 10180, 10198, 10199, 10200, 10201, 10202, 10203, 10204, 10205, 10206, 10207, 10208, 10209, 10210, 10211, 10212, 10213, 10214, 10215, 10216, 10217, 10218, 10219, 10220, 10221, 10222, 10223, 10224, 10225, 10226, 10227, 10228)));
            tokensAtReset.put("globalEventStore", GapAwareTrackingToken.newInstance(38341, list(36941, 36942, 36943, 36944, 36945, 36946, 36947, 36948, 36949, 36950, 36951, 36952, 36953, 36954, 36955, 36956, 36957, 36958, 36959, 36960, 36961, 36962, 36963, 36964, 36965, 36966, 36967, 36968, 36969, 36970, 37157, 37158, 37159, 37160, 37161, 37162, 37163, 37164, 37165, 37166, 37167, 37168, 37169, 37170, 37171, 37172, 37173, 37174, 37175, 37176, 37177, 37178, 37179, 37180, 37181, 37182, 37183, 37184, 37295, 37296, 37297, 37298, 37299, 37300, 37301, 37302, 37303, 37304, 37305, 37306, 37307, 37308, 37309, 37310, 37311, 37312, 37313, 37314, 37315, 37316, 37317, 37318, 37319, 37320, 37321, 37322, 37323, 37324, 37331, 37332, 37333, 37334, 37335, 37336, 37337, 37338, 37339, 37340, 37341, 37342, 37343, 37344, 37345, 37346, 37347, 37348, 37349, 37350, 37351, 37352, 37353, 37354, 37355, 37356, 37357, 37358, 37359, 37360, 37361, 37362, 37717, 37718, 37719, 37720, 37721, 37722, 37723, 37724, 37725, 37726, 37727, 37728, 37729, 37730, 37731, 37732, 37733,
                    37734, 37897, 37898, 37899, 37900, 37901, 37902, 37903, 37904, 37905, 37906, 37907, 37908, 37909, 37910, 37911, 37912, 37913, 37914, 37915, 37916, 37917, 37918, 37919, 37920, 37921, 37922, 37923, 37924, 37925, 37926, 37927)));
            MultiSourceTrackingToken multiTokenAtReset = new MultiSourceTrackingToken(tokensAtReset);

            Map<String, TrackingToken> startPosition = new HashMap<>();
            startPosition.put("localEventStore", GapAwareTrackingToken.newInstance(9960, emptySet()));
            startPosition.put("globalEventStore", GapAwareTrackingToken.newInstance(37192, emptySet()));
            MultiSourceTrackingToken multiStartPosition = new MultiSourceTrackingToken(startPosition);

            TrackingToken replayToken = ReplayToken.createReplayToken(multiTokenAtReset, multiStartPosition);

            // Advance to this position
            Map<String, TrackingToken> nextPositions = new HashMap<>();
            nextPositions.put("localEventStore", GapAwareTrackingToken.newInstance(9961, emptySet()));
            nextPositions.put("globalEventStore", GapAwareTrackingToken.newInstance(37192, emptySet()));
            TrackingToken nextToken = new MultiSourceTrackingToken(nextPositions);

            TrackingToken advancedToken = ((ReplayToken) replayToken).advancedTo(nextToken);

            assertInstanceOf(ReplayToken.class, advancedToken);
            assertTrue(ReplayToken.isReplay(advancedToken));
        }

        private List<Long> list(Integer... values) {
            return Stream.of(values)
                    .map(Long::valueOf)
                    .collect(Collectors.toList());
        }

    }

    @Nested
    class AdvancedToWithMergedTrackingTokenAndGaps {

        @ParameterizedTest
        @MethodSource("org.axonframework.eventhandling.ReplayTokenTest#advancedToParameters")
        void advancedToShouldReturnCorrectTokenTypeAndReplayStatus(
                int index,
                Set<Long> gaps,
                Class<?> expectedTokenType,
                boolean expectedIsReplay
        ) {
            MergedTrackingToken tokenAtReset = new MergedTrackingToken(
                    new GapAwareTrackingToken(3, setOf(2L)),
                    new GapAwareTrackingToken(9, emptySet())
            );
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            TrackingToken result = replayToken.advancedTo(new GapAwareTrackingToken(index, gaps));

            assertInstanceOf(expectedTokenType, result);
            assertEquals(expectedIsReplay, ReplayToken.isReplay(result));
        }
    }

    // TODO: Validate those tests cases if it was expected!!!
    static Stream<Arguments> advancedToParameters() {
        return Stream.of(
                // index, gaps, expectedTokenType, expectedIsReplay
                Arguments.of(1, emptySet(), ReplayToken.class, true),
                Arguments.of(2, emptySet(), ReplayToken.class, false),
                Arguments.of(3, emptySet(), ReplayToken.class, true),
                Arguments.of(4, emptySet(), ReplayToken.class, false),
                Arguments.of(9, emptySet(), ReplayToken.class, false),
                Arguments.of(10, emptySet(), GapAwareTrackingToken.class, false)
        );
    }

    private static Set<Long> setOf(Long... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    @Nested
    class Same {

        @Test
        void sameWithReplayTokenComparesCurrentTokens() {
            TrackingToken tokenAtReset = new GlobalSequenceTrackingToken(10);
            TrackingToken currentToken1 = new GlobalSequenceTrackingToken(5);
            TrackingToken currentToken2 = new GlobalSequenceTrackingToken(5);

            ReplayToken replayToken1 = new ReplayToken(tokenAtReset, currentToken1, null);
            ReplayToken replayToken2 = new ReplayToken(tokenAtReset, currentToken2, null);

            assertTrue(replayToken1.equalsLatest(replayToken2));
            assertTrue(replayToken2.equalsLatest(replayToken1));
        }

        @Test
        void sameWithReplayTokenReturnsFalseWhenCurrentTokensDiffer() {
            TrackingToken tokenAtReset = new GlobalSequenceTrackingToken(10);
            TrackingToken currentToken1 = new GlobalSequenceTrackingToken(5);
            TrackingToken currentToken2 = new GlobalSequenceTrackingToken(6);

            ReplayToken replayToken1 = new ReplayToken(tokenAtReset, currentToken1, null);
            ReplayToken replayToken2 = new ReplayToken(tokenAtReset, currentToken2, null);

            assertFalse(replayToken1.equalsLatest(replayToken2));
            assertFalse(replayToken2.equalsLatest(replayToken1));
        }

        @Test
        void sameWithNonReplayTokenDelegatesToCurrentToken() {
            TrackingToken tokenAtReset = new GlobalSequenceTrackingToken(10);
            TrackingToken currentToken = new GlobalSequenceTrackingToken(5);
            TrackingToken otherToken = new GlobalSequenceTrackingToken(5);

            ReplayToken replayToken = new ReplayToken(tokenAtReset, currentToken, null);

            assertTrue(replayToken.equalsLatest(otherToken));
        }

        @Test
        void sameWithNonReplayTokenReturnsFalseWhenDifferent() {
            TrackingToken tokenAtReset = new GlobalSequenceTrackingToken(10);
            TrackingToken currentToken = new GlobalSequenceTrackingToken(5);
            TrackingToken otherToken = new GlobalSequenceTrackingToken(6);

            ReplayToken replayToken = new ReplayToken(tokenAtReset, currentToken, null);

            assertFalse(replayToken.equalsLatest(otherToken));
        }

        @Test
        void sameWithNullCurrentTokenReturnsFalse() {
            TrackingToken tokenAtReset = new GlobalSequenceTrackingToken(10);
            ReplayToken replayToken = new ReplayToken(tokenAtReset, null, null);

            TrackingToken otherToken = new GlobalSequenceTrackingToken(5);

            assertFalse(replayToken.equalsLatest(otherToken));
        }

        @Test
        void sameWithBothNullCurrentTokens() {
            TrackingToken tokenAtReset = new GlobalSequenceTrackingToken(10);
            ReplayToken replayToken1 = new ReplayToken(tokenAtReset, null, null);
            ReplayToken replayToken2 = new ReplayToken(tokenAtReset, null, null);

            // When both have null currentToken, same() returns false
            // because currentToken != null check fails
            assertFalse(replayToken1.equalsLatest(replayToken2));
        }

        @Test
        void sameWithGapAwareTrackingToken() {
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, emptySet());
            TrackingToken currentToken = GapAwareTrackingToken.newInstance(5, emptySet());
            TrackingToken sameCurrentToken = GapAwareTrackingToken.newInstance(5, emptySet());

            ReplayToken replayToken = new ReplayToken(tokenAtReset, currentToken, null);

            assertTrue(replayToken.equalsLatest(sameCurrentToken));
        }
    }
}
