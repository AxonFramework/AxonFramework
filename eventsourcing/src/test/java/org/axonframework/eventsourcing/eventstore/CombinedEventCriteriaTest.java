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

    private Tag testTagOne;
    private Tag testTagTwo;

    private CombinedEventCriteria testSubject;

    @BeforeEach
    void setUp() {
        testTagOne = new Tag("keyOne", "valueOne");
        testTagTwo = new Tag("keySecond", "valueSecond");

        testSubject = new CombinedEventCriteria(new SingleTagCriteria(testTagOne),
                                                new SingleTagCriteria(testTagTwo));
    }

    @Test
    void throwsAxonConfigurationExceptionWhenConstructingWithNullFirstOrSecondEventCriteria() {
        //noinspection DataFlowIssue
        assertThrows(AxonConfigurationException.class,
                     () -> new CombinedEventCriteria(null, new SingleTagCriteria(testTagTwo)));
        //noinspection DataFlowIssue
        assertThrows(AxonConfigurationException.class,
                     () -> new CombinedEventCriteria(new SingleTagCriteria(testTagOne), null));
    }

    @Test
    void containsExpectedData() {
        assertTrue(testSubject.tags().contains(testTagOne));
        assertTrue(testSubject.tags().contains(testTagTwo));
        assertTrue(testSubject.types().isEmpty());
    }

    @Test
    void matchingTagsMatchesOnContainedTags() {
        assertTrue(testSubject.matchingTags(Set.of(testTagOne, testTagTwo)));
        assertFalse(testSubject.matchingTags(Set.of(testTagOne)));
        assertFalse(testSubject.matchingTags(Set.of(testTagTwo)));
        assertFalse(testSubject.matchingTags(Set.of(new Tag("non-existing-key", "non-existing-value"))));
    }
}