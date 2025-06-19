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
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandHandlingComponent;
import org.axonframework.common.infra.MockComponentDescriptor;
import org.axonframework.configuration.AxonConfiguration;
import org.axonframework.configuration.ComponentBuilder;
import org.axonframework.configuration.Configuration;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.modelling.StateManager;
import org.axonframework.modelling.command.StatefulCommandHandler;
import org.axonframework.modelling.command.StatefulCommandHandlingComponent;
import org.axonframework.modelling.repository.Repository;
import org.axonframework.utils.StubLifecycleRegistry;
import org.junit.jupiter.api.*;

import java.util.Map;
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
    void buildRegistersEntityToStateManagerOfThisModuleAndRegistersCommandHandlers() {
        StateBasedEntityModule<String, String> entityModule =
                StateBasedEntityModule.declarative(String.class, String.class)
                                      .loader(c -> (id, context) -> CompletableFuture.completedFuture("instance"))
                                      .persister(c -> (id, entity, context) -> CompletableFuture.completedFuture(null))
                                      .messagingModel((c, b) -> b
                                              .instanceCommandHandler(
                                                      new QualifiedName(String.class),
                                                      (cmd, state, context) -> MessageStream.just(null))
                                              .build())
                                      .entityIdResolver(config -> (message, context) -> "1");

        StubLifecycleRegistry lifecycleRegistry = new StubLifecycleRegistry();
        AxonConfiguration configuration = ModellingConfigurer
                .create()
                .componentRegistry(cr -> {
                    cr.registerModule(setupPhase
                                              .entities()
                                              .entity(entityModule)
                                              .commandHandlers()
                                              .commandHandler(new QualifiedName(Integer.class),
                                                              (command, state, context) -> MessageStream.just(null))
                                              .build());
                })
                .start();

        Configuration resultConfig = configuration.getModuleConfiguration("test-subject").orElseThrow();

        Optional<StateManager> optionalStateManager = resultConfig.getOptionalComponent(StateManager.class);
        assertTrue(optionalStateManager.isPresent());

        assertNotNull(optionalStateManager.get().repository(String.class, String.class));
        assertNotNull(resultConfig.getModuleConfiguration("SimpleStateBasedEntityModule<String, String>").orElseThrow()
                                   .getComponent(Repository.class, "String#String"));

        MockComponentDescriptor descriptor = new MockComponentDescriptor();
        resultConfig.getComponent(CommandBus.class).describeTo(descriptor);

        Map<QualifiedName, CommandHandlingComponent> subscriptions = descriptor.getProperty("subscriptions");
        assertTrue(subscriptions.containsKey(new QualifiedName(Integer.class)));
        assertTrue(subscriptions.containsKey(new QualifiedName(String.class)));
    }

    @Test
    void buildAnnotatedCommandHandlingComponentSucceedsAndRegisters() {
        var myCommandHandlingObject = new Object() {
            @org.axonframework.commandhandling.annotation.CommandHandler
            public void handle(String command) {
            }
        };


        Configuration resultConfig =
                setupPhase.commandHandlers()
                          .annotatedCommandHandlingComponent(c -> myCommandHandlingObject)
                          .build()
                          .build(ModellingConfigurer.create().build(), new StubLifecycleRegistry());

        Optional<StatefulCommandHandlingComponent> optionalHandlingComponent = resultConfig.getOptionalComponent(
                StatefulCommandHandlingComponent.class, "StatefulCommandHandlingComponent[test-subject]");
        assertTrue(optionalHandlingComponent.isPresent());
        assertTrue(optionalHandlingComponent.get().supportedCommands().contains(new QualifiedName(String.class)));
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
                COMMAND_NAME, (ComponentBuilder<StatefulCommandHandler>) null
        ));
    }

    @Test
    void commandHandlingComponentThrowsNullPointerExceptionForNullCommandHandlingComponentBuilder() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> commandHandlerPhase.commandHandlingComponent(null));
    }


    @Test
    void annotatedCommandHandlingComponentThrowsNullPointerExceptionForNullCommandHandlingComponentBuilder() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> commandHandlerPhase.annotatedCommandHandlingComponent(null));
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