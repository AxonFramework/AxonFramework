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

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventstreaming.EventCriteria;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link DefaultSourcingCondition}.
 *
 * @author Steven van Beelen
 */
class DefaultSourcingConditionTest {

    private static final EventCriteria TEST_CRITERIA = EventCriteria.havingTags("key", "value");
    private static final long TEST_START = 1L;

    private SourcingCondition testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new DefaultSourcingCondition(TEST_START, TEST_CRITERIA);
    }

    @Test
    void throwsExceptionWhenConstructingWithNullEventCriteria() {
        //noinspection DataFlowIssue
        assertThrows(AxonConfigurationException.class,
                     () -> new DefaultSourcingCondition(TEST_START, null));
    }

    @Test
    void combineUsesTheSmallestStartValue() {
        long biggerStart = testSubject.start() + 10;
        SourcingCondition testSubjectWithLargerStart =
                new DefaultSourcingCondition(biggerStart, TEST_CRITERIA);

        SourcingCondition result = testSubject.or(testSubjectWithLargerStart);

        assertNotEquals(biggerStart, result.start());
        assertEquals(testSubject.start(), result.start());
    }
}