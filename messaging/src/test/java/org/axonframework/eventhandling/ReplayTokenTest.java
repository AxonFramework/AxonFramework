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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;

import static java.util.Collections.emptySet;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
