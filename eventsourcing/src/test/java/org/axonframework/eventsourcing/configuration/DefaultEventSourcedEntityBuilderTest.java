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

import org.axonframework.common.infra.MockComponentDescriptor;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.CriteriaResolver;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.annotation.EventSourcedEntityFactory;
import org.axonframework.eventstreaming.EventCriteria;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.axonframework.modelling.EntityEvolver;
import org.axonframework.modelling.SimpleEntityEvolvingComponent;
import org.axonframework.modelling.repository.Repository;
import org.junit.jupiter.api.*;

import java.util.Map;
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
    private EntityEvolver<Course> testEntityEvolver;
    private AtomicBoolean constructedEntityFactory;
    private AtomicBoolean constructedCriteriaResolver;
    private AtomicBoolean constructedEntityEvolver;

    private EventSourcedEntityBuilder<CourseId, Course> testSubject;

    @BeforeEach
    void setUp() {
        testEntityFactory = (type, id) -> new Course(id);
        testCriteriaResolver = (event, context) -> EventCriteria.havingAnyTag();
        testEntityEvolver = (entity, event, context) -> entity;
        constructedEntityFactory = new AtomicBoolean(false);
        constructedCriteriaResolver = new AtomicBoolean(false);
        constructedEntityEvolver = new AtomicBoolean(false);

        testSubject = EventSourcedEntityBuilder.entity(CourseId.class, Course.class)
                                               .entityFactory(c -> {
                                                   constructedEntityFactory.set(true);
                                                   return testEntityFactory;
                                               })
                                               .criteriaResolver(c -> {
                                                   constructedCriteriaResolver.set(true);
                                                   return testCriteriaResolver;
                                               })
                                               .entityEvolver(c -> {
                                                   constructedEntityEvolver.set(true);
                                                   return testEntityEvolver;
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
    void entityEvolverThrowsNullPointerExceptionForNullEntityEvolver() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class,
                     () -> EventSourcedEntityBuilder.entity(CourseId.class, Course.class)
                                                    .entityFactory(c -> testEntityFactory)
                                                    .criteriaResolver(c -> testCriteriaResolver)
                                                    .entityEvolver(null));
    }

    @Test
    void entityNameCombinesIdentifierAndEntityTypeNames() {
        String expectedEntityName = "Course#CourseId";

        assertEquals(expectedEntityName, testSubject.entityName());
    }

    @Test
    void repositoryConstructsEventSourcingRepositoryForEntityFactoryCriteriaResolverAndEntityEvolver() {
        Repository<CourseId, Course> result = testSubject.repository()
                                                         .build(EventSourcingConfigurer.create().build());

        assertInstanceOf(EventSourcingRepository.class, result);
        assertTrue(constructedEntityFactory.get());
        assertTrue(constructedCriteriaResolver.get());
        assertTrue(constructedEntityEvolver.get());
    }

    @Test
    void eventSourcingHandlersAreDisregardedForGivenEventStateApplier() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        // test subject already has an evolver, so all handlers should be ignored
        EventSourcedEntityBuilder.EventSourcingHandlerPhase<CourseId, Course> eshPhase =
                (EventSourcedEntityBuilder.EventSourcingHandlerPhase<CourseId, Course>) testSubject;
        eshPhase.eventSourcingHandler(String.class,
                                      (entity, payload) -> {
                                          invoked.set(true);
                                          return entity;
                                      })
                .eventSourcingHandler(Integer.class,
                                      (entity, payload) -> {
                                          invoked.set(true);
                                      })
                .eventSourcingHandler(Boolean.class,
                                      (entity, payload) -> {
                                          invoked.set(true);
                                          return entity;
                                      })
                .eventSourcingHandler(Long.class,
                                      (entity, payload) -> {
                                          invoked.set(true);
                                      });
        Repository<CourseId, Course> result = testSubject.repository()
                                                         .build(EventSourcingConfigurer.create().build());
        MockComponentDescriptor descriptor = new MockComponentDescriptor();
        result.describeTo(descriptor);

        assertInstanceOf(EntityEvolver.class, descriptor.getProperty("entityEvolver"));
        EntityEvolver<Course> resultEvolver = descriptor.getProperty("entityEvolver");

        // Ignores String event
        GenericEventMessage<String> stringEvent = new GenericEventMessage<>(new MessageType(String.class), "payload");
        resultEvolver.evolve(new Course(new CourseId()),
                             stringEvent,
                             StubProcessingContext.forMessage(stringEvent));
        assertFalse(invoked.get());

        // Ignores Integer event
        GenericEventMessage<Integer> intEvent = new GenericEventMessage<>(new MessageType(Integer.class), 42);
        resultEvolver.evolve(new Course(new CourseId()),
                             intEvent,
                             StubProcessingContext.forMessage(intEvent));
        assertFalse(invoked.get());

        // Ignores Boolean event
        GenericEventMessage<Boolean> booleanEvent = new GenericEventMessage<>(new MessageType(Boolean.class), true);
        resultEvolver.evolve(new Course(new CourseId()),
                             booleanEvent,
                             StubProcessingContext.forMessage(booleanEvent));
        assertFalse(invoked.get());

        // Ignores Long event
        GenericEventMessage<Long> longEvent = new GenericEventMessage<>(new MessageType(Long.class), 1337L);
        resultEvolver.evolve(new Course(new CourseId()),
                             longEvent,
                             StubProcessingContext.forMessage(longEvent));
        assertFalse(invoked.get());

        // The already present Entity Evolver should definitely be constructed instead!
        assertTrue(constructedEntityEvolver.get());
    }

    @Test
    void combinesEventSourcingHandlersIntoSimplyEntityEvolvingComponent() {
        EventSourcedEntityBuilder<CourseId, Course> separateEventSourcingHandlers =
                EventSourcedEntityBuilder.entity(CourseId.class, Course.class)
                                         .entityFactory(c -> testEntityFactory)
                                         .criteriaResolver(c -> testCriteriaResolver)
                                         .eventSourcingHandler(String.class, (entity, payload) -> entity)
                                         .eventSourcingHandler(Integer.class, (entity, payload) -> {
                                         })
                                         .eventSourcingHandler(Boolean.class, (entity, payload) -> entity)
                                         .eventSourcingHandler(Long.class, (entity, payload) -> {
                                         });

        Repository<CourseId, Course> result =
                separateEventSourcingHandlers.repository()
                                             .build(EventSourcingConfigurer.create().build());
        MockComponentDescriptor descriptor = new MockComponentDescriptor();
        result.describeTo(descriptor);

        assertInstanceOf(SimpleEntityEvolvingComponent.class, descriptor.getProperty("entityEvolver"));
        SimpleEntityEvolvingComponent<?> resultEvolver = descriptor.getProperty("entityEvolver");

        MockComponentDescriptor evolverDescriptor = new MockComponentDescriptor();
        resultEvolver.describeTo(evolverDescriptor);
        assertInstanceOf(Map.class, evolverDescriptor.getProperty("delegates"));
        Map<?, ?> delegates = evolverDescriptor.getProperty("delegates");
        assertEquals(4, delegates.size());
    }

    @Test
    void eventSourcingHandlerThrowsIllegalArgumentExceptionWhenRegisteringEventNameTwice() {
        EventSourcedEntityBuilder.EventSourcingHandlerPhase<CourseId, Course> duplicateHandlerTestSubject =
                EventSourcedEntityBuilder.entity(CourseId.class, Course.class)
                                         .entityFactory(c -> testEntityFactory)
                                         .criteriaResolver(c -> testCriteriaResolver)
                                         .eventSourcingHandler(String.class, (entity, payload) -> entity);

        assertThrows(IllegalArgumentException.class,
                     () -> duplicateHandlerTestSubject.eventSourcingHandler(String.class, (entity, payload) -> entity));
    }

    record CourseId() {

    }

    record Course(CourseId id) {

    }
}