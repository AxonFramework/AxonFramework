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

import org.junit.jupiter.api.*;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EventCriteriaTest {

    @Test
    void criteriaForAnyEventWillAlwaysMatch() {
        EventCriteria testSubject = EventCriteria.anyEvent();

        assertTrue(testSubject.matches("OneType", Set.of()));
        assertTrue(testSubject.matches("Another", Set.of()));
        assertTrue(testSubject.matches("OneType", Set.of(new Tag("key1", "value1"), new Tag("key2", "value2"))));
        assertTrue(testSubject.matches("Another", Set.of(new Tag("key1", "value1"))));
    }

    @Test
    void criteriaForEventsOfSpecificTypeIgnoresOtherTypes() {
        EventCriteria testSubject = EventCriteria.forEventTypes("OneType").withAnyTags();

        assertTrue(testSubject.matches("OneType", Set.of()));
        assertTrue(testSubject.matches("OneType", Set.of(new Tag("key1", "value1"), new Tag("key2", "value2"))));

        assertFalse(testSubject.matches("Another", Set.of()));
        assertFalse(testSubject.matches("Another", Set.of(new Tag("key1", "value1"))));
    }

    @Test
    void criteriaWithTypesAndTagsIgnoresTagsForOtherTypes() {
        EventCriteria testSubject = EventCriteria.forEventTypes("OneType").withTags("key1", "value1");

        assertTrue(testSubject.matches("OneType", Set.of(new Tag("key1", "value1"), new Tag("key2", "value2"))));

        assertFalse(testSubject.matches("OneType", Set.of()));
        assertFalse(testSubject.matches("Another", Set.of()));
        assertFalse(testSubject.matches("Another", Set.of(new Tag("key1", "value1"))));

    }

    @Test
    void criteriaWithTypesAndTagsIgnoresEventsWithSubsetOfTags() {
        EventCriteria testSubject = EventCriteria.forEventTypes("OneType").withTags("key1", "value1", "key2", "value2");

        assertTrue(testSubject.matches("OneType", Set.of(new Tag("key1", "value1"), new Tag("key2", "value2"))));

        assertFalse(testSubject.matches("OneType", Set.of(new Tag("key1", "value1"))));
        assertFalse(testSubject.matches("OneType", Set.of()));
        assertFalse(testSubject.matches("Another", Set.of()));
        assertFalse(testSubject.matches("Another", Set.of(new Tag("key1", "value1"))));
    }

    @Test
    void criteriaWithOddParameterCountForTagsIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> EventCriteria.forAnyEventType().withTags("odd"));
        assertThrows(IllegalArgumentException.class, () -> EventCriteria.forAnyEventType().withTags("odd", "even", "odd"));
    }

    @Test
    void criteriaWithEqualParametersAreConsideredEqual() {
        EventCriteria testSubject1 = EventCriteria.forEventTypes("OneType").withTags("key1", "value1");
        EventCriteria testSubject2 = EventCriteria.forEventTypes("OneType").withTags(new Tag("key1", "value1"));
        EventCriteria testSubject3 = EventCriteria.forEventTypes("OtherType").withTags(new Tag("key1", "value1"));
        EventCriteria testSubject4 = EventCriteria.forEventTypes("OneType").withTags(new Tag("key2", "value2s"));
        EventCriteria testSubject5 = EventCriteria.forAnyEventType().withAnyTags();
        EventCriteria testSubject6 = EventCriteria.forEventTypes().withTags(Set.of());

        assertEquals(testSubject1, testSubject2);
        assertEquals(testSubject5, testSubject6);

        assertNotEquals(testSubject1, testSubject3);
        assertNotEquals(testSubject1, testSubject4);
        assertNotEquals(testSubject1, testSubject5);
        assertNotEquals(testSubject1, testSubject6);

        assertNotEquals(testSubject2, testSubject3);
        assertNotEquals(testSubject2, testSubject4);
        assertNotEquals(testSubject2, testSubject5);
        assertNotEquals(testSubject2, testSubject6);

        assertNotEquals(testSubject3, testSubject4);
        assertNotEquals(testSubject3, testSubject5);
        assertNotEquals(testSubject3, testSubject6);

        assertNotEquals(testSubject4, testSubject5);
        assertNotEquals(testSubject4, testSubject6);
    }
}