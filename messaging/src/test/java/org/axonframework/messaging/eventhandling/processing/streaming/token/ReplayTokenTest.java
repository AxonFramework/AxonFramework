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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;

import static java.util.Collections.emptySet;
import static org.junit.jupiter.api.Assertions.*;

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
        ReplayToken testSubject = (ReplayToken) ReplayToken.createReplayToken(innerToken);
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
        TrackingToken replayToken = ReplayToken.createReplayToken(innerToken);
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
    @DisplayName("Initial startup replay behavior")
    class InitialStartupReplayBehavior {

        /**
         * Simulates AxonServer behavior where firstToken() returns position 0.
         * Current default: es.firstToken(null).thenApply(ReplayToken::createReplayToken)
         */
        @Test
        void axonServerFirstToken_firstEventAtPosition0_isConsideredReplay() {
            // Given: AxonServer returns firstToken at position 0
            TrackingToken tokenAtReset = new GlobalSequenceTrackingToken(0);
            ReplayToken initialToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset);

            // When: first event arrives at position 0
            TrackingToken afterFirstEvent = initialToken.advancedTo(new GlobalSequenceTrackingToken(0));

            // Then: Current behavior - first event IS considered replay (arguably a quirk)
            assertTrue(ReplayToken.isReplay(afterFirstEvent),
                       "With AxonServer (tokenAtReset=0), first event at position 0 is marked as replay");
        }

        /**
         * Simulates JPA behavior where firstToken() returns position -1.
         */
        @Test
        void jpaFirstToken_firstEventAtPosition0_isNotConsideredReplay() {
            // Given: JPA returns firstToken at position -1
            TrackingToken tokenAtReset = new GlobalSequenceTrackingToken(-1);
            ReplayToken initialToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset);

            // When: first event arrives at position 0
            TrackingToken afterFirstEvent = initialToken.advancedTo(new GlobalSequenceTrackingToken(0));

            // Then: First event is NOT considered replay
            assertFalse(ReplayToken.isReplay(afterFirstEvent),
                        "With JPA (tokenAtReset=-1), first event at position 0 is NOT marked as replay");
        }

        /**
         * Proposed behavior using TrackingToken.FIRST.
         * Proposed: completedFuture(ReplayToken.createReplayToken(TrackingToken.FIRST))
         * <p>
         * CURRENT STATUS: This test documents that GlobalSequenceTrackingToken.covers() does NOT
         * handle FirstTrackingToken, throwing IllegalArgumentException instead.
         * For the proposition to work, GlobalSequenceTrackingToken needs to be updated.
         */
        @Test
        void trackingTokenFirst_firstEventAtPosition0_currentlyThrowsException() {
            // Given: Using TrackingToken.FIRST as tokenAtReset
            ReplayToken initialToken = (ReplayToken) ReplayToken.createReplayToken(TrackingToken.FIRST);

            // When/Then: GlobalSequenceTrackingToken.covers() doesn't handle FirstTrackingToken
            assertThrows(IllegalArgumentException.class,
                         () -> initialToken.advancedTo(new GlobalSequenceTrackingToken(0)),
                         "GlobalSequenceTrackingToken.covers() needs to be updated to handle FirstTrackingToken");
        }

        /**
         * Documents the inconsistency between AxonServer and JPA with current implementation.
         */
        @Test
        void currentBehavior_isInconsistentAcrossImplementations() {
            // Given: Different firstToken values from different implementations
            TrackingToken axonServerFirstToken = new GlobalSequenceTrackingToken(0);
            TrackingToken jpaFirstToken = new GlobalSequenceTrackingToken(-1);

            ReplayToken axonServerInitial = (ReplayToken) ReplayToken.createReplayToken(axonServerFirstToken);
            ReplayToken jpaInitial = (ReplayToken) ReplayToken.createReplayToken(jpaFirstToken);

            // When: first event arrives at position 0
            TrackingToken firstEvent = new GlobalSequenceTrackingToken(0);
            boolean axonServerReplay = ReplayToken.isReplay(axonServerInitial.advancedTo(firstEvent));
            boolean jpaReplay = ReplayToken.isReplay(jpaInitial.advancedTo(firstEvent));

            // Then: Inconsistent behavior!
            assertNotEquals(axonServerReplay, jpaReplay,
                            "AxonServer and JPA have different replay behavior for first event - this is inconsistent");
        }

        /**
         * FUTURE TEST: Using TrackingToken.FIRST would provide consistent behavior.
         * <p>
         * This test is disabled until GlobalSequenceTrackingToken.covers() is updated
         * to handle FirstTrackingToken. When fixed, this test should pass.
         */
        @Test
        @Disabled("Requires GlobalSequenceTrackingToken.covers() to handle FirstTrackingToken")
        void trackingTokenFirst_providesConsistentBehavior_whenFixed() {
            // Given: Using TrackingToken.FIRST (proposed approach)
            ReplayToken initialToken = (ReplayToken) ReplayToken.createReplayToken(TrackingToken.FIRST);

            // When: processing events at various positions
            TrackingToken event0 = new GlobalSequenceTrackingToken(0);
            TrackingToken event1 = new GlobalSequenceTrackingToken(1);
            TrackingToken event100 = new GlobalSequenceTrackingToken(100);

            // Then: None are considered replay on initial startup
            assertFalse(ReplayToken.isReplay(initialToken.advancedTo(event0)),
                        "Event at position 0 should not be replay");
            assertFalse(ReplayToken.isReplay(initialToken.advancedTo(event1)),
                        "Event at position 1 should not be replay");
            assertFalse(ReplayToken.isReplay(initialToken.advancedTo(event100)),
                        "Event at position 100 should not be replay");
        }
    }
}
