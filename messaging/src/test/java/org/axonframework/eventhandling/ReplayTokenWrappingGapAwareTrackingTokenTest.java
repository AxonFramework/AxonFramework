package org.axonframework.eventhandling;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for validating the behavior of {@link ReplayToken} that wraps {@link GapAwareTrackingToken}.
 *
 * @author Mateusz Nowak
 * @since 4.12.3
 */
public class ReplayTokenWrappingGapAwareTrackingTokenTest {

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

        @ParameterizedTest(name = "{5}: event at {2} \u2192 isReplay={4}")
        @MethodSource("org.axonframework.eventhandling.ReplayTokenWrappingGapAwareTrackingTokenTest#gapPositionScenarios")
        void eventPositionRelativeToGaps(
                long tokenAtResetIndex,
                Set<Long> gaps,
                long eventIndex,
                Set<Long> eventGaps,
                boolean expectedReplay,
                String description
        ) {
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(tokenAtResetIndex, gaps);
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            TrackingToken newToken = GapAwareTrackingToken.newInstance(eventIndex, eventGaps);
            TrackingToken result = replayToken.advancedTo(newToken);

            assertInstanceOf(ReplayToken.class, result, "Should still be in replay mode");
            assertEquals(expectedReplay, ReplayToken.isReplay(result));
        }

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
        void replayBeforeResetIndexAndGaps() {
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

        @Test
        void replayAtResetIndexEvenWhenGapsWereFilledDuringReplay() {
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, setOf(7L, 8L));

            TrackingToken currentToken = ReplayToken.createReplayToken(tokenAtReset, null);
            for (int i = 0; i <= 9; i++) {
                currentToken = ((ReplayToken) currentToken).advancedTo(
                        GapAwareTrackingToken.newInstance(i, emptySet())
                );
            }

            TrackingToken newToken = GapAwareTrackingToken.newInstance(10, emptySet());
            TrackingToken result = ((ReplayToken) currentToken).advancedTo(newToken);

            assertTrue(ReplayToken.isReplay(result));
        }

        @Test
        void replayEndsAfterResetIndexEvenWhenGapsWereFilledDuringReplay() {
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, setOf(7L, 8L));

            TrackingToken currentToken = ReplayToken.createReplayToken(tokenAtReset, null);
            for (int i = 0; i <= 10; i++) {
                currentToken = ((ReplayToken) currentToken).advancedTo(
                        GapAwareTrackingToken.newInstance(i, emptySet())
                );
            }

            TrackingToken newToken = GapAwareTrackingToken.newInstance(11, emptySet());
            TrackingToken result = ((ReplayToken) currentToken).advancedTo(newToken);

