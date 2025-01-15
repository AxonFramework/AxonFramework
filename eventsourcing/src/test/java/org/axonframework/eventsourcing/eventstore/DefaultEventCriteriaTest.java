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

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link DefaultEventCriteria}.
 *
 * @author Steven van Beelen
 */
class DefaultEventCriteriaTest {

    @Test
    void throwsExceptionWhenConstructingWithNullTag() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> new DefaultEventCriteria(null, Set.of()));

        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> new DefaultEventCriteria(Set.of(), null));
    }

    @Test
    void containsExpectedData() {
        Tag testTag = new Tag("key", "value");

        EventCriteria testSubject = new DefaultEventCriteria(Set.of(), Set.of(testTag));

        assertEquals(1, testSubject.tags().size());
        assertTrue(testSubject.tags().contains(testTag));
        assertTrue(testSubject.types().isEmpty());
    }
}