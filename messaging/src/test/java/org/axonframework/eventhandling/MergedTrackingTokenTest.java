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

import org.axonframework.eventhandling.processors.streaming.token.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.processors.streaming.token.MergedTrackingToken;
import org.axonframework.eventhandling.processors.streaming.token.ReplayToken;
import org.axonframework.eventhandling.processors.streaming.token.TrackingToken;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MergedTrackingTokenTest {

    @Test
    void mergedTokenCoversOriginal() {
        MergedTrackingToken testSubject = new MergedTrackingToken(token(1), token(3));

        assertTrue(testSubject.covers(token(1)));
        assertFalse(testSubject.covers(token(2)));
        assertFalse(testSubject.covers(token(3)));
    }

    @Test
    void upperBound() {
        MergedTrackingToken testSubject = new MergedTrackingToken(token(1), token(3));

        assertEquals(new MergedTrackingToken(token(2), token(3)), testSubject.upperBound(token(2)));
        assertEquals(token(3), testSubject.upperBound(token(3)));
    }

    @Test
    void lowerBound() {
        MergedTrackingToken testSubject = new MergedTrackingToken(token(1), token(3));

        assertEquals(new MergedTrackingToken(token(1), token(2)), testSubject.lowerBound(token(2)));
        assertEquals(token(1), testSubject.lowerBound(token(1)));
    }

    @Test
    void unwrapToLowerBound() {
        assertEquals(token(1), new MergedTrackingToken(new MergedTrackingToken(token(1), token(5)), token(3)).lowerBound());
        assertEquals(token(1), new MergedTrackingToken(token(1), new MergedTrackingToken(token(5), token(3))).lowerBound());
    }

    @Test
    void upperBound_NestedTokens() {
        MergedTrackingToken testSubject = new MergedTrackingToken(new MergedTrackingToken(token(1), token(3)), token(5));

        assertEquals(new MergedTrackingToken(token(4), token(5)), testSubject.upperBound(token(4)));
        assertEquals(new MergedTrackingToken(new MergedTrackingToken(token(3), token(3)), token(5)), testSubject.upperBound(token(3)));
        assertEquals(new MergedTrackingToken(new MergedTrackingToken(token(2), token(3)), token(5)), testSubject.upperBound(token(2)));
    }

    @Test
    void lowerBound_NestedTokens() {
        MergedTrackingToken testSubject = new MergedTrackingToken(new MergedTrackingToken(token(1), token(5)), token(3));

        assertEquals(new MergedTrackingToken(new MergedTrackingToken(token(1), token(3)), token(3)), testSubject.lowerBound(token(3)));
        assertEquals(new MergedTrackingToken(new MergedTrackingToken(token(1), token(2)), token(2)), testSubject.lowerBound(token(2)));
        assertEquals(token(1), testSubject.lowerBound(token(1)));
    }

    @Test
    void advanceWithNestedReplayToken() {
        TrackingToken incomingMessage = new GlobalSequenceTrackingToken(0);

        MergedTrackingToken currentToken = new MergedTrackingToken(
                new GlobalSequenceTrackingToken(9),
                ReplayToken.createReplayToken(new GlobalSequenceTrackingToken(9), new GlobalSequenceTrackingToken(-1))
        );

        TrackingToken advancedToken = currentToken.advancedTo(incomingMessage);

        assertTrue(advancedToken instanceof MergedTrackingToken);
        MergedTrackingToken actual = (MergedTrackingToken) advancedToken;
        // this token should not have been modified
        assertTrue(actual.lowerSegmentToken() instanceof GlobalSequenceTrackingToken);
        // this token should not have been modified
        assertTrue(
                actual.upperSegmentToken() instanceof ReplayToken, "Wrong upper segment: " + actual.upperSegmentToken()
        );
    }

    @Test
    void unwrapPrefersLastAdvancedToken_LowerSegmentAdvanced() {
        TrackingToken merged = new MergedTrackingToken(token(1), token(3)).advancedTo(token(2));
        assertTrue(merged instanceof MergedTrackingToken);
        assertEquals(token(2), WrappedToken.unwrap(merged, GlobalSequenceTrackingToken.class).orElse(null));
    }

    @Test
    void unwrapPrefersLastAdvancedToken_UpperSegmentAdvanced() {
        TrackingToken merged = new MergedTrackingToken(token(3), token(1)).advancedTo(token(2));
        assertTrue(merged instanceof MergedTrackingToken);
        assertEquals(token(2), WrappedToken.unwrap(merged, GlobalSequenceTrackingToken.class).orElse(null));
    }

    @Test
    void unwrapPrefersLastAdvancedToken_NeitherSegmentAdvanced() {
        TrackingToken merged = new MergedTrackingToken(token(3), token(3)).advancedTo(token(2));
        assertTrue(merged instanceof MergedTrackingToken);
        assertEquals(token(3), WrappedToken.unwrap(merged, GlobalSequenceTrackingToken.class).orElse(null));
    }

    @Test
    void unwrapPrefersLastAdvancedToken_NeitherSegmentAdvanced_OnlyLowerIsCandidate() {
        MergedTrackingToken merged = new MergedTrackingToken(token(3), mock(TrackingToken.class));
        assertEquals(token(3), merged.unwrap(GlobalSequenceTrackingToken.class).orElse(null));
    }

    @Test
    void unwrapPrefersLastAdvancedToken_NeitherSegmentAdvanced_OnlyUpperIsCandidate() {
        MergedTrackingToken merged = new MergedTrackingToken(mock(TrackingToken.class), token(3));
        assertEquals(token(3), merged.unwrap(GlobalSequenceTrackingToken.class).orElse(null));
    }

    @Test
    void positionReportsLowestSegment() {
        MergedTrackingToken merged = new MergedTrackingToken(token(4), token(3));
        assertEquals(3L, merged.position().orElse(0L));
    }

    @Test
    void positionIsNotPresent() {
        MergedTrackingToken merged = new MergedTrackingToken(mock(TrackingToken.class), token(3));
        assertFalse(merged.position().isPresent());
    }

    @Test
    void isMergeInProgress() {
        MergedTrackingToken testSubject = new MergedTrackingToken(token(1), token(3));
        assertTrue(MergedTrackingToken.isMergeInProgress(testSubject));
    }

    @Test
    void mergePosition() {
        MergedTrackingToken testSubject = new MergedTrackingToken(new MergedTrackingToken(token(1), token(3)), token(5));

        assertTrue(MergedTrackingToken.mergePosition(testSubject).isPresent());
        assertEquals(MergedTrackingToken.mergePosition(testSubject).getAsLong(), 5);
    }

    @Test
    void coversWithNestedMergedNullTokens() {
        MergedTrackingToken testSubject = new MergedTrackingToken(new MergedTrackingToken(null, null), null);

        assertFalse(testSubject.covers(token(0)));
        assertTrue(testSubject.covers(null));

        GlobalSequenceTrackingToken advance = token(1);
        assertSame(advance, testSubject.advancedTo(advance));
    }

    @Test
    void coversWithNullTokens() {
        MergedTrackingToken testSubject = new MergedTrackingToken(null, null);

        assertFalse(testSubject.covers(token(0)));
        assertTrue(testSubject.covers(null));

        GlobalSequenceTrackingToken advance = token(1);
        assertSame(advance, testSubject.advancedTo(advance));
    }

    private GlobalSequenceTrackingToken token(int sequence) {
        return new GlobalSequenceTrackingToken(sequence);
    }
}
