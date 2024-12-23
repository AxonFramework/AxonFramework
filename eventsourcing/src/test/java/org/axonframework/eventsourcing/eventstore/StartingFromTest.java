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

import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test class validating the {@link StartingFrom}.
 *
 * @author Steven van Beelen
 */
class StartingFromTest {

    private static final GlobalSequenceTrackingToken TEST_POSITION = new GlobalSequenceTrackingToken(1337);
    private static final EventCriteria TEST_CRITERIA = EventCriteria.hasIndex(new Index("key", "value"));

    private StreamingCondition testSubject;

    @BeforeEach
    void setUp() {
        testSubject = StreamingCondition.startingFrom(TEST_POSITION);
    }

    @Test
    void containsExpectedData() {
        assertEquals(TEST_POSITION, testSubject.position());
        assertEquals(EventCriteria.noCriteria(), testSubject.criteria());
    }

    @Test
    void withCriteriaReplaceNoCriteriaForGivenCriteria() {
        assertEquals(EventCriteria.noCriteria(), testSubject.criteria());

        StreamingCondition result = testSubject.with(TEST_CRITERIA);

        assertEquals(TEST_CRITERIA, result.criteria());
    }

    @Test
    void withCriteriaThrowsIllegalArgumentExceptionWhenPositionIsNull() {
        StreamingCondition nullPositionTestSubject = StreamingCondition.startingFrom(null);

        assertThrows(IllegalArgumentException.class, () -> nullPositionTestSubject.with(TEST_CRITERIA));
    }
}