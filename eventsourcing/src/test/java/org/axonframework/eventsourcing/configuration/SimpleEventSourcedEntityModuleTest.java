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

import org.axonframework.messaging.commandhandling.CommandBus;
import org.axonframework.messaging.commandhandling.CommandHandlingComponent;
import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.eventsourcing.CriteriaResolver;
import org.axonframework.eventsourcing.EventSourcedEntityFactory;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.messaging.commandhandling.sequencing.CommandSequencingPolicy;
import org.axonframework.messaging.commandhandling.sequencing.NoOpCommandSequencingPolicy;
import org.axonframework.messaging.eventstreaming.EventCriteria;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.correlation.CorrelationDataProviderRegistry;
import org.axonframework.messaging.core.correlation.DefaultCorrelationDataProviderRegistry;
import org.axonframework.modelling.EntityIdResolver;
import org.axonframework.modelling.StateManager;
import org.axonframework.modelling.entity.EntityCommandHandlingComponent;
import org.axonframework.modelling.entity.EntityMetamodel;
import org.axonframework.modelling.repository.Repository;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link SimpleEventSourcedEntityModule}.
 *
 * @author Steven van Beelen
 */
class SimpleEventSourcedEntityModuleTest {

    private EventSourcedEntityFactory<CourseId, Course> testEntityFactory;
    private CriteriaResolver<CourseId> testCriteriaResolver;
    private EntityMetamodel<Course> testEntityModel;
    private EntityIdResolver<CourseId> testEntityIdResolver;
    private AtomicBoolean constructedEntityModel;
    private AtomicBoolean constructedEntityFactory;
    private AtomicBoolean constructedCriteriaResolver;
    private AtomicBoolean constructedEntityIdResolver;

    private EventSourcedEntityModule<CourseId, Course> testSubject;

    @BeforeEach
    void setUp() {
        testEntityFactory = EventSourcedEntityFactory.fromIdentifier(Course::new);
        testCriteriaResolver = (event, context) -> EventCriteria.havingAnyTag();
        testEntityIdResolver = (message, context) -> new CourseId();
        testEntityModel = EntityMetamodel.forEntityType(Course.class)
                                         .entityEvolver((entity, event, context) -> entity)
                                         .instanceCommandHandler(new QualifiedName("instance"),
                                                                 (command, entity, context) -> MessageStream.empty()
                                                                                                            .cast())
                                         .creationalCommandHandler(new QualifiedName("creational"),
                                                                   (command, context) -> MessageStream.empty().cast())
                                         .build();
        constructedEntityFactory = new AtomicBoolean(false);
        constructedCriteriaResolver = new AtomicBoolean(false);
        constructedEntityModel = new AtomicBoolean(false);
        constructedEntityIdResolver = new AtomicBoolean(false);

        testSubject = EventSourcedEntityModule.declarative(CourseId.class, Course.class)
                                              .messagingModel((c, b) -> {
                                                  constructedEntityModel.set(true);
                                                  return testEntityModel;
                                              })
                                              .entityFactory(c -> {
                                                  constructedEntityFactory.set(true);
                                                  return testEntityFactory;
                                              })
                                              .criteriaResolver(c -> {
                                                  constructedCriteriaResolver.set(true);
                                                  return testCriteriaResolver;
                                              })
                                              .entityIdResolver(c -> {
                                                  constructedEntityIdResolver.set(true);
                                                  return testEntityIdResolver;
                                              });
    }

    @Test
    void entityThrowsNullPointerExceptionForNullIdentifierType() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> EventSourcedEntityModule.declarative(null, Course.class));
    }

    @Test
    void entityThrowsNullPointerExceptionForNullEntityType() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> EventSourcedEntityModule.declarative(CourseId.class, null));
    }

    @Test
    void entityFactoryThrowsNullPointerExceptionForNullEntityModel() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class,
                     () -> EventSourcedEntityModule.declarative(CourseId.class, Course.class)
                                                   .messagingModel(null));
    }

    @Test
    void entityFactoryThrowsNullPointerExceptionForNullEntityFactory() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class,
                     () -> EventSourcedEntityModule.declarative(CourseId.class, Course.class)
                                                   .messagingModel((c, b) -> testEntityModel)
                                                   .entityFactory(null));
    }


    @Test
    void criteriaResolverThrowsNullPointerExceptionForNullCriteriaResolver() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class,
                     () -> EventSourcedEntityModule.declarative(CourseId.class, Course.class)
                                                   .messagingModel((c, m) -> testEntityModel)
                                                   .entityFactory(c -> testEntityFactory)
                                                   .criteriaResolver(null));
    }

    @Test
    void entityEvolverThrowsNullPointerExceptionForNullEntityIdResolver() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class,
                     () -> EventSourcedEntityModule.declarative(CourseId.class, Course.class)
                                                   .messagingModel((c, b) -> testEntityModel)
                                                   .entityFactory(c -> testEntityFactory)
                                                   .criteriaResolver(c -> testCriteriaResolver)
                                                   .entityIdResolver(null));
    }

    @Test
    void entityNameCombinesIdentifierAndEntityTypeNames() {
        String expectedEntityName = "Course#CourseId";

        assertEquals(expectedEntityName, testSubject.entityName());
    }

    @Test
    void registersAnEventSourcingRepositoryWithTheStateManager() {
        AxonConfiguration configuration = EventSourcingConfigurer.create()
                                                                 .componentRegistry(cr -> cr.registerModule(testSubject))
                                                                 .start();
        Repository<CourseId, Course> result = configuration.getComponent(StateManager.class)
                                                           .repository(Course.class, CourseId.class);

        assertInstanceOf(EventSourcingRepository.class, result);
        assertTrue(constructedEntityFactory.get());
        assertTrue(constructedCriteriaResolver.get());
        assertTrue(constructedEntityModel.get());
        assertTrue(constructedEntityIdResolver.get());
    }

    @Test
    void registersAnEntityCommandHandlingComponentWithTheCommandBus() {
        CommandBus commandBus = mock(CommandBus.class);
        // Registers default provider registry to remove MessageOriginProvider, thus removing CorrelationDataInterceptor.
        // and the NoOpCommandSequencingPolicy, thus removing the CommandSequencingInterceptor
        // This ensures we keep the SimpleCommandBus, from which we can retrieve the subscription for validation.
        EventSourcingConfigurer.create()
                               .componentRegistry(cr -> cr.registerComponent(
                                       CorrelationDataProviderRegistry.class,
                                       c -> new DefaultCorrelationDataProviderRegistry())
                               )
                               .componentRegistry(cr -> cr.registerComponent(CommandSequencingPolicy.class,
                                                                             c -> new NoOpCommandSequencingPolicy()))
                               .componentRegistry(cr -> cr.registerModule(testSubject)
                                                          .registerComponent(CommandBus.class, c -> commandBus))
                               .start();

        assertTrue(constructedEntityIdResolver.get());

        ArgumentCaptor<CommandHandlingComponent> captor = ArgumentCaptor.forClass(CommandHandlingComponent.class);
        verify(commandBus).subscribe(captor.capture());

        CommandHandlingComponent component = captor.getValue();
        assertInstanceOf(EntityCommandHandlingComponent.class, component);
        assertTrue(component.supportedCommands().contains(new QualifiedName("instance")));
        assertTrue(component.supportedCommands().contains(new QualifiedName("creational")));
    }

    record CourseId() {

    }

    record Course(CourseId id) {

    }
}