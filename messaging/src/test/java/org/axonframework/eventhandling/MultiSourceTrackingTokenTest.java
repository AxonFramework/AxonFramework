/*
 * Copyright (c) 2010-2022. Axon Framework
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link MultiSourceTrackingToken}.
 *
 * @author Greg Woods
 */
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
    void incompatibleToken() {
        assertThrows(IllegalArgumentException.class, () -> testSubject.covers(new GlobalSequenceTrackingToken(0)));
    }

    @Test
    void trackingTokenIsImmutable() {
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

        MultiSourceTrackingToken newMultiToken =
                (MultiSourceTrackingToken) testSubject.lowerBound(new MultiSourceTrackingToken(newTokens));

        assertEquals(new MultiSourceTrackingToken(expectedTokens), newMultiToken);
    }

    @Test
    void lowerBoundWithNullOnThisToken() {
        Map<String, TrackingToken> tokens = new HashMap<>();
        tokens.put("token1", new GlobalSequenceTrackingToken(2));
        tokens.put("token2", null);

        testSubject = new MultiSourceTrackingToken(tokens);

        Map<String, TrackingToken> otherTokens = new HashMap<>();
        otherTokens.put("token1", new GlobalSequenceTrackingToken(1));
        otherTokens.put("token2", new GlobalSequenceTrackingToken(2));

        Map<String, TrackingToken> expectedTokens = new HashMap<>();
        expectedTokens.put("token1", new GlobalSequenceTrackingToken(1));
        expectedTokens.put("token2", null);

        MultiSourceTrackingToken result =
                (MultiSourceTrackingToken) testSubject.lowerBound(new MultiSourceTrackingToken(otherTokens));

        assertEquals(new MultiSourceTrackingToken(expectedTokens), result);
    }

    @Test
    void lowerBoundWithNullOnOtherToken() {
        Map<String, TrackingToken> tokens = new HashMap<>();
        tokens.put("token1", new GlobalSequenceTrackingToken(2));
        tokens.put("token2", new GlobalSequenceTrackingToken(2));

        testSubject = new MultiSourceTrackingToken(tokens);

        Map<String, TrackingToken> otherTokens = new HashMap<>();
        otherTokens.put("token1", new GlobalSequenceTrackingToken(1));
        otherTokens.put("token2", null);

        Map<String, TrackingToken> expectedTokens = new HashMap<>();
        expectedTokens.put("token1", new GlobalSequenceTrackingToken(1));
        expectedTokens.put("token2", null);

        MultiSourceTrackingToken result =
                (MultiSourceTrackingToken) testSubject.lowerBound(new MultiSourceTrackingToken(otherTokens));

        assertEquals(new MultiSourceTrackingToken(expectedTokens), result);
    }

    @Test
    void lowerBoundMismatchTokens() {
        Map<String, TrackingToken> newTokens = new HashMap<>();
        newTokens.put("token1", new GlobalSequenceTrackingToken(1));
        newTokens.put("token3", new GlobalSequenceTrackingToken(2));

        assertThrows(IllegalArgumentException.class,
                     () -> testSubject.lowerBound(new MultiSourceTrackingToken(newTokens)));
    }

    @Test
    void lowerBoundDifferentNumberOfTokens() {
        Map<String, TrackingToken> newTokens = new HashMap<>();
        newTokens.put("token1", new GlobalSequenceTrackingToken(1));

        assertThrows(IllegalArgumentException.class,
                     () -> testSubject.lowerBound(new MultiSourceTrackingToken(newTokens)));
    }

    @Test
    void positionNotProvidedWhenUnderlyingTokensDontProvide() {
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

        MultiSourceTrackingToken newMultiToken =
                (MultiSourceTrackingToken) testSubject.upperBound(new MultiSourceTrackingToken(newTokens));

        assertEquals(new MultiSourceTrackingToken(newTokens), newMultiToken);
    }

    @Test
    void upperBoundWithNullInThisToken() {
        Map<String, TrackingToken> tokens = new HashMap<>();
        tokens.put("token1", new GlobalSequenceTrackingToken(0));
        tokens.put("token2", null);

        testSubject = new MultiSourceTrackingToken(tokens);
        Map<String, TrackingToken> otherTokens = new HashMap<>();

        otherTokens.put("token1", new GlobalSequenceTrackingToken(1));
        otherTokens.put("token2", new GlobalSequenceTrackingToken(2));

        MultiSourceTrackingToken result =
                (MultiSourceTrackingToken) testSubject.upperBound(new MultiSourceTrackingToken(otherTokens));

        assertEquals(new MultiSourceTrackingToken(otherTokens), result);
    }

    @Test
    void upperBoundWithNullInOtherToken() {
        Map<String, TrackingToken> expectedTokens = new HashMap<>();
        expectedTokens.put("token1", new GlobalSequenceTrackingToken(1));
        expectedTokens.put("token2", new GlobalSequenceTrackingToken(2));


        Map<String, TrackingToken> tokens = new HashMap<>();
        tokens.put("token1", new GlobalSequenceTrackingToken(0));
        tokens.put("token2", new GlobalSequenceTrackingToken(2));

        testSubject = new MultiSourceTrackingToken(tokens);
        Map<String, TrackingToken> otherTokens = new HashMap<>();

        otherTokens.put("token1", new GlobalSequenceTrackingToken(1));
        otherTokens.put("token2", null);

        MultiSourceTrackingToken result =
                (MultiSourceTrackingToken) testSubject.upperBound(new MultiSourceTrackingToken(otherTokens));

        assertEquals(new MultiSourceTrackingToken(expectedTokens), result);
    }

    @Test
    void upperBoundMismatchTokens() {
        Map<String, TrackingToken> newTokens = new HashMap<>();
        newTokens.put("token1", new GlobalSequenceTrackingToken(1));
        newTokens.put("token3", new GlobalSequenceTrackingToken(2));

        assertThrows(IllegalArgumentException.class,
                     () -> testSubject.upperBound(new MultiSourceTrackingToken(newTokens)));
    }

    @Test
    void upperBoundDifferentNumberOfTokens() {
        Map<String, TrackingToken> newTokens = new HashMap<>();
        newTokens.put("token1", new GlobalSequenceTrackingToken(1));

        assertThrows(IllegalArgumentException.class,
                     () -> testSubject.upperBound(new MultiSourceTrackingToken(newTokens)));
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
    void constituentTokenNotInitialized() {
        Map<String, TrackingToken> tokenMap = new HashMap<>();
        tokenMap.put("token1", null);
        tokenMap.put("token2", new GlobalSequenceTrackingToken(0));

        MultiSourceTrackingToken newMultiToken = new MultiSourceTrackingToken(tokenMap);

        assertNotEquals(newMultiToken, testSubject);
    }

    @Nested
    class Processed {

        @Test
        void processedReturnsTrueWhenAllConstituentsProcessed() {
            Map<String, TrackingToken> tokenMap = new HashMap<>();
            tokenMap.put("token1", new GlobalSequenceTrackingToken(10));
            tokenMap.put("token2", new GlobalSequenceTrackingToken(5));
            MultiSourceTrackingToken token = new MultiSourceTrackingToken(tokenMap);

            Map<String, TrackingToken> otherMap = new HashMap<>();
            otherMap.put("token1", new GlobalSequenceTrackingToken(5));
            otherMap.put("token2", new GlobalSequenceTrackingToken(3));
            MultiSourceTrackingToken other = new MultiSourceTrackingToken(otherMap);

            assertTrue(token.processed(other));
        }

        @Test
        void processedReturnsFalseWhenAnyConstituentNotProcessed() {
            Map<String, TrackingToken> tokenMap = new HashMap<>();
            tokenMap.put("token1", new GlobalSequenceTrackingToken(5));
            tokenMap.put("token2", new GlobalSequenceTrackingToken(5));
            MultiSourceTrackingToken token = new MultiSourceTrackingToken(tokenMap);

            Map<String, TrackingToken> otherMap = new HashMap<>();
            otherMap.put("token1", new GlobalSequenceTrackingToken(10)); // Beyond token1's range
            otherMap.put("token2", new GlobalSequenceTrackingToken(3));
            MultiSourceTrackingToken other = new MultiSourceTrackingToken(otherMap);

            assertFalse(token.processed(other));
        }

        @Test
        void processedWithGapAwareConstituentsIgnoresGapSetDifferences() {
            Map<String, TrackingToken> tokenMap = new HashMap<>();
            // token1 has gaps [3, 4] below the other's index of 8
            tokenMap.put("token1", GapAwareTrackingToken.newInstance(10, java.util.Arrays.asList(3L, 4L)));
            tokenMap.put("token2", new GlobalSequenceTrackingToken(5));
            MultiSourceTrackingToken token = new MultiSourceTrackingToken(tokenMap);

            Map<String, TrackingToken> otherMap = new HashMap<>();
            // other has no gaps at index 8, but token1 has gaps [3, 4] below index 8
            // covers() would fail because other.gaps ({}) doesn't contain token.gaps.headSet(8) = {3, 4}
            otherMap.put("token1", GapAwareTrackingToken.newInstance(8, Collections.emptyList()));
            otherMap.put("token2", new GlobalSequenceTrackingToken(3));
            MultiSourceTrackingToken other = new MultiSourceTrackingToken(otherMap);

            // covers() fails due to gap set incompatibility
            assertFalse(token.covers(other));
            // processed() succeeds because position 8 was processed by token1 (8 <= 10 and not in gaps)
            assertTrue(token.processed(other));
        }

        @Test
        void processedReturnsFalseWhenConstituentPositionIsInGap() {
            Map<String, TrackingToken> tokenMap = new HashMap<>();
            tokenMap.put("token1", GapAwareTrackingToken.newInstance(10, Collections.singletonList(7L)));
            tokenMap.put("token2", new GlobalSequenceTrackingToken(5));
            MultiSourceTrackingToken token = new MultiSourceTrackingToken(tokenMap);

            Map<String, TrackingToken> otherMap = new HashMap<>();
            // Position 7 is in token1's gaps - never processed
            otherMap.put("token1", GapAwareTrackingToken.newInstance(7, Collections.emptyList()));
            otherMap.put("token2", new GlobalSequenceTrackingToken(3));
            MultiSourceTrackingToken other = new MultiSourceTrackingToken(otherMap);

            assertFalse(token.processed(other));
        }

        @Test
        void processedWithNullConstituents() {
            Map<String, TrackingToken> tokenMap = new HashMap<>();
            tokenMap.put("token1", new GlobalSequenceTrackingToken(10));
            tokenMap.put("token2", null);
            MultiSourceTrackingToken token = new MultiSourceTrackingToken(tokenMap);

            Map<String, TrackingToken> otherMap = new HashMap<>();
            otherMap.put("token1", new GlobalSequenceTrackingToken(5));
            otherMap.put("token2", null);
            MultiSourceTrackingToken other = new MultiSourceTrackingToken(otherMap);

            // Both have null for token2, should be treated as processed
            assertTrue(token.processed(other));
        }

        @Test
        void processedReturnsFalseWhenThisHasNullButOtherHasValue() {
            Map<String, TrackingToken> tokenMap = new HashMap<>();
            tokenMap.put("token1", new GlobalSequenceTrackingToken(10));
            tokenMap.put("token2", null);
            MultiSourceTrackingToken token = new MultiSourceTrackingToken(tokenMap);

            Map<String, TrackingToken> otherMap = new HashMap<>();
            otherMap.put("token1", new GlobalSequenceTrackingToken(5));
            otherMap.put("token2", new GlobalSequenceTrackingToken(0)); // token has null, other has value
            MultiSourceTrackingToken other = new MultiSourceTrackingToken(otherMap);

            assertFalse(token.processed(other));
        }

        @Test
        void processedThrowsExceptionForIncompatibleToken() {
            assertThrows(IllegalArgumentException.class,
                    () -> testSubject.processed(new GlobalSequenceTrackingToken(0)));
        }

        @Test
        void processedThrowsExceptionForMismatchedKeys() {
            Map<String, TrackingToken> otherMap = new HashMap<>();
            otherMap.put("token1", new GlobalSequenceTrackingToken(0));
            otherMap.put("token3", new GlobalSequenceTrackingToken(0)); // Different key

            assertThrows(IllegalArgumentException.class,
                    () -> testSubject.processed(new MultiSourceTrackingToken(otherMap)));
        }

    }
}
