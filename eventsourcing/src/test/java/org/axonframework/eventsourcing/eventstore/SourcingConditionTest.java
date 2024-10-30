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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test class validating the {@code static} factory methods and {@code default} methods of the
 * {@link SourcingCondition}.
 *
 * @author Steven van Beelen
 */
class SourcingConditionTest {

    private static final EventCriteria TEST_CRITERIA = EventCriteria.hasIndex(new Index("key", "value"));
    private static final long TEST_START = 42L;

    @Test
    void conditionForCriteria() {
        SourcingCondition result = SourcingCondition.conditionFor(TEST_CRITERIA);

        assertEquals(TEST_CRITERIA, result.criteria());
        assertEquals(-1L, result.start());
        assertEquals(Long.MAX_VALUE, result.end());
    }

    @Test
    void conditionForCriteriaAndStartPosition() {
        SourcingCondition result = SourcingCondition.conditionFor(TEST_CRITERIA, TEST_START);

        assertEquals(TEST_CRITERIA, result.criteria());
        assertEquals(TEST_START, result.start());
        assertEquals(Long.MAX_VALUE, result.end());
    }

    @Test
    void conditionForCriteriaAndStartPositionAndEndPosition() {
        long testEnd = 1337L;

        SourcingCondition result = SourcingCondition.conditionFor(TEST_CRITERIA, TEST_START, testEnd);

        assertEquals(TEST_CRITERIA, result.criteria());
        assertEquals(TEST_START, result.start());
        assertEquals(testEnd, result.end());
    }
}