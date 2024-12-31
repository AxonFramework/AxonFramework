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

import org.axonframework.common.AxonConfigurationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test class validating the {@link DefaultAppendCondition}.
 *
 * @author Steven van Beelen
 */
class DefaultAppendConditionTest {

    private static final long TEST_CONSISTENCY_MARKER = 10L;
    private static final EventCriteria TEST_CRITERIA = EventCriteria.hasTag(new Tag("key", "value"));

    private AppendCondition testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new DefaultAppendCondition(TEST_CONSISTENCY_MARKER, TEST_CRITERIA);
    }

    @Test
    void throwsAxonConfigurationExceptionWhenConstructingWithNullEventCriteria() {
        //noinspection DataFlowIssue
        assertThrows(AxonConfigurationException.class, () -> new DefaultAppendCondition(-1L, null));
    }

    @Test
    void containsExpectedData() {
        assertEquals(TEST_CONSISTENCY_MARKER, testSubject.consistencyMarker());
        assertEquals(TEST_CRITERIA, testSubject.criteria());
    }

    @Test
    void withSourcingConditionSelectsSmallestConsistencyMarkerAndCombinesCriteria() {
        EventCriteria testCriteria = EventCriteria.hasTag(new Tag("newKey", "newValue"));
        long testEnd = TEST_CONSISTENCY_MARKER + 5;
        SourcingCondition testSourcingCondition = SourcingCondition.conditionFor(testCriteria, 0L, testEnd);

        EventCriteria expectedCriteria = testSourcingCondition.criteria().combine(TEST_CRITERIA);

        AppendCondition result = testSubject.with(testSourcingCondition);

        assertEquals(TEST_CONSISTENCY_MARKER, result.consistencyMarker());
        assertEquals(expectedCriteria, result.criteria());
    }

    @Test
    void withMarkerSelectsSmallestValue() {
        long testConsistencyMarker = TEST_CONSISTENCY_MARKER - 5;

        AppendCondition result = testSubject.withMarker(testConsistencyMarker);

        assertEquals(testConsistencyMarker, result.consistencyMarker());
    }
}