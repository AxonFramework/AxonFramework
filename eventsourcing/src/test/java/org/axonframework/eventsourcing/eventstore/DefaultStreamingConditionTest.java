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
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test class validating the {@link DefaultStreamingCondition}.
 *
 * @author Steven van Beelen
 */
class DefaultStreamingConditionTest {

    private static final GlobalSequenceTrackingToken TEST_POSITION = new GlobalSequenceTrackingToken(1337);
    private static final EventCriteria TEST_CRITERIA = EventCriteria.hasTag(new Tag("key", "value"));

    private DefaultStreamingCondition testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new DefaultStreamingCondition(TEST_POSITION, TEST_CRITERIA);
    }

    @Test
    void throwsAxonConfigurationExceptionWhenConstructingWithNullPosition() {
        //noinspection DataFlowIssue
        assertThrows(AxonConfigurationException.class, () -> new DefaultStreamingCondition(null, TEST_CRITERIA));
    }

    @Test
    void throwsAxonConfigurationExceptionWhenConstructingWithNullCriteria() {
        //noinspection DataFlowIssue
        assertThrows(AxonConfigurationException.class, () -> new DefaultStreamingCondition(TEST_POSITION, null));
    }

    @Test
    void containsExpectedData() {
        assertEquals(TEST_POSITION, testSubject.position());
        assertEquals(TEST_CRITERIA, testSubject.criteria());
    }

    @Test
    void withCriteriaCombinesGivenWithExistingCriteria() {
        EventCriteria testCriteria = EventCriteria.hasTag(new Tag("other-key", "other-value"));
        EventCriteria expectedCriteria = testCriteria.combine(TEST_CRITERIA);

        StreamingCondition result = testSubject.with(testCriteria);

        assertEquals(TEST_POSITION, testSubject.position());
        assertEquals(expectedCriteria, result.criteria());
    }
}