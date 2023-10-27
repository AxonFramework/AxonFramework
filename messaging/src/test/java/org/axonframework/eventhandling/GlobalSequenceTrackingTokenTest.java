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

import static org.junit.jupiter.api.Assertions.*;

class GlobalSequenceTrackingTokenTest {

    @Test
    void upperBound() {
        GlobalSequenceTrackingToken token1 = new GlobalSequenceTrackingToken(1L);
        GlobalSequenceTrackingToken token2 = new GlobalSequenceTrackingToken(2L);

        assertSame(token2, token1.upperBound(token2));
        assertSame(token2, token2.upperBound(token1));
        assertSame(token2, token2.upperBound(token2));
    }

    @Test
    void lowerBound() {
        GlobalSequenceTrackingToken token1 = new GlobalSequenceTrackingToken(1L);
        GlobalSequenceTrackingToken token2 = new GlobalSequenceTrackingToken(2L);

        assertSame(token1, token1.lowerBound(token2));
        assertSame(token1, token2.lowerBound(token1));
        assertSame(token2, token2.lowerBound(token2));
    }

    @Test
    void covers() {
        GlobalSequenceTrackingToken token1 = new GlobalSequenceTrackingToken(1L);
        GlobalSequenceTrackingToken token2 = new GlobalSequenceTrackingToken(2L);

        assertFalse(token1.covers(token2));
        assertTrue(token2.covers(token1));
        assertTrue(token2.covers(token2));
        assertTrue(token2.covers(null));
    }

    @Test
    void position() {
        GlobalSequenceTrackingToken token = new GlobalSequenceTrackingToken(1L);

        assertEquals(1L, token.position().getAsLong());
    }
}
