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
        EventCriteria testSubject = EventCriteria.match().eventsOfTypes("OneType").withAnyTags();

        assertTrue(testSubject.matches("OneType", Set.of()));
        assertTrue(testSubject.matches("OneType", Set.of(new Tag("key1", "value1"), new Tag("key2", "value2"))));

        assertFalse(testSubject.matches("Another", Set.of()));
        assertFalse(testSubject.matches("Another", Set.of(new Tag("key1", "value1"))));
    }

    @Test
    void criteriaWithTypesAndTagsIgnoresTagsForOtherTypes() {
        EventCriteria testSubject = EventCriteria.match().eventsOfTypes("OneType").withTags("key1", "value1");

        assertTrue(testSubject.matches("OneType", Set.of(new Tag("key1", "value1"), new Tag("key2", "value2"))));

        assertFalse(testSubject.matches("OneType", Set.of()));
        assertFalse(testSubject.matches("Another", Set.of()));
        assertFalse(testSubject.matches("Another", Set.of(new Tag("key1", "value1"))));
    }

    @Test
    void criteriaWithTypesAndTagsIgnoresEventsWithSubsetOfTags() {
        EventCriteria testSubject = EventCriteria.match().eventsOfTypes("OneType").withTags("key1",
                                                                                            "value1",
                                                                                            "key2",
                                                                                            "value2");

        assertTrue(testSubject.matches("OneType", Set.of(new Tag("key1", "value1"), new Tag("key2", "value2"))));

        assertFalse(testSubject.matches("OneType", Set.of(new Tag("key1", "value1"))));
        assertFalse(testSubject.matches("OneType", Set.of()));
        assertFalse(testSubject.matches("Another", Set.of()));
        assertFalse(testSubject.matches("Another", Set.of(new Tag("key1", "value1"))));
    }

    @Test
    void criteriaWithOddParameterCountForTagsIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> EventCriteria.match().eventsOfAnyType().withTags("odd"));
        assertThrows(IllegalArgumentException.class,
                     () -> EventCriteria.match().eventsOfAnyType().withTags("odd", "even", "odd"));
    }

    @Test
    void criteriaWithEqualParametersAreConsideredEqual() {
        EventCriteria testSubject1 = EventCriteria.match().eventsOfTypes("OneType").withTags("key1", "value1");
        EventCriteria testSubject2 = EventCriteria.match().eventsOfTypes("OneType").withTags(new Tag("key1", "value1"));
        EventCriteria testSubject3 = EventCriteria.match().eventsOfTypes("OtherType").withTags(new Tag("key1", "value1"));
        EventCriteria testSubject4 = EventCriteria.match().eventsOfTypes("OneType").withTags(new Tag("key2", "value2s"));
        EventCriteria testSubject5 = EventCriteria.anyEvent();
        EventCriteria testSubject6 = EventCriteria.match().eventsOfAnyType().withTags(Set.of());

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

    @Test
    void criteriaWithOrConditionMatchBoth() {
        EventCriteria testSubject = EventCriteria.match().eventsOfTypes("OneType").withTags("key1", "value1")
                                                 .or().eventsOfTypes("OtherType").withTags("key2", "value2");

        assertTrue(testSubject.matches("OneType", Set.of(new Tag("key1", "value1"))));
        assertTrue(testSubject.matches("OtherType", Set.of(new Tag("key2", "value2"))));

        assertFalse(testSubject.matches("OneType", Set.of(new Tag("key2", "value2"))));
        assertFalse(testSubject.matches("OtherType", Set.of(new Tag("key1", "value1"))));
    }

    @Test
    void criteriaWithOrOnAnyEventWillMatchAllEvents() {
        EventCriteria testSubject = EventCriteria.match().eventsOfAnyType().withTags("key1", "value1")
                                                 .or().eventsOfAnyType().withAnyTags();

        assertTrue(testSubject.matches("OneType", Set.of(new Tag("key1", "value1"))));
        assertTrue(testSubject.matches("OtherType", Set.of(new Tag("key2", "value2"))));
    }

    @Test
    void criteriaWithAnyTagsWillMatchAllEventsOfThatType() {
        EventCriteria testSubject = EventCriteria.match().eventsOfTypes("OneType").withAnyTags();

        assertTrue(testSubject.matches("OneType", Set.of(new Tag("key1", "value1"))));
        assertTrue(testSubject.matches("OneType", Set.of(new Tag("key2", "value2"))));
        assertTrue(testSubject.matches("OneType", Set.of()));
        assertFalse(testSubject.matches("TypeTwo", Set.of(new Tag("key1", "value1"))));
        assertFalse(testSubject.matches("TypeTwo", Set.of(new Tag("key2", "value2"))));
        assertFalse(testSubject.matches("TypeTwo", Set.of()));
    }

    @Test
    void whenNestingIsDifferentSameCriteriaStillLeadToEquals() {
        EventCriteria testSubject1 = EventCriteria.match().eventsOfTypes("OneType").withTags("key1", "value1")
                                                  .or().eventsOfTypes("OtherType").withTags("key2", "value2")
                                                  .or().eventsOfTypes("ThirdType").withAnyTags();
        EventCriteria testSubject2 = EventCriteria.match().eventsOfTypes("OtherType").withTags("key2", "value2")
                                                  .or().eventsOfTypes("OneType").withTags("key1", "value1")
                                                  .or().eventsOfTypes("ThirdType").withAnyTags();

        assertEquals(testSubject1, testSubject2);
    }

    @Test
    void anyEventIsFlattenedToNothing() {
        EventCriteria testSubject = EventCriteria.anyEvent();

        assertTrue(testSubject.flatten().isEmpty());
    }



    @Nested
    class FlattenTest {


        @Test
        void flatteningTwoConditionsInOrLeadsToTwoCriteria() {
            EventCriteria testSubject1 = EventCriteria.match().eventsOfTypes("OneType").withTags("key1", "value1");
            EventCriteria testSubject2 = EventCriteria.match().eventsOfTypes("OneType").withTags("key2", "value2");
            EventCriteria combined = testSubject1.or(testSubject2);

            Set<EventCriterion> flattened = combined.flatten();
            assertEquals(2, flattened.size());
            assertTrue(flattened.contains(testSubject1));
            assertTrue(flattened.contains(testSubject2));
        }

        @Test
        void makingOrOfOrCriteriaWillMergeCriteria() {
            EventCriteria testSubject1 = EventCriteria.match().eventsOfTypes("OneType").withTags("key1", "value1");
            EventCriteria testSubject2 = EventCriteria.match().eventsOfTypes("OneType").withTags("key2", "value2");
            EventCriteria testSubject3 = EventCriteria.match().eventsOfTypes("OneType").withTags("key3", "value3");
            EventCriteria combined1 = testSubject1.or(testSubject2);
            EventCriteria combined2 = testSubject1.or(testSubject3);
            EventCriteria combined = combined1.or(combined2);

            Set<EventCriterion> flattened = combined.flatten();
            assertEquals(3, flattened.size());
            assertTrue(flattened.contains(testSubject1));
            assertTrue(flattened.contains(testSubject2));
            assertTrue(flattened.contains(testSubject3));
        }

        @Test
        void combiningSameCriteriaInOrLeadsToSingularCriteriaDuringFlatten() {
            EventCriteria testSubject1 = EventCriteria.match().eventsOfTypes("OneType").withTags("key1", "value1");
            EventCriteria testSubject2 = EventCriteria.match().eventsOfTypes("OneType").withTags("key1", "value1");
            EventCriteria combined = testSubject1.or(testSubject2);

            assertEquals(1, combined.flatten().size());
        }

        @Test
        void flatteningFilteredCriteriaWithNoTagsOrTypesLeadsToEmptySet() {
            EventCriteria testSubject = EventCriteria.match().eventsOfAnyType().withAnyTags();

            assertTrue(testSubject.flatten().isEmpty());
        }
    }

    @Nested
    class HasCriteriaTest {

        @Test
        void anyEventDoesNotHaveCriteria() {
            EventCriteria testSubject = EventCriteria.anyEvent();

            assertFalse(testSubject.hasCriteria());
        }

        @Test
        void criteriaWithEventTypesAndTagsWillHaveCriteria() {
            EventCriteria testSubject = EventCriteria.match().eventsOfTypes("OneType").withTags("key1", "value1");

            assertTrue(testSubject.hasCriteria());
        }

        @Test
        void criteriaWithEventTypesAndWithoutTagsWillHaveCriteria() {
            EventCriteria testSubject = EventCriteria.match().eventsOfTypes("OneType").withAnyTags();

            assertTrue(testSubject.hasCriteria());
        }
        @Test
        void criteriaWithoutEventTypesAndWithoutTagsWillNotHaveCriteria() {
            EventCriteria testSubject = EventCriteria.match().eventsOfAnyType().withAnyTags();

            assertFalse(testSubject.hasCriteria());
        }

        @Test
        void orConditionWithCriteriaWillHaveCriteria() {
            EventCriteria testSubject = EventCriteria.match().eventsOfTypes("OneType").withTags("key1", "value1")
                                                     .or().eventsOfTypes("OtherType").withTags("key2", "value2");

            assertTrue(testSubject.hasCriteria());
        }

        @Test
        void orConditionWithAnyEventWillNotHaveCriteria() {
            EventCriteria testSubject = EventCriteria.match().eventsOfAnyType().withAnyTags()
                                                     .or().eventsOfAnyType().withAnyTags();

            assertFalse(testSubject.hasCriteria());
        }
    }
}