            assertFalse(ReplayToken.isReplay(result),
                    "Event 11 was never processed before reset, should not be a replay");
        }

        @Test
        void replayEndsAfterResetIndexEvenWhenGapsWereNotFilledDuringReplay() {
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, setOf(7L, 8L));
            TrackingToken currentToken = ReplayToken.createReplayToken(tokenAtReset, null);

            TrackingToken newToken = GapAwareTrackingToken.newInstance(11, setOf(7L, 8L));
            TrackingToken result = ((ReplayToken) currentToken).advancedTo(newToken);

            assertFalse(ReplayToken.isReplay(result),
                    "Event 11 was never processed before reset, should not be a replay");
        }

        @Test
        void eventPreviouslyProcessedShouldBeReplayWhenOnlySomeGapsFilled() {
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, setOf(7L, 8L));

            TrackingToken currentToken = ReplayToken.createReplayToken(tokenAtReset, null);
            for (int i = 0; i <= 7; i++) {
                currentToken = ((ReplayToken) currentToken).advancedTo(
                        GapAwareTrackingToken.newInstance(i, emptySet())
                );
            }

            TrackingToken newToken = GapAwareTrackingToken.newInstance(9, setOf(8L));
            TrackingToken result = ((ReplayToken) currentToken).advancedTo(newToken);

            assertInstanceOf(ReplayToken.class, result);
            assertTrue(ReplayToken.isReplay(result),
                    "Event 9 was processed before reset, should be replay even with partial gap fill");
        }

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

        @ParameterizedTest(name = "{4}")
        @MethodSource("org.axonframework.eventhandling.ReplayTokenWrappingGapAwareTrackingTokenTest#remainInReplayScenarios")
        void advancedToShouldRemainInReplay(
                long tokenAtResetIndex,
                Set<Long> tokenAtResetGaps,
                long newTokenIndex,
                Set<Long> newTokenGaps,
                String description
        ) {
            GapAwareTrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(tokenAtResetIndex, tokenAtResetGaps);
            TrackingToken replayToken = ReplayToken.createReplayToken(tokenAtReset, null);

            GapAwareTrackingToken newToken = GapAwareTrackingToken.newInstance(newTokenIndex, newTokenGaps);
            TrackingToken advancedToken = ((ReplayToken) replayToken).advancedTo(newToken);

            assertInstanceOf(ReplayToken.class, advancedToken, "Should still be a ReplayToken");
            assertTrue(ReplayToken.isReplay(advancedToken), "Should still be in replay mode");
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

    }

    // Test parameters for advancedTo remaining in replay mode
    // All scenarios where advancing should keep the token in replay mode
    static Stream<Arguments> remainInReplayScenarios() {
        return Stream.of(
                // tokenAtResetIndex, tokenAtResetGaps, newTokenIndex, newTokenGaps, description
                Arguments.of(6L, setOf(2L), 1L, emptySet(), "newToken before gap position"),
                Arguments.of(6L, setOf(2L), 6L, emptySet(), "newToken at same index, gap filled"),
                Arguments.of(6L, setOf(1L), 2L, emptySet(), "tokenAtReset has gaps, newToken does not"),
                Arguments.of(6L, setOf(1L), 5L, emptySet(), "processing within gaps range"),
                Arguments.of(6L, setOf(1L), 6L, setOf(1L), "same position with same gaps")
        );
    }

    // Test parameters for gap position scenarios - isReplay depends on event position relative to gaps
    // tokenAtReset at index 10 with gaps at 7,8 - processor saw 0-6,9,10 before reset
    static Stream<Arguments> gapPositionScenarios() {
        return Stream.of(
                // tokenAtResetIndex, gaps, eventIndex, eventGaps, expectedReplay, description
                Arguments.of(10L, setOf(7L, 8L), 7L, emptySet(), false, "event at gap position"),
                Arguments.of(10L, setOf(7L, 8L), 9L, setOf(7L, 8L), true, "event after gap but before reset"),
                Arguments.of(10L, setOf(7L, 8L), 6L, emptySet(), true, "event before gaps")
        );
    }

    @Nested
    class MultiSourceTrackingTokenAdvancedTo {


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
    class MergedTrackingTokenAdvancedToGapAwareTrackingToken {

        @ParameterizedTest
        @MethodSource("org.axonframework.eventhandling.ReplayTokenWrappingGapAwareTrackingTokenTest#advancedToParameters")
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

        // ==================== Basic Scenarios ====================

        /**
         * Basic scenario: MergedTrackingToken with segments at different positions, no gaps.
         * <pre>
         * tokenAtReset = MergedTrackingToken {
         *     lowerSegment: position 5
         *     upperSegment: position 10
         * }
         * upperBound = 10
         *
         * Events 0-5 were processed by lower segment before reset.
         * Events 6-10 were NOT processed by lower segment (only by upper).
         * Event 11 is new (not a replay).
         * </pre>
         */
        @ParameterizedTest(name = "event {3}: position {0} → isReplay={2}")
        @MethodSource("org.axonframework.eventhandling.ReplayTokenWrappingGapAwareTrackingTokenTest#mergedTokenNoGapsPositions")
        void mergedTokenNoGaps_eventAtPosition(
                int position,
                Class<?> expectedTokenType,
                boolean expectedIsReplay,
                String description
        ) {
            MergedTrackingToken tokenAtReset = new MergedTrackingToken(
                    GapAwareTrackingToken.newInstance(5, emptySet()),
                    GapAwareTrackingToken.newInstance(10, emptySet())
            );
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            TrackingToken result = replayToken.advancedTo(GapAwareTrackingToken.newInstance(position, emptySet()));

            assertInstanceOf(expectedTokenType, result);
            assertEquals(expectedIsReplay, ReplayToken.isReplay(result));
        }

        // ==================== Gaps in Lower Segment ====================

        /**
         * MergedTrackingToken with gap in lower segment.
         * <pre>
         * tokenAtReset = MergedTrackingToken {
         *     lowerSegment: position 5, gaps [3]  ← gap at position 3
         *     upperSegment: position 10
         * }
         *
         * Before reset:
         * - Lower segment saw: 0,1,2,4,5 (NOT 3)
         * - Upper segment saw: 0-10
         *
         * Combined: events 0,1,2,4,5,6,7,8,9,10 were seen.
         * Event 3 was NOT seen (it's in the gap).
         * </pre>
         */
        @ParameterizedTest(name = "{2}: position {0} → isReplay={1}")
        @MethodSource("org.axonframework.eventhandling.ReplayTokenWrappingGapAwareTrackingTokenTest#mergedTokenWithGapInLowerPositions")
        void mergedTokenWithGapInLower_eventAtPosition(int position, boolean expectedReplay, String description) {
            MergedTrackingToken tokenAtReset = new MergedTrackingToken(
                    GapAwareTrackingToken.newInstance(5, setOf(3L)),  // gap at 3
                    GapAwareTrackingToken.newInstance(10, emptySet())
            );
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            TrackingToken result = replayToken.advancedTo(GapAwareTrackingToken.newInstance(position, emptySet()));

            assertInstanceOf(ReplayToken.class, result, "Should still be in replay mode");
            assertEquals(expectedReplay, ReplayToken.isReplay(result));
        }

        // ==================== Gaps in Upper Segment ====================

        /**
         * MergedTrackingToken with gap in upper segment.
         * <pre>
         * tokenAtReset = MergedTrackingToken {
         *     lowerSegment: position 5
         *     upperSegment: position 10, gaps [8]  ← gap at position 8
         * }
         *
         * Before reset:
         * - Lower segment saw: 0-5
         * - Upper segment saw: 0-7,9,10 (NOT 8)
         *
         * For positions 0-5: both segments saw them → replay
         * For positions 6-7: upper segment saw them → replay
         * For position 8: gap in upper segment → NOT replay
         * For positions 9-10: upper segment saw them → replay
         * </pre>
         */
        @ParameterizedTest(name = "{3}: position {0} → isReplay={2}")
        @MethodSource("org.axonframework.eventhandling.ReplayTokenWrappingGapAwareTrackingTokenTest#mergedTokenWithGapInUpperPositions")
        void mergedTokenWithGapInUpper_eventAtPosition(int position, Set<Long> eventGaps, boolean expectedReplay, String description) {
            MergedTrackingToken tokenAtReset = new MergedTrackingToken(
                    GapAwareTrackingToken.newInstance(5, emptySet()),
                    GapAwareTrackingToken.newInstance(10, setOf(8L))  // gap at 8
            );
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            TrackingToken result = replayToken.advancedTo(GapAwareTrackingToken.newInstance(position, eventGaps));

            assertInstanceOf(ReplayToken.class, result, "Should still be in replay mode");
            assertEquals(expectedReplay, ReplayToken.isReplay(result));
        }

        // ==================== Gaps in Both Segments ====================

        /**
         * MergedTrackingToken with gaps in both segments.
         * <pre>
         * tokenAtReset = MergedTrackingToken {
         *     lowerSegment: position 5, gaps [2]
         *     upperSegment: position 10, gaps [8]
         * }
         *
         * Lower segment saw: 0,1,3,4,5 (NOT 2)
         * Upper segment saw: 0-7,9,10 (NOT 8)
         *
         * Position 2: gap in lower → NOT replay
         * Position 8: gap in upper → NOT replay
         * Other positions up to 10: replay
         * </pre>
         */
        @ParameterizedTest(name = "{2}: position {0} → isReplay={1}")
        @MethodSource("org.axonframework.eventhandling.ReplayTokenWrappingGapAwareTrackingTokenTest#mergedTokenWithGapsInBothPositions")
        void mergedTokenWithGapsInBoth_eventAtPosition(int position, boolean expectedReplay, String description) {
            MergedTrackingToken tokenAtReset = new MergedTrackingToken(
                    GapAwareTrackingToken.newInstance(5, setOf(2L)),
                    GapAwareTrackingToken.newInstance(10, setOf(8L))
            );
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            TrackingToken result = replayToken.advancedTo(GapAwareTrackingToken.newInstance(position, emptySet()));

            assertInstanceOf(ReplayToken.class, result, "Should still be in ReplayToken wrapper");
            assertEquals(expectedReplay, ReplayToken.isReplay(result));
        }

        // ==================== Progressive Replay Through MergedTrackingToken ====================

        /**
         * Tests progressive replay through a MergedTrackingToken, verifying replay status at each step.
         * Key insight: Only events ≤ lower segment index are replays. Events past lower but before
         * upper are NOT replays (lower segment hasn't seen them) but stay in ReplayToken wrapper.
         */
        @Test
        void progressiveReplayThroughMergedToken() {
            MergedTrackingToken tokenAtReset = new MergedTrackingToken(
                    GapAwareTrackingToken.newInstance(3, setOf(2L)),  // gap at 2, lower at 3
                    GapAwareTrackingToken.newInstance(9, emptySet())  // upper at 9
            );
            TrackingToken currentToken = ReplayToken.createReplayToken(tokenAtReset, null);

            // Position 0: before lower segment, was processed → replay
            currentToken = ((ReplayToken) currentToken).advancedTo(GapAwareTrackingToken.newInstance(0, emptySet()));
            assertTrue(ReplayToken.isReplay(currentToken), "Position 0 should be replay");

            // Position 1: before gap, was processed → replay
            currentToken = ((ReplayToken) currentToken).advancedTo(GapAwareTrackingToken.newInstance(1, emptySet()));
            assertTrue(ReplayToken.isReplay(currentToken), "Position 1 should be replay");

            // Position 2: gap in lower segment → NOT replay
            currentToken = ((ReplayToken) currentToken).advancedTo(GapAwareTrackingToken.newInstance(2, emptySet()));
            assertFalse(ReplayToken.isReplay(currentToken), "Position 2 (gap) should NOT be replay");

            // Position 3: at lower segment index, was processed → replay
            currentToken = ((ReplayToken) currentToken).advancedTo(GapAwareTrackingToken.newInstance(3, emptySet()));
            assertTrue(ReplayToken.isReplay(currentToken), "Position 3 should be replay");

            // Position 5: between segments → NOT replay (lower segment hasn't seen it)
            currentToken = ((ReplayToken) currentToken).advancedTo(GapAwareTrackingToken.newInstance(5, emptySet()));
            assertInstanceOf(ReplayToken.class, currentToken, "Should still be in ReplayToken");
            assertFalse(ReplayToken.isReplay(currentToken), "Position 5 should NOT be replay");

            // Position 9: at upper segment → NOT replay (lower segment hasn't seen it)
            currentToken = ((ReplayToken) currentToken).advancedTo(GapAwareTrackingToken.newInstance(9, emptySet()));
            assertInstanceOf(ReplayToken.class, currentToken, "Should still be in ReplayToken");
            assertFalse(ReplayToken.isReplay(currentToken), "Position 9 should NOT be replay");

            // Position 10: past upper segment → exits replay
            currentToken = ((ReplayToken) currentToken).advancedTo(GapAwareTrackingToken.newInstance(10, emptySet()));
            assertFalse(ReplayToken.isReplay(currentToken), "Position 10 should exit replay");
        }

        // ==================== Asymmetric Segment Positions ====================

        /**
         * Tests when lower segment is far behind upper segment.
         * Events between segments are NOT replays - lower segment hasn't seen them.
         */
        @Test
        void asymmetricSegments_lowerFarBehind() {
            MergedTrackingToken tokenAtReset = new MergedTrackingToken(
                    GapAwareTrackingToken.newInstance(3, emptySet()),   // far behind
                    GapAwareTrackingToken.newInstance(100, emptySet())  // far ahead
            );
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            // Event at position 50 - between segments, lower segment hasn't seen it
            TrackingToken result = replayToken.advancedTo(GapAwareTrackingToken.newInstance(50, emptySet()));

            assertInstanceOf(ReplayToken.class, result, "Should still be in ReplayToken wrapper");
            assertFalse(ReplayToken.isReplay(result),
                    "Event at position 50 is NOT replay - lower segment hasn't seen it");
        }

        @Test
        void asymmetricSegments_eventPastUpperBound_exitsReplay() {
            MergedTrackingToken tokenAtReset = new MergedTrackingToken(
                    GapAwareTrackingToken.newInstance(3, emptySet()),
                    GapAwareTrackingToken.newInstance(100, emptySet())
            );
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            // Event at position 101 - past upper bound
            TrackingToken result = replayToken.advancedTo(GapAwareTrackingToken.newInstance(101, emptySet()));

            assertFalse(ReplayToken.isReplay(result),
                    "Event past upper bound should exit replay");
        }

        // ==================== Edge Cases ====================

        /**
         * When both segments are at the same position.
         */
        @Test
        void segmentsAtSamePosition_eventAtThatPosition_isReplay() {
            MergedTrackingToken tokenAtReset = new MergedTrackingToken(
                    GapAwareTrackingToken.newInstance(10, emptySet()),
                    GapAwareTrackingToken.newInstance(10, emptySet())
            );
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            TrackingToken result = replayToken.advancedTo(GapAwareTrackingToken.newInstance(10, emptySet()));

            assertInstanceOf(ReplayToken.class, result);
            assertTrue(ReplayToken.isReplay(result),
                    "Event at shared position should be replay");
        }

        @Test
        void segmentsAtSamePosition_eventPastThatPosition_exitsReplay() {
            MergedTrackingToken tokenAtReset = new MergedTrackingToken(
                    GapAwareTrackingToken.newInstance(10, emptySet()),
                    GapAwareTrackingToken.newInstance(10, emptySet())
            );
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            TrackingToken result = replayToken.advancedTo(GapAwareTrackingToken.newInstance(11, emptySet()));

            assertFalse(ReplayToken.isReplay(result),
                    "Event past shared position should exit replay");
        }

        /**
         * When segments have overlapping gaps.
         */
        @Test
        void overlappingGaps_eventAtSharedGap_isNotReplay() {
            // Both segments have gap at position 5
            MergedTrackingToken tokenAtReset = new MergedTrackingToken(
                    GapAwareTrackingToken.newInstance(7, setOf(5L)),
                    GapAwareTrackingToken.newInstance(10, setOf(5L))
            );
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            TrackingToken result = replayToken.advancedTo(GapAwareTrackingToken.newInstance(5, emptySet()));

            assertInstanceOf(ReplayToken.class, result);
            assertFalse(ReplayToken.isReplay(result),
                    "Event at shared gap position should NOT be replay");
        }

        /**
         * Gap filled during replay doesn't affect replay status of other events.
         */
        @Test
        void gapFilledDuringReplay_subsequentNonGapEvent_isStillReplay() {
            MergedTrackingToken tokenAtReset = new MergedTrackingToken(
                    GapAwareTrackingToken.newInstance(5, setOf(2L)),
                    GapAwareTrackingToken.newInstance(10, emptySet())
            );
            TrackingToken currentToken = ReplayToken.createReplayToken(tokenAtReset, null);

            // First fill the gap at position 2
            currentToken = ((ReplayToken) currentToken).advancedTo(GapAwareTrackingToken.newInstance(2, emptySet()));
            assertFalse(ReplayToken.isReplay(currentToken), "Gap fill should NOT be replay");

            // Now process position 3 - this was processed before reset
            currentToken = ((ReplayToken) currentToken).advancedTo(GapAwareTrackingToken.newInstance(3, emptySet()));
            assertTrue(ReplayToken.isReplay(currentToken),
                    "Position 3 (processed before reset) should be replay even after gap fill");
        }

        // ==================== Nested MergedTrackingToken in Segments ====================

        /**
         * Complex scenario: MergedTrackingToken containing another MergedTrackingToken.
         * This can happen with multiple merge operations.
         * The lowerBound of nested MergedToken is still the innermost lowerSegment (3).
         */
        @Test
        void nestedMergedTrackingToken_basicReplayBehavior() {
            // Inner merged token
            MergedTrackingToken innerMerged = new MergedTrackingToken(
                    GapAwareTrackingToken.newInstance(3, emptySet()),
                    GapAwareTrackingToken.newInstance(5, emptySet())
            );

            // Outer merged token with inner as lower segment
            MergedTrackingToken tokenAtReset = new MergedTrackingToken(
                    innerMerged,
                    GapAwareTrackingToken.newInstance(10, emptySet())
            );

            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            // Event at position 7 - past innermost lower segment (3), NOT a replay
            TrackingToken result = replayToken.advancedTo(GapAwareTrackingToken.newInstance(7, emptySet()));

            assertInstanceOf(ReplayToken.class, result, "Should still be in ReplayToken wrapper");
            assertFalse(ReplayToken.isReplay(result),
                    "Event at position 7 is NOT replay - innermost lower segment (3) hasn't seen it");
        }

        @Test
        void nestedMergedTrackingToken_exitReplayPastOuterUpperBound() {
            MergedTrackingToken innerMerged = new MergedTrackingToken(
                    GapAwareTrackingToken.newInstance(3, emptySet()),
                    GapAwareTrackingToken.newInstance(5, emptySet())
            );

            MergedTrackingToken tokenAtReset = new MergedTrackingToken(
                    innerMerged,
                    GapAwareTrackingToken.newInstance(10, emptySet())
            );

            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            // Event at position 11 - past outer upper bound
            TrackingToken result = replayToken.advancedTo(GapAwareTrackingToken.newInstance(11, emptySet()));

            assertFalse(ReplayToken.isReplay(result),
                    "Event past outer upper bound should exit replay");
        }
    }

    // Test parameters for the parameterized test
    // MergedTrackingToken: lower(3, gaps[2]), upper(9, gaps[])
    // - Position 1: before gap at 2, was processed → replay=true
    // - Position 2: gap in lower segment → replay=false (NOT processed)
    // - Position 3: at lower index, was processed → replay=true
    // - Position 4: after lower, before upper, in upper's range but NOT in lower's gaps → replay depends on implementation
    // - Position 9: at upper index → replay=true
    // - Position 10: past upper index → exits replay, returns GapAwareTrackingToken
    static Stream<Arguments> advancedToParameters() {
        return Stream.of(
                // index, gaps, expectedTokenType, expectedIsReplay
                Arguments.of(1, emptySet(), ReplayToken.class, true),    // before gap, was processed
                Arguments.of(2, emptySet(), ReplayToken.class, false),   // at gap position
                Arguments.of(3, emptySet(), ReplayToken.class, true),    // at lower index
                Arguments.of(4, emptySet(), ReplayToken.class, false),   // past lower, upper doesn't have this as gap but lower does contextually
                Arguments.of(9, emptySet(), ReplayToken.class, false),   // at upper index - needs verification
                Arguments.of(10, emptySet(), GapAwareTrackingToken.class, false)  // past upper, exits replay
        );
    }

    // Test parameters for MergedTrackingToken with no gaps in segments
    // MergedTrackingToken: lower(5, no gaps), upper(10, no gaps)
    // Tests replay status at different positions relative to the segments
    static Stream<Arguments> mergedTokenNoGapsPositions() {
        return Stream.of(
                // position, expectedTokenType, expectedIsReplay, description
                Arguments.of(3, ReplayToken.class, true, "before lower segment"),
                Arguments.of(5, ReplayToken.class, true, "at lower segment"),
                Arguments.of(7, ReplayToken.class, false, "between segments"),
                Arguments.of(10, ReplayToken.class, false, "at upper segment"),
                Arguments.of(11, GapAwareTrackingToken.class, false, "past upper segment")
        );
    }

    // Test parameters for MergedToken with gap in lower segment
    // tokenAtReset = MergedTrackingToken { lower: position 5 with gap at 3, upper: position 10 }
    static Stream<Arguments> mergedTokenWithGapInLowerPositions() {
        return Stream.of(
                // position, expectedReplay, description
                Arguments.of(3, false, "event at gap position"),
                Arguments.of(2, true, "event before gap"),
                Arguments.of(4, true, "event after gap but before lower index")
        );
    }

    // Test parameters for MergedToken with gap in upper segment
    // tokenAtReset = MergedTrackingToken { lower: position 5, upper: position 10 with gap at 8 }
    static Stream<Arguments> mergedTokenWithGapInUpperPositions() {
        return Stream.of(
                // position, eventGaps, expectedReplay, description
                Arguments.of(8, emptySet(), false, "event at gap position"),
                Arguments.of(9, setOf(8L), false, "event after gap (past lower segment)")
        );
    }

    // Test parameters for MergedToken with gaps in both segments
    // tokenAtReset = MergedTrackingToken { lower: position 5 with gap at 2, upper: position 10 with gap at 8 }
    static Stream<Arguments> mergedTokenWithGapsInBothPositions() {
        return Stream.of(
                // position, expectedReplay, description
                Arguments.of(2, false, "event at lower gap"),
                Arguments.of(8, false, "event at upper gap"),
                Arguments.of(7, false, "event at non-gap position (past lower segment)")
        );
    }

    private static Set<Long> setOf(Long... values) {
        return new HashSet<>(Arrays.asList(values));
    }

}
