/*
 * Copyright (c) 2010-2019. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MultiSourceTrackingTokenTest {

    private MultiSourceTrackingToken testSubject;

    @BeforeEach
    void setUp() {
        Map<String, TrackingToken> tokenMap = new HashMap<>();
        tokenMap.put("token1", new GlobalSequenceTrackingToken(0));
        tokenMap.put("token2", new GlobalSequenceTrackingToken(0));

        testSubject = new MultiSourceTrackingToken(tokenMap);
    }

    @Test
    void testIncompatibleToken() {
        assertThrows(IllegalArgumentException.class, () -> testSubject.covers(new GlobalSequenceTrackingToken(0)));
    }

    @Test
    void testTrackingTokenIsImmutable() {
        MultiSourceTrackingToken newToken = testSubject.advancedTo("token1", new GlobalSequenceTrackingToken(1));

        assertEquals(new GlobalSequenceTrackingToken(0), testSubject.getTokenForStream("token1"));
        assertEquals(new GlobalSequenceTrackingToken(0), testSubject.getTokenForStream("token2"));
        assertEquals(new GlobalSequenceTrackingToken(1), newToken.getTokenForStream("token1"));
        assertEquals(new GlobalSequenceTrackingToken(0), newToken.getTokenForStream("token2"));
    }

    @Test
    void lowerBound() {
        Map<String, TrackingToken> newTokens = new HashMap<>();
        newTokens.put("token1", new GlobalSequenceTrackingToken(1));
        newTokens.put("token2", new GlobalSequenceTrackingToken(2));

        Map<String, TrackingToken> expectedTokens = new HashMap<>();
        expectedTokens.put("token1", new GlobalSequenceTrackingToken(0));
        expectedTokens.put("token2", new GlobalSequenceTrackingToken(0));

        MultiSourceTrackingToken newMultiToken = (MultiSourceTrackingToken) testSubject
                .lowerBound(new MultiSourceTrackingToken(newTokens));

        assertEquals(new MultiSourceTrackingToken(expectedTokens), newMultiToken);
    }
    @Test
    void lowerBoundWithNull() {
        Map<String, TrackingToken> tokenMap = new HashMap<>();
        tokenMap.put("token1", new GlobalSequenceTrackingToken(2));
        tokenMap.put("token2", null);

        testSubject = new MultiSourceTrackingToken(tokenMap);

        Map<String, TrackingToken> newTokens = new HashMap<>();
        newTokens.put("token1", new GlobalSequenceTrackingToken(1));
        newTokens.put("token2", new GlobalSequenceTrackingToken(2));

        Map<String, TrackingToken> expectedTokens = new HashMap<>();
        expectedTokens.put("token1", new GlobalSequenceTrackingToken(1));
        expectedTokens.put("token2", null);

        MultiSourceTrackingToken newMultiToken = (MultiSourceTrackingToken) testSubject
                .lowerBound(new MultiSourceTrackingToken(newTokens));

        assertEquals(new MultiSourceTrackingToken(expectedTokens), newMultiToken);
    }

    @Test
    void lowerBoundMismatchTokens() {
        Map<String, TrackingToken> newTokens = new HashMap<>();
        newTokens.put("token1", new GlobalSequenceTrackingToken(1));
        newTokens.put("token3", new GlobalSequenceTrackingToken(2));

        assertThrows(IllegalArgumentException.class, () -> testSubject.lowerBound(new MultiSourceTrackingToken(newTokens)));
    }

    @Test
    void lowerBoundDifferentNumberOfTokens() {
        Map<String, TrackingToken> newTokens = new HashMap<>();
        newTokens.put("token1", new GlobalSequenceTrackingToken(1));

        assertThrows(IllegalArgumentException.class, () -> testSubject.lowerBound(new MultiSourceTrackingToken(newTokens)));
    }

    @Test
    void testPositionNotProvidedWhenUnderlyingTokensDontProvide() {
        TrackingToken trackingToken = mock(TrackingToken.class);
        when(trackingToken.position()).thenReturn(OptionalLong.empty());
        testSubject = new MultiSourceTrackingToken(Collections.singletonMap("key", trackingToken));

        assertFalse(testSubject.position().isPresent());
    }

    @Test
    void upperBound() {
        Map<String, TrackingToken> newTokens = new HashMap<>();

        newTokens.put("token1", new GlobalSequenceTrackingToken(1));
        newTokens.put("token2", new GlobalSequenceTrackingToken(2));

        MultiSourceTrackingToken newMultiToken = (MultiSourceTrackingToken) testSubject
                .upperBound(new MultiSourceTrackingToken(newTokens));

        assertEquals(new MultiSourceTrackingToken(newTokens), newMultiToken);
    }

    @Test
    void upperBoundWithNull() {
        Map<String, TrackingToken> tokenMap = new HashMap<>();
        tokenMap.put("token1", new GlobalSequenceTrackingToken(0));
        tokenMap.put("token2", null);

        testSubject = new MultiSourceTrackingToken(tokenMap);
        Map<String, TrackingToken> newTokens = new HashMap<>();

        newTokens.put("token1", new GlobalSequenceTrackingToken(1));
        newTokens.put("token2", new GlobalSequenceTrackingToken(2));

        MultiSourceTrackingToken newMultiToken = (MultiSourceTrackingToken) testSubject
                .upperBound(new MultiSourceTrackingToken(newTokens));

        assertEquals(new MultiSourceTrackingToken(newTokens), newMultiToken);
    }

    @Test
    void upperBoundMismatchTokens() {
        Map<String, TrackingToken> newTokens = new HashMap<>();
        newTokens.put("token1", new GlobalSequenceTrackingToken(1));
        newTokens.put("token3", new GlobalSequenceTrackingToken(2));

        assertThrows(IllegalArgumentException.class, () -> testSubject.upperBound(new MultiSourceTrackingToken(newTokens)));
    }

    @Test
    void upperBoundDifferentNumberOfTokens() {
        Map<String, TrackingToken> newTokens = new HashMap<>();
        newTokens.put("token1", new GlobalSequenceTrackingToken(1));

        assertThrows(IllegalArgumentException.class, () -> testSubject.upperBound(new MultiSourceTrackingToken(newTokens)));
    }

    @Test
    void covers() {
        Map<String, TrackingToken> newTokens = new HashMap<>();
        newTokens.put("token1", new GlobalSequenceTrackingToken(0));
        newTokens.put("token2", new GlobalSequenceTrackingToken(0));

        assertTrue(testSubject.covers(new MultiSourceTrackingToken(newTokens)));
    }

    @Test
    void doesNotCover() {
        Map<String, TrackingToken> newTokens = new HashMap<>();
        newTokens.put("token1", new GlobalSequenceTrackingToken(1));
        newTokens.put("token2", new GlobalSequenceTrackingToken(0));

        assertFalse(testSubject.covers(new MultiSourceTrackingToken(newTokens)));
    }

    @Test
    void coversNullConstituents() {
        Map<String, TrackingToken> newTokens = new HashMap<>();
        newTokens.put("token1", new GlobalSequenceTrackingToken(0));
        newTokens.put("token2", null);
        MultiSourceTrackingToken tokenWithNullConstituent = new MultiSourceTrackingToken(newTokens);

        assertTrue(tokenWithNullConstituent.covers(tokenWithNullConstituent));
        assertTrue(testSubject.covers(tokenWithNullConstituent));
        assertFalse(tokenWithNullConstituent.covers(testSubject));
    }


    @Test
    void coversMismatchTokens() {
        Map<String, TrackingToken> newTokens = new HashMap<>();
        newTokens.put("token1", new GlobalSequenceTrackingToken(1));
        newTokens.put("token3", new GlobalSequenceTrackingToken(2));

        assertThrows(IllegalArgumentException.class, () -> testSubject.covers(new MultiSourceTrackingToken(newTokens)));
    }

    @Test
    void coversDifferentNumberOfTokens() {
        Map<String, TrackingToken> newTokens = new HashMap<>();
        newTokens.put("token1", new GlobalSequenceTrackingToken(1));

        assertThrows(IllegalArgumentException.class, () -> testSubject.covers(new MultiSourceTrackingToken(newTokens)));
    }


    @Test
    void advancedTo() {
        MultiSourceTrackingToken result = testSubject.advancedTo("token1", new GlobalSequenceTrackingToken(4));

        assertEquals(new GlobalSequenceTrackingToken(4), result.getTokenForStream("token1"));
        //other token remains unchanged
        assertEquals(new GlobalSequenceTrackingToken(0), result.getTokenForStream("token2"));
    }

    @Test
    void getTokenForStream() {
        assertEquals(new GlobalSequenceTrackingToken(0), testSubject.getTokenForStream("token1"));
    }

    @Test
    void hasPosition() {
        assertTrue(testSubject.position().isPresent());
        assertEquals(0L, testSubject.position().getAsLong());
    }

    @Test
    void positionWithNullValue() {
        Map<String, TrackingToken> tokenMap = new HashMap<>();
        tokenMap.put("token1", new GlobalSequenceTrackingToken(10));
        tokenMap.put("token2", null);

        MultiSourceTrackingToken newMultiToken = new MultiSourceTrackingToken(tokenMap);

        assertEquals(10, newMultiToken.position().orElse(-1));
    }

    @Test
    void positionWithNullValue2() {
        Map<String, TrackingToken> tokenMap = new HashMap<>();
        tokenMap.put("token1", null);
        tokenMap.put("token2", new GlobalSequenceTrackingToken(10));

        MultiSourceTrackingToken newMultiToken = new MultiSourceTrackingToken(tokenMap);

        assertEquals(10, newMultiToken.position().orElse(-1));
    }

    @Test
    void equals() {
        Map<String, TrackingToken> tokenMap = new HashMap<>();
        tokenMap.put("token1", new GlobalSequenceTrackingToken(0));
        tokenMap.put("token2", new GlobalSequenceTrackingToken(0));

        MultiSourceTrackingToken newMultiToken = new MultiSourceTrackingToken(tokenMap);

        assertEquals(newMultiToken, testSubject);
    }

    @Test
    void notEquals() {
        Map<String, TrackingToken> tokenMap = new HashMap<>();
        tokenMap.put("token1", new GlobalSequenceTrackingToken(1));
        tokenMap.put("token2", new GlobalSequenceTrackingToken(0));

        MultiSourceTrackingToken newMultiToken = new MultiSourceTrackingToken(tokenMap);

        assertNotEquals(newMultiToken, testSubject);
    }

    @Test
    void constituantTokenNotInitialized() {
        Map<String, TrackingToken> tokenMap = new HashMap<>();
        tokenMap.put("token1", null);
        tokenMap.put("token2", new GlobalSequenceTrackingToken(0));

        MultiSourceTrackingToken newMultiToken = new MultiSourceTrackingToken(tokenMap);

        assertNotEquals(newMultiToken, testSubject);
    }
}
