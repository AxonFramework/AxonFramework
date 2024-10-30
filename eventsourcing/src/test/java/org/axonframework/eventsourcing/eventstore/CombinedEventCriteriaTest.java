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

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link CombinedEventCriteria}.
 *
 * @author Steven van Beelen
 */
class CombinedEventCriteriaTest {

    private Index testIndexOne;
    private Index testIndexTwo;

    private CombinedEventCriteria testSubject;

    @BeforeEach
    void setUp() {
        testIndexOne = new Index("keyOne", "valueOne");
        testIndexTwo = new Index("keySecond", "valueSecond");

        testSubject = new CombinedEventCriteria(new SingleIndexCriteria(testIndexOne),
                                                new SingleIndexCriteria(testIndexTwo));
    }

    @Test
    void throwsAxonConfigurationExceptionWhenConstructingWithNullFirstOrSecondEventCriteria() {
        //noinspection DataFlowIssue
        assertThrows(AxonConfigurationException.class,
                     () -> new CombinedEventCriteria(null, new SingleIndexCriteria(testIndexTwo)));
        //noinspection DataFlowIssue
        assertThrows(AxonConfigurationException.class,
                     () -> new CombinedEventCriteria(new SingleIndexCriteria(testIndexOne), null));
    }

    @Test
    void containsExpectedData() {
        assertTrue(testSubject.indices().contains(testIndexOne));
        assertTrue(testSubject.indices().contains(testIndexTwo));
        assertTrue(testSubject.types().isEmpty());
    }

    @Test
    void matchingIndicesMatchesOnContainedIndices() {
        assertTrue(testSubject.matchingIndices(Set.of(testIndexOne, testIndexTwo)));
        assertFalse(testSubject.matchingIndices(Set.of(testIndexOne)));
        assertFalse(testSubject.matchingIndices(Set.of(testIndexTwo)));
        assertFalse(testSubject.matchingIndices(Set.of(new Index("non-existing-key", "non-existing-value"))));
    }
}