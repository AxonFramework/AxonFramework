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

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptySet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Nested
    class ReplayTokenFromGapAwareTrackingToken {

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
         * Test with MultiSourceTrackingToken scenario as described in the issue.
         * <p>
         * During reset:
         * - A: Index 6, Gaps [1]
         * - B: Index 4, Gaps [1]
         * <p>
         * During replay the new token:
         * - A: Index 2, Gaps []
         * - B: Index 1, Gaps []
         * <p>
         * Due to the missing gaps in the new token, ReplayToken incorrectly determines
         * that lastMessageWasReplay should be false.
         */
        @Test
        void advancedToShouldRemainInReplayWithMultiSourceTrackingTokenWhenGapsAreMissing() {
            // Setup MultiSourceTrackingToken at reset with gaps
            Map<String, TrackingToken> tokensAtReset = new HashMap<>();
            tokensAtReset.put("A", GapAwareTrackingToken.newInstance(6, Collections.singleton(1L)));
            tokensAtReset.put("B", GapAwareTrackingToken.newInstance(4, Collections.singleton(1L)));
            MultiSourceTrackingToken multiTokenAtReset = new MultiSourceTrackingToken(tokensAtReset);

            // Create replay token starting from the beginning
            TrackingToken replayToken = ReplayToken.createReplayToken(multiTokenAtReset, null);
            assertTrue(replayToken instanceof ReplayToken);

            // Simulate advancing during replay with a token that has no gaps
            Map<String, TrackingToken> newTokens = new HashMap<>();
            newTokens.put("A", GapAwareTrackingToken.newInstance(2, emptySet()));
            newTokens.put("B", GapAwareTrackingToken.newInstance(1, emptySet()));
            MultiSourceTrackingToken newMultiToken = new MultiSourceTrackingToken(newTokens);

            TrackingToken advancedToken = ((ReplayToken) replayToken).advancedTo(newMultiToken);

            // The token should still indicate replay is in progress
            assertTrue(advancedToken instanceof ReplayToken,
                       "Should still be a ReplayToken since we haven't caught up to reset position");
            assertTrue(ReplayToken.isReplay(advancedToken),
                       "Should still be in replay mode - events at index 2/1 were already processed before reset at index 6/4");
        }

        /**
         * Verifies that when the newToken eventually catches up to and covers the tokenAtReset,
         * the replay mode is correctly exited.
         */
        @Test
        void advancedToShouldExitReplayWhenNewTokenCoversTokenAtReset() {
            GapAwareTrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(6, Collections.singleton(1L));

            TrackingToken replayToken = ReplayToken.createReplayToken(tokenAtReset, null);

            // Advance to a token that fully covers the reset position (index 7 > index 6, no relevant gaps)
            GapAwareTrackingToken coveringToken = GapAwareTrackingToken.newInstance(7, emptySet());
            TrackingToken advancedToken = ((ReplayToken) replayToken).advancedTo(coveringToken);

            // Should no longer be in replay mode
            assertFalse(ReplayToken.isReplay(advancedToken),
                        "Should have exited replay mode since index 7 covers index 6");
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
         * Tests that when newToken has same index but filled a gap that tokenAtReset had,
         * the current event at that index is still considered a replay (it was processed before).
         * The gap-filling events are handled separately during their processing.
         */
        @Test
        void advancedToShouldRemainInReplayWhenSameIndexButGapWasFilled() {
            // Token at reset: index 6 with gap at position 1
            GapAwareTrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(6, Collections.singleton(1L));

            TrackingToken replayToken = ReplayToken.createReplayToken(tokenAtReset, null);

            // Advance to same index but WITHOUT the gap - the gap was filled during replay
            // The current event at index 6 was still processed before reset, so it's a replay
            GapAwareTrackingToken filledGapToken = GapAwareTrackingToken.newInstance(6, emptySet());
            TrackingToken advancedToken = ((ReplayToken) replayToken).advancedTo(filledGapToken);

            // Should still be in replay - the event at index 6 was processed before reset
            assertTrue(advancedToken instanceof ReplayToken,
                       "Should still be a ReplayToken at the same index position");
            assertTrue(ReplayToken.isReplay(advancedToken),
                       "Event at index 6 was processed before reset, so it's still a replay");
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
         * replay should still be in progress because we processed the last event. Then Index 7 will be without. 
         */
        @Test
        void advancedToShouldRemainInReplayWhenNewTokenHasSameIndexButTokenAtResetHasGaps() {
            // tokenAtReset: Index 6, Gaps [2]
            // newToken during replay: Index 6, Gaps []
            //
            // The newToken has the same index (6) as tokenAtReset, but tokenAtReset
            // has a gap at position 2 that the newToken doesn't have.
            // This means the event at position 2 was never processed before reset,
            // so during replay we're seeing it for the first time - it should be
            // treated as a replay event.
            GapAwareTrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(6, Collections.singleton(2L));
            GapAwareTrackingToken newToken = GapAwareTrackingToken.newInstance(6, emptySet());

            TrackingToken replayToken = ReplayToken.createReplayToken(tokenAtReset, null);
            TrackingToken advancedToken = ((ReplayToken) replayToken).advancedTo(newToken);

            // The newToken covers tokenAtReset (same index, no gaps vs gap at 2)
            // so it should exit replay mode - this is the correct behavior
            // because index 6 with no gaps means we've processed everything up to 6
            assertFalse(ReplayToken.isReplay(advancedToken),
                    "Should have exited replay mode since newToken (index 6, no gaps) covers tokenAtReset (index 6, gap at 2)");
        }
    }
}
