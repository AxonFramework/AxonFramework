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
        EventCriteria testSubject = EventCriteria.isOneOfTypes("OneType");

        assertTrue(testSubject.matches("OneType", Set.of()));
        assertTrue(testSubject.matches("OneType", Set.of(new Tag("key1", "value1"), new Tag("key2", "value2"))));

        assertFalse(testSubject.matches("Another", Set.of()));
        assertFalse(testSubject.matches("Another", Set.of(new Tag("key1", "value1"))));
    }

    @Test
    void criteriaWithTypesBothTagsIgnoresTagsForOtherTypes() {
        EventCriteria testSubject = EventCriteria.both(
                EventCriteria.isOneOfTypes("OneType"),
                EventCriteria.matchesTag(new Tag("key1", "value1"))
        );

        assertTrue(testSubject.matches("OneType", Set.of(new Tag("key1", "value1"), new Tag("key2", "value2"))));

        assertFalse(testSubject.matches("OneType", Set.of()));
        assertFalse(testSubject.matches("Another", Set.of()));
        assertFalse(testSubject.matches("Another", Set.of(new Tag("key1", "value1"))));

    }

    @Test
    void criteriaWithTypesBothTagsIgnoresEventsWithSubsetOfTags() {
        EventCriteria testSubject = EventCriteria.both(
                EventCriteria.isOneOfTypes("OneType"),
                EventCriteria.both(
                        EventCriteria.matchesTag(new Tag("key1", "value1")),
                        EventCriteria.matchesTag(new Tag("key2", "value2"))
                )
        );

        assertTrue(testSubject.matches("OneType", Set.of(new Tag("key1", "value1"), new Tag("key2", "value2"))));

        assertFalse(testSubject.matches("OneType", Set.of(new Tag("key1", "value1"))));
        assertFalse(testSubject.matches("OneType", Set.of()));
        assertFalse(testSubject.matches("Another", Set.of()));
        assertFalse(testSubject.matches("Another", Set.of(new Tag("key1", "value1"))));
    }

    @Test
    void canConstructCriteriaObjectForOrm() {
        var criteria = EventCriteria.both(
                EventCriteria.isOneOfTypes("OneType"),
                EventCriteria.either(
                        EventCriteria.matchesTag(new Tag("key1", "value1")),
                        EventCriteria.matchesTag(new Tag("key2", "value2"))
                )
        );

        assertEquals("(IS (OneType) AND (key1=value1 OR key2=value2))", criteria.toString());
    }

    @Test
    void canFormInsanelyComplexCondition() {
        var criteria = EventCriteria.either(
                EventCriteria.both(
                        EventCriteria.isOneOfTypes("OneType"),
                        EventCriteria.either(
                                EventCriteria.matchesTag(new Tag("key1", "value1")),
                                EventCriteria.both(
                                        EventCriteria.matchesTag(new Tag("key2", "value2")),
                                        EventCriteria.isOneOfTypes("AnotherType")
                                )
                        )
                ),
                EventCriteria.both(
                        EventCriteria.isOneOfTypes("TwoType"),
                        EventCriteria.either(
                                EventCriteria.matchesTag(new Tag("key1", "value1")),
                                EventCriteria.both(
                                        EventCriteria.matchesTag(new Tag("key2", "value2")),
                                        EventCriteria.isOneOfTypes("AnotherType")
                                )
                        )
                )
        );

        assertEquals("((IS (OneType) AND (key1=value1 OR (key2=value2 AND IS (AnotherType)))) OR (IS (TwoType) AND (key1=value1 OR (key2=value2 AND IS (AnotherType)))))", criteria.toString());
    }


    @Test
    void proveThatCanBeRecursivelyReduced() {
        var criteria = EventCriteria.either(
                EventCriteria.both(
                        EventCriteria.isOneOfTypes("OneType"),
                        EventCriteria.either(
                                EventCriteria.matchesTag(new Tag("key1", "value1")),
                                EventCriteria.both(
                                        EventCriteria.matchesTag(new Tag("key2", "value2")),
                                        EventCriteria.isOneOfTypes("AnotherType")
                                )
                        )
                ),
                EventCriteria.both(
                        EventCriteria.isOneOfTypes("TwoType"),
                        EventCriteria.either(
                                EventCriteria.matchesTag(new Tag("key1", "value1")),
                                EventCriteria.both(
                                        EventCriteria.matchesTag(new Tag("key2", "value2")),
                                        EventCriteria.isOneOfTypes("AnotherType")
                                )
                        )
                )
        );

        assertEquals("OR (AND (IS (OneType) AND OR (key1=value1 OR AND (key2=value2 AND IS (AnotherType)))) OR AND (IS (TwoType) AND OR (key1=value1 OR AND (key2=value2 AND IS (AnotherType)))))", reduceCriteria(criteria));


    }

    private String reduceCriteria(EventCriteria criteria) {
        switch (criteria) {
            case AndEventCriteria andEventCriteria -> {
                return "AND (" + andEventCriteria.criteria().stream().map(this::reduceCriteria).reduce((a, b) -> a
                        + " AND " + b).orElse("") + ")";
            }
            case OrEventCriteria orEventCriteria -> {
                return "OR (" + orEventCriteria.criteria().stream().map(this::reduceCriteria).reduce((a, b) -> a
                        + " OR " + b).orElse("") + ")";
            }
            case EventTypesCriteria eventTypesCriteria -> {
                return "IS (" + eventTypesCriteria.types().stream().reduce((a, b) -> a + ", " + b).orElse("") + ")";
            }
            case TagEventCriteria tagEventCriteria -> {
                return tagEventCriteria.tag().key() + "=" + tagEventCriteria.tag().value();
            }
            case AnyEvent anyEvent -> {
                return "*";
            }
        }
    }
}