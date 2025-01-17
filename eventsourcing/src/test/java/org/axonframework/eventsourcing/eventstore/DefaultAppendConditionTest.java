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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link DefaultAppendCondition}.
 *
 * @author Steven van Beelen
 */
class DefaultAppendConditionTest {

    private static final ConsistencyMarker TEST_CONSISTENCY_MARKER = new GlobalIndexConsistencyMarker(10);
    private static final EventCriteria TEST_CRITERIA = EventCriteria.forAnyEventType().withTags("key", "value");

    private AppendCondition testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new DefaultAppendCondition(TEST_CONSISTENCY_MARKER, TEST_CRITERIA);
    }

    @Test
    void throwsExceptionWhenConstructingWithNullEventCriteria() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> new DefaultAppendCondition(ConsistencyMarker.ORIGIN, (EventCriteria) null));
    }

    @Test
    void containsExpectedData() {
        assertEquals(TEST_CONSISTENCY_MARKER, testSubject.consistencyMarker());
        assertEquals(Set.of(TEST_CRITERIA), testSubject.criteria());
    }

    @Test
    void withMarkerChangesMarkerButLeavesConditions() {
        ConsistencyMarker testConsistencyMarker = new GlobalIndexConsistencyMarker(5);

        AppendCondition result = testSubject.withMarker(testConsistencyMarker);

        assertEquals(testConsistencyMarker, result.consistencyMarker());
        assertEquals(testSubject.criteria(), result.criteria());
    }

    @Test
    void orCriteriaAreCombinedWithExistingCriteria() {
        ConsistencyMarker testConsistencyMarker = new GlobalIndexConsistencyMarker(5);

        EventCriteria otherCriteria = EventCriteria.forAnyEventType().withAnyTags();
        AppendCondition result = testSubject.withMarker(testConsistencyMarker).orCriteria(TEST_CRITERIA)
                .orCriteria(otherCriteria);
        assertTrue(result.criteria().contains(TEST_CRITERIA));
        assertTrue(result.criteria().contains(otherCriteria));
    }
}