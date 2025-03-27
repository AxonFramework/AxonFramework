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

package org.axonframework.eventsourcing.annotation;

import org.axonframework.eventsourcing.eventstore.EventCriteria;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class AnnotationBasedEventCriteriaResolverTest {

    @Test
    void resolvedRightBuilderMethodForDifferentIdTypes() {
        var resolver = new AnnotationBasedEventCriteriaResolver(FunctionalEventSourcedEntity.class);

        var criteriaString = resolver.resolve("id");
        assertEquals(EventCriteria.match().eventsOfAnyType().withTags("aggregateIdentifierOfString", "id"), criteriaString);

        var criteriaLong = resolver.resolve(1L);
        assertEquals(EventCriteria.match().eventsOfAnyType().withTags("aggregateIdentifierOfLong", "1"), criteriaLong);
    }

    @Test
    void throwsWhenEventCriteriaBuilderReturnsNull() {
        var resolver = new AnnotationBasedEventCriteriaResolver(FunctionalEventSourcedEntity.class);

        var exception = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve(1)
        );
        assertEquals(
                "The @EventCriteriaBuilder method returned null. The method must return a non-null EventCriteria. Violating method: buildCriteria(java.lang.Integer)",
                exception.getMessage()
        );
    }

    @Test
    void usesTagKeyPropertyIfNoEventCriteriaBuilderMatches() {
        var resolver = new AnnotationBasedEventCriteriaResolver(FunctionalEventSourcedEntity.class);

        var criteria = resolver.resolve(0.0);
        assertEquals(EventCriteria.match().eventsOfAnyType().withTags("fallbackTagKey", "0.0"), criteria);
    }

    @EventSourcedEntity(tagKey = "fallbackTagKey")
    class FunctionalEventSourcedEntity {

        @EventCriteriaBuilder
        public static EventCriteria buildCriteria(String id) {
            return EventCriteria.match().eventsOfAnyType().withTags("aggregateIdentifierOfString", id);
        }

        @EventCriteriaBuilder
        public static EventCriteria buildCriteria(Long id) {
            return EventCriteria.match().eventsOfAnyType().withTags("aggregateIdentifierOfLong", id.toString());
        }

        @EventCriteriaBuilder
        public static EventCriteria buildCriteria(Integer id) {
            return null;
        }
    }

    @Test
    void usesSimpleClassNameAsTagNameIfNoTagKeyPropertyIsSet() {
        var resolver = new AnnotationBasedEventCriteriaResolver(DefaultEventSourcedEntity.class);

        var criteria = resolver.resolve("id");
        assertEquals(EventCriteria.match().eventsOfAnyType().withTags("DefaultEventSourcedEntity", "id"), criteria);
    }

    @EventSourcedEntity()
    class DefaultEventSourcedEntity {
    }

    @Nested
    class ConfigurationProblems {

        @Test
        void testEventCriteriaBuilderWithNonStaticBuilderThrowsException() {
            var exception = Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> new AnnotationBasedEventCriteriaResolver(EntityWithNonStaticEventCriteriaBuilder.class));
            assertEquals(
                    "Method annotated with @EventCriteriaBuilder must be static. Violating method: buildCriteria(java.lang.String)",
                    exception.getMessage()
            );
        }

        @EventSourcedEntity
        class EntityWithNonStaticEventCriteriaBuilder {

            @EventCriteriaBuilder
            public EventCriteria buildCriteria(String id) {
                return EventCriteria.match().eventsOfAnyType().withTags("aggregateIdentifier", id);
            }
        }

        @Test
        void testEventCriteriaBuilderWithZeroArgBuilderThrowsException() {
            var exception = Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> new AnnotationBasedEventCriteriaResolver(EntityWithZeroArgEventCriteriaBuilder.class));
            assertEquals(
                    "Method annotated with @EventCriteriaBuilder must have exactly one parameter. Violating method: buildCriteria()",
                    exception.getMessage()
            );
        }


        @EventSourcedEntity
        class EntityWithZeroArgEventCriteriaBuilder {

            @EventCriteriaBuilder
            public static EventCriteria buildCriteria() {
                return EventCriteria.anyEvent();
            }
        }

        @Test
        void testEventCriteriaBuilderWithVoidReturnValueThrowsException() {
            var exception = Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> new AnnotationBasedEventCriteriaResolver(EntityWithVoidReturnValue.class));
            assertEquals(
                    "Method annotated with @EventCriteriaBuilder must return an EventCriteria. Violating method: buildCriteria(java.lang.String)",
                    exception.getMessage()
            );
        }

        @EventSourcedEntity
        class EntityWithVoidReturnValue {

            @EventCriteriaBuilder
            public static void buildCriteria(String id) {
            }
        }

        @Test
        void testEventCriteriaBuilderWithNonEventSourcedEntityThrowsException() {
            var exception = Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> new AnnotationBasedEventCriteriaResolver(NonEventSourcedEntity.class));
            assertEquals(
                    "The given class is not an @EventSourcedEntity",
                    exception.getMessage()
            );
        }

        class NonEventSourcedEntity {

        }
    }
}