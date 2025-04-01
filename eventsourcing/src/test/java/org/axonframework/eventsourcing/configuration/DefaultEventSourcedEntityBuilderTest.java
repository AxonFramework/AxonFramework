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

package org.axonframework.eventsourcing.configuration;

import org.axonframework.eventsourcing.AsyncEventSourcingRepository;
import org.axonframework.eventsourcing.CriteriaResolver;
import org.axonframework.eventsourcing.EventStateApplier;
import org.axonframework.eventsourcing.annotation.EventSourcedEntityFactory;
import org.axonframework.eventsourcing.eventstore.EventCriteria;
import org.axonframework.modelling.repository.AsyncRepository;
import org.junit.jupiter.api.*;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link DefaultEventSourcedEntityBuilder}.
 *
 * @author Steven van Beelen
 */
class DefaultEventSourcedEntityBuilderTest {

    private EventSourcedEntityFactory<CourseId, Course> testEntityFactory;
    private CriteriaResolver<CourseId> testCriteriaResolver;
    private EventStateApplier<Course> testEventStateApplier;
    private AtomicBoolean constructedEntityFactory;
    private AtomicBoolean constructedCriteriaResolver;
    private AtomicBoolean constructedEventStateApplier;

    private EventSourcedEntityBuilder<CourseId, Course> testSubject;

    @BeforeEach
    void setUp() {
        testEntityFactory = (type, id) -> new Course(id);
        testCriteriaResolver = event -> EventCriteria.anyEvent();
        testEventStateApplier = (model, event, processingContext) -> model;
        constructedEntityFactory = new AtomicBoolean(false);
        constructedCriteriaResolver = new AtomicBoolean(false);
        constructedEventStateApplier = new AtomicBoolean(false);

        testSubject = EventSourcedEntityBuilder.entity(CourseId.class, Course.class)
                                               .entityFactory(c -> {
                                                   constructedEntityFactory.set(true);
                                                   return testEntityFactory;
                                               })
                                               .criteriaResolver(c -> {
                                                   constructedCriteriaResolver.set(true);
                                                   return testCriteriaResolver;
                                               })
                                               .eventStateApplier(c -> {
                                                   constructedEventStateApplier.set(true);
                                                   return testEventStateApplier;
                                               });
    }

    @Test
    void entityThrowsNullPointerExceptionForNullIdentifierType() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> EventSourcedEntityBuilder.entity(null, Course.class));
    }

    @Test
    void entityThrowsNullPointerExceptionForNullEntityType() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> EventSourcedEntityBuilder.entity(CourseId.class, null));
    }

    @Test
    void entityFactoryThrowsNullPointerExceptionForNullEntityFactory() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class,
                     () -> EventSourcedEntityBuilder.entity(CourseId.class, Course.class)
                                                    .entityFactory(null));
    }

    @Test
    void criteriaResolverThrowsNullPointerExceptionForNullCriteriaResolver() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class,
                     () -> EventSourcedEntityBuilder.entity(CourseId.class, Course.class)
                                                    .entityFactory(c -> testEntityFactory)
                                                    .criteriaResolver(null));
    }

    @Test
    void eventStateApplierThrowsNullPointerExceptionForNullEventStateApplier() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class,
                     () -> EventSourcedEntityBuilder.entity(CourseId.class, Course.class)
                                                    .entityFactory(c -> testEntityFactory)
                                                    .criteriaResolver(c -> testCriteriaResolver)
                                                    .eventStateApplier(null));
    }

    @Test
    void entityNameCombinesIdentifierAndEntityTypeNames() {
        String expectedEntityName = "Course#CourseId";

        assertEquals(expectedEntityName, testSubject.entityName());
    }

    @Test
    void repositoryConstructsEventSourcingRepositoryForEntityFactoryCriteriaResolverAndEventStateApplier() {
        AsyncRepository<CourseId, Course> result = testSubject.repository()
                                                              .build(EventSourcingConfigurer.create().build());

        assertInstanceOf(AsyncEventSourcingRepository.class, result);
        assertTrue(constructedEntityFactory.get());
        assertTrue(constructedCriteriaResolver.get());
        assertTrue(constructedEventStateApplier.get());
    }

    record CourseId() {

    }

    record Course(CourseId id) {

    }
}