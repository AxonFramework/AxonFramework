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

import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link StartingFrom}.
 *
 * @author Steven van Beelen
 */
class StartingFromTest {

    private static final GlobalSequenceTrackingToken TEST_POSITION = new GlobalSequenceTrackingToken(1337);
    private static final EventCriteria TEST_CRITERIA = EventCriteria.matchesTag(new Tag("key", "value"));

    private StreamingCondition testSubject;

    @BeforeEach
    void setUp() {
        testSubject = StreamingCondition.startingFrom(TEST_POSITION);
    }

    @Test
    void containsExpectedData() {
        assertEquals(TEST_POSITION, testSubject.position());
        assertEquals(Collections.emptySet(), testSubject.criteria());
    }

    @Test
    void withCriteriaReplaceNoCriteriaForGivenCriteria() {
        assertEquals(Collections.emptySet(), testSubject.criteria());

        StreamingCondition result = testSubject.or(TEST_CRITERIA);

        assertEquals(Set.of(TEST_CRITERIA), result.criteria());
    }

    @Test
    void withCriteriaThrowsIllegalArgumentExceptionWhenPositionIsNull() {
        StreamingCondition nullPositionTestSubject = StreamingCondition.startingFrom(null);

        assertThrows(IllegalArgumentException.class, () -> nullPositionTestSubject.or(TEST_CRITERIA));
    }
}