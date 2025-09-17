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

package org.axonframework.modelling.configuration;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandlingComponent;
import org.axonframework.configuration.AxonConfiguration;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.correlation.CorrelationDataProvider;
import org.axonframework.messaging.correlation.CorrelationDataProviderRegistry;
import org.axonframework.messaging.correlation.DefaultCorrelationDataProviderRegistry;
import org.axonframework.modelling.SimpleRepository;
import org.axonframework.modelling.SimpleRepositoryEntityLoader;
import org.axonframework.modelling.SimpleRepositoryEntityPersister;
import org.axonframework.modelling.StateManager;
import org.axonframework.modelling.entity.EntityCommandHandlingComponent;
import org.axonframework.modelling.repository.Repository;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link StateBasedEntityModule}.
 *
 * @author Steven van Beelen
 */
class StateBasedEntityModuleTest {

    private SimpleRepositoryEntityLoader<CourseId, Course> testLoader;
    private SimpleRepositoryEntityPersister<CourseId, Course> testPersister;
    private AtomicBoolean constructedLoader;
    private AtomicBoolean constructedPersister;

    private StateBasedEntityModule<CourseId, Course> testSubject;

    @BeforeEach
    void setUp() {
        testLoader = (courseId, context) -> CompletableFuture.completedFuture(new Course(courseId));
        testPersister = (courseId, course, context) -> CompletableFuture.completedFuture(null);
        constructedLoader = new AtomicBoolean(false);
        constructedPersister = new AtomicBoolean(false);

        testSubject = baseModule().build();
    }

    @Test
    void entityThrowsNullPointerExceptionForNullIdentifierType() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> StateBasedEntityModule.declarative(null, Course.class));
    }

    @Test
    void entityThrowsNullPointerExceptionForNullEntityType() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> StateBasedEntityModule.declarative(CourseId.class, null));
    }

    @Test
    void repositoryThrowsNullPointerExceptionForNullRepository() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class,
                     () -> StateBasedEntityModule.declarative(CourseId.class, Course.class)
                                                 .repository(null));
    }

    @Test
    void loaderThrowsNullPointerExceptionForNullLoader() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class,
                     () -> StateBasedEntityModule.declarative(CourseId.class, Course.class)
                                                 .loader(null));
    }

    @Test
    void persisterThrowsNullPointerExceptionForNullPersister() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class,
                     () -> StateBasedEntityModule.declarative(CourseId.class, Course.class)
                                                 .loader(c -> testLoader)
                                                 .persister(null));
    }

    @Test
    void entityNameCombinesIdentifierAndEntityTypeNames() {
        baseModule().build();
        String expectedEntityName = "Course#CourseId";

        assertEquals(expectedEntityName, testSubject.entityName());
        assertEquals(CourseId.class, testSubject.idType());
        assertEquals(Course.class, testSubject.entityType());
    }

    @Test
    void registersRepositoryToStateManager() {
        AxonConfiguration configuration = ModellingConfigurer
                .create()
                .componentRegistry(cr -> cr.registerModule(
                        stateBasedModuleWithoutModel()
                ))
                .start();

        Repository<CourseId, Course> result = configuration
                .getComponent(StateManager.class)
                .repository(Course.class, CourseId.class);

        assertInstanceOf(SimpleRepository.class, result);
        assertTrue(constructedLoader.get());
        assertTrue(constructedPersister.get());
    }

    @Test
    void doesNotRegisterCommandHandlingComponentWithoutModel() {
        CommandBus commandBus = mock(CommandBus.class);
        ModellingConfigurer
                .create()
                .componentRegistry(cr -> cr
                        .registerModule(stateBasedModuleWithoutModel())
                        .registerComponent(CommandBus.class, c -> commandBus))
                .start();

        verifyNoInteractions(commandBus);
    }

    @Test
    void doesRegisterCommandHandlingComponentWithModel() {
        CommandBus commandBus = mock(CommandBus.class);
        // Registers default provider registry to remove MessageOriginProvider, thus removing CorrelationDataInterceptor.
        // This ensures we keep the SimpleCommandBus, from which we can capture the handling component subscribe method.
        ModellingConfigurer
                .create()
                .componentRegistry(cr -> cr.registerComponent(CorrelationDataProviderRegistry.class,
                                                              c -> new DefaultCorrelationDataProviderRegistry()))
                .componentRegistry(cr -> cr.registerModule(stateBasedModuleWithModel()))
                .componentRegistry(cr -> cr.registerComponent(CommandBus.class, c -> commandBus))
                .start();

        ArgumentCaptor<CommandHandlingComponent> captor = ArgumentCaptor.forClass(
                CommandHandlingComponent.class);
        verify(commandBus).subscribe(captor.capture());

        CommandHandlingComponent commandHandlingComponent = captor.getValue();
        assertInstanceOf(EntityCommandHandlingComponent.class, commandHandlingComponent);
        assertTrue(commandHandlingComponent.supportedCommands().contains(new QualifiedName("myQualifiedName")));
    }

    private StateBasedEntityModule<CourseId, Course> stateBasedModuleWithoutModel() {
        return baseModule()
                .build();
    }

    private StateBasedEntityModule<CourseId, Course> stateBasedModuleWithModel() {
        return baseModule()
                .messagingModel((config, builder) -> builder
                        .instanceCommandHandler(new QualifiedName("myQualifiedName"),
                                                                 (c1, command, ctx) -> MessageStream.empty().cast())
                        .build())
                .entityIdResolver(c -> (message, context) -> new CourseId());
    }

    private StateBasedEntityModule.MessagingMetamodelPhase<CourseId, Course> baseModule() {
        return StateBasedEntityModule
                .declarative(CourseId.class, Course.class)
                .loader(c -> {
                    constructedLoader.set(true);
                    return testLoader;
                })
                .persister(c -> {
                    constructedPersister.set(true);
                    return testPersister;
                });
    }

    record CourseId() {

    }

    record Course(CourseId id) {

    }
}