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

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.configuration.ComponentFactory;
import org.axonframework.configuration.NewConfiguration;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.modelling.StateManager;
import org.axonframework.modelling.command.StatefulCommandHandler;
import org.axonframework.modelling.command.StatefulCommandHandlingComponent;
import org.axonframework.modelling.repository.AsyncRepository;
import org.junit.jupiter.api.*;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link StatefulCommandHandlingModule}.
 *
 * @author Steven van Beelen
 */
class StatefulCommandHandlingModuleTest {

    private static final QualifiedName COMMAND_NAME = new QualifiedName(String.class);

    private StatefulCommandHandlingModule.SetupPhase setupPhase;
    private StatefulCommandHandlingModule.CommandHandlerPhase commandHandlerPhase;
    private StatefulCommandHandlingModule.EntityPhase entityPhase;

    @BeforeEach
    void setUp() {
        setupPhase = StatefulCommandHandlingModule.named("test-subject");
        commandHandlerPhase = setupPhase.commandHandlers();
        entityPhase = setupPhase.entities();
    }

    @Test
    void nameReturnsModuleName() {
        assertEquals("test-subject", setupPhase.commandHandlers().build().name());
    }

    @Test
    void buildRegisteredRepositoryStateManagerAndStatefulCommandHandlingComponent() {
        StateBasedEntityBuilder<String, String> entityBuilder =
                StateBasedEntityBuilder.entity(String.class, String.class)
                                       .loader(c -> (id, context) -> CompletableFuture.completedFuture("instance"))
                                       .persister(c -> (id, entity, context) -> CompletableFuture.completedFuture(null));
        String stateManagerName = "StateManager[test-subject]";
        String statefulCommandHandlingComponentName = "StatefulCommandHandlingComponent[test-subject]";

        NewConfiguration resultConfig = setupPhase.entities()
                                                  .entity(entityBuilder)
                                                  .commandHandlers()
                                                  .build()
                                                  .build(ModellingConfigurer.create().build());

        //noinspection rawtypes
        Optional<AsyncRepository> optionalRepository =
                resultConfig.getOptionalComponent(AsyncRepository.class, entityBuilder.entityName());
        assertTrue(optionalRepository.isPresent());

        Optional<StateManager> optionalStateManager =
                resultConfig.getOptionalComponent(StateManager.class, stateManagerName);
        assertTrue(optionalStateManager.isPresent());

        Optional<StatefulCommandHandlingComponent> optionalHandlingComponent = resultConfig.getOptionalComponent(
                StatefulCommandHandlingComponent.class, statefulCommandHandlingComponentName
        );
        assertTrue(optionalHandlingComponent.isPresent());
    }

    @Test
    void namedThrowsIllegalArgumentExceptionForNullModuleName() {
        //noinspection DataFlowIssue
        assertThrows(IllegalArgumentException.class, () -> StatefulCommandHandlingModule.named(null));
    }

    @Test
    void namedThrowsIllegalArgumentExceptionForEmptyModuleName() {
        assertThrows(IllegalArgumentException.class, () -> StatefulCommandHandlingModule.named(""));
    }

    @Test
    void commandHandlerThrowsNullPointerExceptionForNullCommandName() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class,
                     () -> commandHandlerPhase.commandHandler(null, (cmd, context) -> MessageStream.just(null)));
    }

    @Test
    void commandHandlerThrowsNullPointerExceptionForNullCommandHandler() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class,
                     () -> commandHandlerPhase.commandHandler(COMMAND_NAME, (CommandHandler) null));
    }

    @Test
    void commandHandlerThrowsNullPointerExceptionForNullCommandNameWithStatefulCommandHandler() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class,
                     () -> commandHandlerPhase.commandHandler(null, (cmd, state, context) -> MessageStream.just(null)));
    }

    @Test
    void commandHandlerThrowsNullPointerExceptionForNullStatefulCommandHandler() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class,
                     () -> commandHandlerPhase.commandHandler(COMMAND_NAME, (StatefulCommandHandler) null));
    }

    @Test
    void commandHandlerThrowsNullPointerExceptionForNullCommandNameWithCommandHandlerBuilder() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> commandHandlerPhase.commandHandler(
                null, c -> (cmd, state, context) -> null
        ));
    }

    @Test
    void commandHandlerThrowsNullPointerExceptionForNullCommandHandlerBuilder() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> commandHandlerPhase.commandHandler(
                COMMAND_NAME, (ComponentFactory<StatefulCommandHandler>) null
        ));
    }

    @Test
    void commandHandlingComponentThrowsNullPointerExceptionForNullCommandHandlingComponentBuilder() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> commandHandlerPhase.commandHandlingComponent(null));
    }

    @Test
    void commandHandlingThrowsNullPointerExceptionForNullCommandHandlerPhaseConsumer() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> commandHandlerPhase.commandHandlers(null));
    }

    @Test
    void entityThrowsNullPointerExceptionForNullEntityBuilder() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> entityPhase.entity(null));
    }

    @Test
    void entityThrowsNullPointerExceptionForNullEntityPhaseConsumer() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> entityPhase.entities(null));
    }
}