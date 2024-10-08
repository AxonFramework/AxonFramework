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

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link DefaultSourcingCondition}.
 *
 * @author Steven van Beelen
 */
class DefaultSourcingConditionTest {

    private static final EventCriteria TEST_CRITERIA = EventCriteria.hasIndex(new Index("key", "value"));
    private static final long TEST_START = 1L;
    private static final long TEST_END = 10L;

    private SourcingCondition testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new DefaultSourcingCondition(TEST_CRITERIA, TEST_START, TEST_END);
    }

    @Test
    void throwsAxonConfigurationExceptionWhenConstructingWithNullEventCriteria() {
        //noinspection DataFlowIssue
        assertThrows(AxonConfigurationException.class, () -> new DefaultSourcingCondition(null, TEST_START, TEST_END));
    }

    @Test
    void defaultsStartToMinusOneWhenNullIsGiven() {
        SourcingCondition testSubjectWithNullStart = new DefaultSourcingCondition(TEST_CRITERIA, null, null);

        assertEquals(-1L, testSubjectWithNullStart.start());
    }

    @Test
    void endReturnsEmptyOptionalForNullEndValue() {
        SourcingCondition testSubjectWithNullEnd = new DefaultSourcingCondition(TEST_CRITERIA, TEST_START, null);

        assertFalse(testSubjectWithNullEnd.end().isPresent());
    }

    @Test
    void endReturnsFilledOptionalForActualEndValue() {
        OptionalLong resultEnd = testSubject.end();

        assertTrue(resultEnd.isPresent());
        assertEquals(TEST_END, resultEnd.getAsLong());
    }

    @Test
    void combineUsesTheSmallestStartValue() {
        long biggerStart = testSubject.start() + 10;
        SourcingCondition testSubjectWithLargerStart =
                new DefaultSourcingCondition(TEST_CRITERIA, biggerStart, TEST_END);

        SourcingCondition result = testSubject.combine(testSubjectWithLargerStart);

        assertNotEquals(biggerStart, result.start());
        assertEquals(testSubject.start(), result.start());
    }

    @Test
    void combineUsesTheLargestEndValue() {
        //noinspection OptionalGetWithoutIsPresent
        long smallerEnd = testSubject.end().getAsLong() - 5;
        SourcingCondition testSubjectWithSmallerEnd =
                new DefaultSourcingCondition(TEST_CRITERIA, TEST_START, smallerEnd);

        SourcingCondition result = testSubject.combine(testSubjectWithSmallerEnd);

        OptionalLong optionalEnd = result.end();
        assertTrue(optionalEnd.isPresent());
        long resultEnd = optionalEnd.getAsLong();
        assertNotEquals(smallerEnd, resultEnd);
        //noinspection OptionalGetWithoutIsPresent
        assertEquals(testSubject.end().getAsLong(), resultEnd);
    }
}