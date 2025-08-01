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

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventstreaming.EventCriteria;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link NoAppendCondition}.
 *
 * @author Steven van Beelen
 */
class NoAppendConditionTest {

    @Test
    void consistencyMarkerFixedToLongMax() {
        assertEquals(ConsistencyMarker.INFINITY, AppendCondition.none().consistencyMarker());
    }

    @Test
    void criteriaFixedToNoCriteria() {
        assertEquals(EventCriteria.havingAnyTag(), AppendCondition.none().criteria());
    }

    @Test
    void withMarkerThrowsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> AppendCondition.none().withMarker(mock()));
    }
}