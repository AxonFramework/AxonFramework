/*
 * Copyright (c) 2010-2024. Axon Framework
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

import org.junit.jupiter.api.*;

import static org.axonframework.eventsourcing.eventstore.EventCriteria.hasTag;
import static org.axonframework.eventsourcing.eventstore.SourcingCondition.conditionFor;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link NoAppendCondition}.
 *
 * @author Steven van Beelen
 */
class NoAppendConditionTest {

    @Test
    void consistencyMarkerFixedToLongMax() {
        assertEquals(Long.MAX_VALUE, AppendCondition.none().consistencyMarker());
    }

    @Test
    void criteriaFixedToNoCriteria() {
        assertEquals(EventCriteria.anyEvent(), AppendCondition.none().criteria());
    }

    @Test
    void withSourcingConditionSetsActualMarkerAndCriteria() {
        long testEnd = 20L;
        SourcingCondition testSourcingCondition = conditionFor(hasTag(new Tag("key", "value")), 10L, testEnd);

        AppendCondition result = AppendCondition.none().with(testSourcingCondition);

        assertEquals(testEnd, result.consistencyMarker());
        assertEquals(testSourcingCondition.criteria(), result.criteria());
    }

    @Test
    void withMarkerThrowsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> AppendCondition.none().withMarker(42L));
    }
}