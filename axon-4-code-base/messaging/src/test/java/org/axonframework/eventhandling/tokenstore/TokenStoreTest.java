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

package org.axonframework.eventhandling.tokenstore;

import org.axonframework.eventhandling.Segment;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TokenStoreTest {

    private final TokenStore tokenStore = spy(TokenStore.class);

    @Test
    void fetchAvailableSegments() {
        when(tokenStore.fetchSegments("")).thenReturn(new int[]{0, 1, 2, 3});
        List<Segment> availableSegments = tokenStore.fetchAvailableSegments("");

        assertEquals(4, availableSegments.size());

        Segment segment0 = availableSegments.get(0);
        assertEquals(0, segment0.getSegmentId());
        assertEquals(3, segment0.getMask());

        Segment segment1 = availableSegments.get(1);
        assertEquals(1, segment1.getSegmentId());
        assertEquals(3, segment1.getMask());

        Segment segment2 = availableSegments.get(2);
        assertEquals(2, segment2.getSegmentId());
        assertEquals(3, segment2.getMask());

        Segment segment3 = availableSegments.get(3);
        assertEquals(3, segment3.getSegmentId());
        assertEquals(3, segment3.getMask());
    }
}